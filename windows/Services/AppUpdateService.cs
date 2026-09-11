using System;
using System.Collections;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Net;
using System.Net.Http;
using System.Text.RegularExpressions;
using System.Threading.Tasks;
using System.Web.Script.Serialization;
using System.Windows;
using System.Xml;
using RUAIUnlocker.Models;

namespace RUAIUnlocker.Services
{
    public class AppUpdateService
    {
        public const string CURRENT_VERSION = "1.0.0";
        public const string RELEASES_API_URL = "https://api.github.com/repos/f1ndles/RU_AI-UNLOCKER/releases/latest";
        public const string RELEASES_ATOM_URL = "https://github.com/f1ndles/RU_AI-UNLOCKER/releases.atom";
        public const string REPO_RELEASES_PAGE = "https://github.com/f1ndles/RU_AI-UNLOCKER/releases";

        private class ReleaseCandidate
        {
            public string Tag { get; set; }
            public string Title { get; set; }
            public string Changelog { get; set; }
            public string HtmlUrl { get; set; }
            public Version ParsedVersion { get; set; }
        }

        public AppUpdateService()
        {
            try
            {
                ServicePointManager.SecurityProtocol = SecurityProtocolType.Tls12 | (SecurityProtocolType)3072;
            }
            catch
            {
            }
        }

        public async Task<AppReleaseInfo> CheckForAppUpdateAsync()
        {
            var result = new AppReleaseInfo();

            try
            {
                Logger.Log("Проверка обновлений приложения на GitHub...");

                using (var handler = new HttpClientHandler { AutomaticDecompression = DecompressionMethods.GZip | DecompressionMethods.Deflate })
                using (var client = new HttpClient(handler))
                {
                    client.Timeout = TimeSpan.FromSeconds(20);
                    client.DefaultRequestHeaders.Add("User-Agent", "RUAIUnlocker-Windows/" + CURRENT_VERSION);

                    bool found = false;

                    try
                    {
                        using (var response = await client.GetAsync(RELEASES_API_URL))
                        {
                            if (response.IsSuccessStatusCode)
                            {
                                string json = await response.Content.ReadAsStringAsync();
                                if (!string.IsNullOrEmpty(json))
                                {
                                    var serializer = new JavaScriptSerializer();
                                    var dict = serializer.Deserialize<Dictionary<string, object>>(json);
                                    if (dict != null)
                                    {
                                        ParseJsonRelease(dict, result);
                                        if (!string.IsNullOrEmpty(result.DownloadUrl))
                                        {
                                            found = true;
                                        }
                                    }
                                }
                            }
                            else
                            {
                                Logger.Log(string.Format("GitHub API вернул статус {0}. Запрос через резервный RSS-канал...", (int)response.StatusCode));
                            }
                        }
                    }
                    catch (Exception ex)
                    {
                        Logger.Log("GitHub API недоступен (" + ex.Message + "). Запрос через резервный RSS-канал...");
                    }

                    if (!found)
                    {
                        found = await CheckFromAtomFeedAsync(client, result);
                    }

                    if (!string.IsNullOrEmpty(result.Version))
                    {
                        result.HasUpdate = IsNewerVersion(result.Version, CURRENT_VERSION);

                        if (result.HasUpdate)
                        {
                            Logger.Log(string.Format("Найдена новая версия приложения: {0} (текущая: {1})", result.Version, CURRENT_VERSION));
                        }
                        else
                        {
                            Logger.Log(string.Format("Приложение актуально (версия {0})", CURRENT_VERSION));
                        }
                    }
                    else
                    {
                        if (string.IsNullOrEmpty(result.ErrorMessage))
                        {
                            result.ErrorMessage = "Релизы приложения не найдены";
                        }
                        Logger.Log(result.ErrorMessage);
                    }
                }
            }
            catch (Exception ex)
            {
                result.ErrorMessage = ex.Message;
                Logger.Log("Ошибка при проверке обновлений приложения: " + ex.Message);
            }

            return result;
        }

