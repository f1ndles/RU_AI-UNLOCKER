using System;
using System.IO;
using System.Net;
using System.Net.Http;
using System.Text;
using System.Threading.Tasks;

namespace RUAIUnlocker.Services
{
    public class UpdateCheckResult
    {
        public bool HasUpdate { get; set; }
        public string RemoteDate { get; set; }
        public string RemoteContent { get; set; }
        public string ErrorMessage { get; set; }

        public UpdateCheckResult()
        {
            RemoteDate = string.Empty;
            RemoteContent = string.Empty;
            ErrorMessage = string.Empty;
        }
    }

    public class UpdateService
    {
        public const string HOSTS_URL = "https://raw.githubusercontent.com/ImMALWARE/dns.malw.link/refs/heads/master/hosts";

        private readonly HostsService _hostsService;

        public UpdateService(HostsService hostsService)
        {
            _hostsService = hostsService;
            try
            {
                ServicePointManager.SecurityProtocol = SecurityProtocolType.Tls12 | (SecurityProtocolType)3072;
            }
            catch
            {
            }
        }

        public async Task<UpdateCheckResult> CheckForUpdateAsync(string localDate)
        {
            var result = new UpdateCheckResult();

            try
            {
                Logger.Log("Проверка обновлений hosts с dns.malw.link...");

                string content = await DownloadHostsContentAsync();
                if (string.IsNullOrEmpty(content))
                {
                    result.ErrorMessage = "Загруженный hosts файл пуст";
                    return result;
                }

                result.RemoteContent = content;
                result.RemoteDate = ExtractDateFromContent(content);

                if (!string.IsNullOrEmpty(result.RemoteDate) && !string.IsNullOrEmpty(localDate))
                {
                    result.HasUpdate = !string.Equals(result.RemoteDate.Trim(), localDate.Trim(), StringComparison.OrdinalIgnoreCase);
                }
                else if (!string.IsNullOrEmpty(result.RemoteDate) && string.IsNullOrEmpty(localDate))
                {
                    result.HasUpdate = true;
                }

                if (result.HasUpdate)
                {
                    Logger.Log(string.Format("Найдено обновление hosts! Локальная: '{0}', Доступна: '{1}'", localDate, result.RemoteDate));
                }
                else
                {
                    Logger.Log(string.Format("У вас установлена актуальная версия hosts ({0})", result.RemoteDate));
                }
            }
            catch (Exception ex)
            {
                result.ErrorMessage = ex.Message;
                Logger.Log("Ошибка проверки обновлений: " + ex.Message);
            }

            return result;
        }

        public bool ApplyUpdate(string remoteContent)
        {
            if (string.IsNullOrEmpty(remoteContent)) return false;

            try
            {
                _hostsService.CreateBackup();
                bool written = _hostsService.WriteLocalHosts(remoteContent);
                if (written)
                {
                    string date = ExtractDateFromContent(remoteContent);
                    Logger.Log(string.Format("Hosts успешно обновлен до версии от {0}", string.IsNullOrEmpty(date) ? "сегодня" : date));
                    return true;
                }
            }
            catch (Exception ex)
            {
                Logger.Log("Ошибка применения обновления: " + ex.Message);
            }

            return false;
        }

        public async Task<string> DownloadHostsContentAsync()
        {
            using (var handler = new HttpClientHandler { AutomaticDecompression = DecompressionMethods.GZip | DecompressionMethods.Deflate })
            using (var client = new HttpClient(handler))
            {
                client.Timeout = TimeSpan.FromSeconds(30);
                client.DefaultRequestHeaders.Add("User-Agent", "RUAIUnlocker-Windows/1.0");

                using (var response = await client.GetAsync(HOSTS_URL))
                {
                    response.EnsureSuccessStatusCode();
                    byte[] bytes = await response.Content.ReadAsByteArrayAsync();
                    return Encoding.UTF8.GetString(bytes);
                }
            }
        }

        public string ExtractDateFromContent(string content)
        {
            if (string.IsNullOrEmpty(content)) return string.Empty;

            using (var reader = new StringReader(content))
            {
                string line;
                while ((line = reader.ReadLine()) != null)
                {
                    string trimmed = line.Trim();
                    if (trimmed.IndexOf("Последнее обновление", StringComparison.OrdinalIgnoreCase) >= 0)
                    {
                        int colon = trimmed.IndexOf(':');
                        if (colon >= 0 && colon < trimmed.Length - 1)
                        {
                            return trimmed.Substring(colon + 1).Trim();
                        }
                    }
                }
            }

            return string.Empty;
        }
    }
}