        private void ParseJsonRelease(Dictionary<string, object> dict, AppReleaseInfo result)
        {
            if (dict.ContainsKey("tag_name") && dict["tag_name"] != null)
            {
                result.TagName = dict["tag_name"].ToString();
                result.Version = result.TagName.TrimStart('v', 'V');
            }

            if (dict.ContainsKey("name") && dict["name"] != null)
            {
                result.ReleaseName = dict["name"].ToString();
            }

            if (dict.ContainsKey("body") && dict["body"] != null)
            {
                result.Changelog = dict["body"].ToString();
            }

            if (dict.ContainsKey("html_url") && dict["html_url"] != null)
            {
                result.HtmlUrl = dict["html_url"].ToString();
            }

            var assetsList = dict.ContainsKey("assets") ? dict["assets"] as ArrayList : null;
            if (assetsList != null)
            {
                foreach (var item in assetsList)
                {
                    var asset = item as Dictionary<string, object>;
                    if (asset != null)
                    {
                        string assetName = asset.ContainsKey("name") && asset["name"] != null ? asset["name"].ToString() : "";
                        if (!string.IsNullOrEmpty(assetName) && assetName.EndsWith(".exe", StringComparison.OrdinalIgnoreCase))
                        {
                            result.DownloadUrl = asset.ContainsKey("browser_download_url") && asset["browser_download_url"] != null ? asset["browser_download_url"].ToString() : "";
                            if (asset.ContainsKey("size") && asset["size"] != null)
                            {
                                long size;
                                if (long.TryParse(asset["size"].ToString(), out size))
                                {
                                    result.AssetSize = size;
                                }
                            }
                            break;
                        }
                    }
                }
            }
        }

        private async Task<bool> CheckFromAtomFeedAsync(HttpClient client, AppReleaseInfo result)
        {
            try
            {
                using (var response = await client.GetAsync(RELEASES_ATOM_URL))
                {
                    if (!response.IsSuccessStatusCode)
                    {
                        result.ErrorMessage = "Резервный RSS-канал вернул статус: " + (int)response.StatusCode;
                        Logger.Log(result.ErrorMessage);
                        return false;
                    }

                    string xml = await response.Content.ReadAsStringAsync();
                    if (string.IsNullOrEmpty(xml)) return false;

                    var doc = new XmlDocument();
                    doc.LoadXml(xml);

                    var entryNodes = doc.GetElementsByTagName("entry");
                    if (entryNodes == null || entryNodes.Count == 0)
                    {
                        return false;
                    }

                    var entries = new List<ReleaseCandidate>();

                    foreach (XmlNode entry in entryNodes)
                    {
                        string tag = string.Empty;
                        string title = string.Empty;
                        string contentHtml = string.Empty;
                        string htmlUrl = string.Empty;

                        foreach (XmlNode child in entry.ChildNodes)
                        {
                            if (child.LocalName == "id")
                            {
                                int lastSlash = child.InnerText.LastIndexOf('/');
                                if (lastSlash >= 0)
                                {
                                    tag = child.InnerText.Substring(lastSlash + 1).Trim();
                                }
                            }
                            else if (child.LocalName == "title")
                            {
                                title = child.InnerText.Trim();
                            }
                            else if (child.LocalName == "content")
                            {
                                contentHtml = child.InnerText.Trim();
                            }
                            else if (child.LocalName == "link")
                            {
                                var href = child.Attributes != null && child.Attributes["href"] != null
                                    ? child.Attributes["href"].Value
                                    : string.Empty;
                                if (!string.IsNullOrEmpty(href))
                                {
                                    htmlUrl = href;
                                    if (string.IsNullOrEmpty(tag))
                                    {
                                        int lastSlash = href.LastIndexOf('/');
                                        if (lastSlash >= 0)
                                        {
                                            tag = href.Substring(lastSlash + 1).Trim();
                                        }
                                    }
                                }
                            }
                        }

                        if (!string.IsNullOrEmpty(tag))
                        {
                            Version ver;
                            NormalizeAndParseVersion(tag, out ver);

                            entries.Add(new ReleaseCandidate
                            {
                                Tag = tag,
                                Title = title,
                                Changelog = StripHtml(contentHtml),
                                HtmlUrl = htmlUrl,
                                ParsedVersion = ver
                            });
                        }
                    }

                    entries.Sort((a, b) =>
                    {
                        if (a.ParsedVersion != null && b.ParsedVersion != null)
                        {
                            return b.ParsedVersion.CompareTo(a.ParsedVersion);
                        }
                        if (a.ParsedVersion != null) return -1;
                        if (b.ParsedVersion != null) return 1;
                        return string.Compare(b.Tag, a.Tag, StringComparison.OrdinalIgnoreCase);
                    });

                    foreach (var item in entries)
                    {
                        string verStr = item.Tag.Trim().TrimStart('v', 'V');
                        if (!IsNewerVersion(verStr, CURRENT_VERSION))
                        {
                            continue;
                        }

                        string[] exeNames = new string[] { "RUAIUnlocker.exe", "ruaiunlocker.exe" };
                        foreach (var exeName in exeNames)
                        {
                            string candidateUrl = string.Format("https://github.com/f1ndles/RU_AI-UNLOCKER/releases/download/{0}/{1}", item.Tag, exeName);
                            try
                            {
                                using (var headReq = new HttpRequestMessage(HttpMethod.Head, candidateUrl))
                                using (var headResp = await client.SendAsync(headReq))
                                {
                                    if (headResp.IsSuccessStatusCode)
                                    {
                                        result.TagName = item.Tag;
                                        result.Version = verStr;
                                        result.ReleaseName = !string.IsNullOrEmpty(item.Title) ? item.Title : item.Tag;
                                        result.Changelog = item.Changelog;
                                        result.HtmlUrl = !string.IsNullOrEmpty(item.HtmlUrl)
                                            ? item.HtmlUrl
                                            : string.Format("https://github.com/f1ndles/RU_AI-UNLOCKER/releases/tag/{0}", item.Tag);
                                        result.DownloadUrl = candidateUrl;
                                        result.AssetSize = headResp.Content.Headers.ContentLength ?? 0;
                                        result.ErrorMessage = null;
                                        return true;
                                    }
                                }
                            }
                            catch
                            {
                            }
                        }
                    }

                    if (entries.Count > 0)
                    {
                        var latest = entries[0];
                        result.TagName = latest.Tag;
                        result.Version = latest.Tag.Trim().TrimStart('v', 'V');
                        result.ReleaseName = latest.Title;
                        result.Changelog = latest.Changelog;
                        result.HtmlUrl = latest.HtmlUrl;
                        result.ErrorMessage = null;
                        return true;
                    }
                }
            }
            catch (Exception ex)
            {
                Logger.Log("Ошибка проверки через RSS-канал: " + ex.Message);
            }

            return false;
        }

        private string StripHtml(string html)
        {
            if (string.IsNullOrEmpty(html)) return string.Empty;
            string text = html.Replace("<br>", Environment.NewLine)
                              .Replace("<br/>", Environment.NewLine)
                              .Replace("<br />", Environment.NewLine)
                              .Replace("</p>", Environment.NewLine)
                              .Replace("</li>", Environment.NewLine);
            text = Regex.Replace(text, "<[^>]+>", string.Empty);
            text = WebUtility.HtmlDecode(text);
            return text.Trim();
        }

        public async Task<bool> DownloadAndInstallUpdateAsync(string downloadUrl, IProgress<int> progress = null)
        {
            if (string.IsNullOrEmpty(downloadUrl))
            {
                throw new ArgumentException("Ссылка для скачивания пуста");
            }

            string updateDir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "RUAIUnlocker", "updates");
            if (!Directory.Exists(updateDir))
            {
                Directory.CreateDirectory(updateDir);
            }

            string updateFilePath = Path.Combine(updateDir, "RUAIUnlocker_update.exe");

            Logger.Log("Начало скачивания новой версии с " + downloadUrl);

            using (var client = new HttpClient())
            {
                client.Timeout = TimeSpan.FromMinutes(5);
                client.DefaultRequestHeaders.Add("User-Agent", "RUAIUnlocker-Windows/" + CURRENT_VERSION);

                using (var response = await client.GetAsync(downloadUrl, HttpCompletionOption.ResponseHeadersRead))
                {
                    response.EnsureSuccessStatusCode();

                    long totalBytes = response.Content.Headers.ContentLength ?? -1L;

                    using (var contentStream = await response.Content.ReadAsStreamAsync())
                    using (var fileStream = new FileStream(updateFilePath, FileMode.Create, FileAccess.Write, FileShare.None, 8192, true))
                    {
                        byte[] buffer = new byte[8192];
                        long totalRead = 0;
                        int read;

                        while ((read = await contentStream.ReadAsync(buffer, 0, buffer.Length)) > 0)
                        {
                            await fileStream.WriteAsync(buffer, 0, read);
                            totalRead += read;

                            if (totalBytes > 0 && progress != null)
                            {
                                int percent = (int)((totalRead * 100) / totalBytes);
                                progress.Report(percent);
                            }
                        }
                    }
                }
            }

            Logger.Log("Новая версия успешно скачана. Запуск процесса обновления и перезапуска...");

            string currentExe = Process.GetCurrentProcess().MainModule.FileName;
            int currentPid = Process.GetCurrentProcess().Id;

            string script = string.Format(
                "-NoProfile -WindowStyle Hidden -Command \"Wait-Process -Id {0} -Timeout 10; Start-Sleep -Milliseconds 600; Copy-Item -Path '{1}' -Destination '{2}' -Force; Start-Process -FilePath '{2}'\"",
                currentPid, updateFilePath, currentExe
            );

            var psi = new ProcessStartInfo
            {
                FileName = "powershell.exe",
                Arguments = script,
                CreateNoWindow = true,
                UseShellExecute = false,
                WindowStyle = ProcessWindowStyle.Hidden
            };

            Process.Start(psi);
            Application.Current.Shutdown();
            return true;
        }

        public void OpenUrl(string url)
        {
            if (string.IsNullOrEmpty(url)) url = REPO_RELEASES_PAGE;
            try
            {
                Process.Start(new ProcessStartInfo(url) { UseShellExecute = true });
            }
            catch (Exception ex)
            {
                Logger.Log("Ошибка открытия ссылки: " + ex.Message);
            }
        }

        private bool IsNewerVersion(string remoteVerStr, string currentVerStr)
        {
            if (string.IsNullOrEmpty(remoteVerStr)) return false;

            Version remoteVer;
            Version currentVer;

            if (NormalizeAndParseVersion(remoteVerStr, out remoteVer) && NormalizeAndParseVersion(currentVerStr, out currentVer))
            {
                return remoteVer > currentVer;
            }

            return !string.Equals(remoteVerStr.Trim(), currentVerStr.Trim(), StringComparison.OrdinalIgnoreCase);
        }

        private bool NormalizeAndParseVersion(string verStr, out Version version)
        {
            version = null;
            if (string.IsNullOrEmpty(verStr)) return false;

            string cleaned = verStr.Trim().TrimStart('v', 'V');
            string[] parts = cleaned.Split('.');
            if (parts.Length == 1) cleaned += ".0";
            if (parts.Length == 2) cleaned += ".0";

            return Version.TryParse(cleaned, out version);
        }
    }
}
