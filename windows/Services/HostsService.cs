using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Globalization;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;
using RUAIUnlocker.Models;

namespace RUAIUnlocker.Services
{
    public class HostsService
    {
        [DllImport("dnsapi.dll", EntryPoint = "DnsFlushResolverCache")]
        private static extern UInt32 DnsFlushResolverCache();

        private readonly string _hostsPath;
        private readonly string _backupDir;

        public HostsService()
        {
            _hostsPath = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.System), @"drivers\etc\hosts");
            _backupDir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), @"RUAIUnlocker\backups");

            try
            {
                if (!Directory.Exists(_backupDir))
                {
                    Directory.CreateDirectory(_backupDir);
                }
            }
            catch (Exception ex)
            {
                Logger.Log("Ошибка создания папки бэкапов: " + ex.Message);
            }
        }

        public string GetHostsPath()
        {
            return _hostsPath;
        }

        public string GetBackupDir()
        {
            return _backupDir;
        }

        public HostsInfo ReadLocalHosts()
        {
            var info = new HostsInfo();

            if (!File.Exists(_hostsPath))
            {
                Logger.Log("Файл hosts не найден: " + _hostsPath);
                return info;
            }

            try
            {
                var fileInfo = new FileInfo(_hostsPath);
                info.FileSize = fileInfo.Length;

                string[] lines = File.ReadAllLines(_hostsPath, Encoding.UTF8);
                string currentSection = string.Empty;

                foreach (string rawLine in lines)
                {
                    string line = rawLine.Trim();

                    if (line.IndexOf("Последнее обновление", StringComparison.OrdinalIgnoreCase) >= 0)
                    {
                        int colonIndex = line.IndexOf(':');
                        if (colonIndex >= 0 && colonIndex < line.Length - 1)
                        {
                            info.LastUpdate = line.Substring(colonIndex + 1).Trim();
                        }
                    }
                    else if (line.StartsWith("#") && !line.StartsWith("##"))
                    {
                        currentSection = line.Substring(1).Trim();
                    }
                    else if (!string.IsNullOrEmpty(line) && !line.StartsWith("#"))
                    {
                        string[] parts = line.Split(new char[] { ' ', '\t' }, StringSplitOptions.RemoveEmptyEntries);
                        if (parts.Length >= 2)
                        {
                            info.Entries.Add(new HostEntry(parts[0], parts[1], currentSection));
                        }
                    }
                }

                info.EntriesCount = info.Entries.Count;
            }
            catch (Exception ex)
            {
                Logger.Log("Ошибка чтения hosts: " + ex.Message);
            }

            return info;
        }

        public string ReadRawHosts()
        {
            if (!File.Exists(_hostsPath)) return string.Empty;
            return File.ReadAllText(_hostsPath, Encoding.UTF8);
        }

        public bool WriteLocalHosts(string content)
        {
            try
            {
                string dir = Path.GetDirectoryName(_hostsPath);
                string tempFile = Path.Combine(dir, "hosts.tmp_" + Guid.NewGuid().ToString("N"));

                File.WriteAllText(tempFile, content, new UTF8Encoding(false));

                if (File.Exists(_hostsPath))
                {
                    File.SetAttributes(_hostsPath, FileAttributes.Normal);
                }

                File.Copy(tempFile, _hostsPath, true);
                File.Delete(tempFile);

                FlushDns();
                Logger.Log(string.Format("Hosts успешно записан ({0} байт)", Encoding.UTF8.GetByteCount(content)));
                return true;
            }
            catch (Exception ex)
            {
                Logger.Log("Ошибка записи в hosts: " + ex.Message);
                return false;
            }
        }

        public BackupEntry CreateBackup()
        {
            if (!File.Exists(_hostsPath)) return null;

            try
            {
                string timestamp = DateTime.Now.ToString("yyyyMMdd_HHmmss");
                string backupName = string.Format("hosts_{0}.bak", timestamp);
                string destPath = Path.Combine(_backupDir, backupName);

                File.Copy(_hostsPath, destPath, true);

                var fileInfo = new FileInfo(destPath);
                var entry = new BackupEntry
                {
                    FileName = backupName,
                    Date = DateTime.Now.ToString("dd.MM.yyyy HH:mm:ss"),
                    Size = fileInfo.Length,
                    Path = destPath
                };

                Logger.Log("Создан бэкап: " + backupName);
                CleanOldBackups(20);
                return entry;
            }
            catch (Exception ex)
            {
                Logger.Log("Ошибка создания бэкапа: " + ex.Message);
                return null;
            }
        }

        public List<BackupEntry> GetBackups()
        {
            var list = new List<BackupEntry>();
            if (!Directory.Exists(_backupDir)) return list;

            try
            {
                string[] files = Directory.GetFiles(_backupDir, "hosts_*.bak");
                foreach (string file in files)
                {
                    var fileInfo = new FileInfo(file);
                    string name = Path.GetFileName(file);
                    string dateStr = fileInfo.CreationTime.ToString("dd.MM.yyyy HH:mm:ss");

                    list.Add(new BackupEntry
                    {
                        FileName = name,
                        Date = dateStr,
                        Size = fileInfo.Length,
                        Path = file
                    });
                }

                list.Sort((a, b) => string.Compare(b.FileName, a.FileName, StringComparison.Ordinal));
            }
            catch (Exception ex)
            {
                Logger.Log("Ошибка получения списка бэкапов: " + ex.Message);
            }

            return list;
        }

        public bool RestoreBackup(string backupPath)
        {
            if (!File.Exists(backupPath))
            {
                Logger.Log("Файл бэкапа не найден: " + backupPath);
                return false;
            }

            try
            {
                CreateBackup();

                if (File.Exists(_hostsPath))
                {
                    File.SetAttributes(_hostsPath, FileAttributes.Normal);
                }

                File.Copy(backupPath, _hostsPath, true);
                FlushDns();

                Logger.Log("Восстановлен бэкап: " + Path.GetFileName(backupPath));
                return true;
            }
            catch (Exception ex)
            {
                Logger.Log("Ошибка восстановления бэкапа: " + ex.Message);
                return false;
            }
        }

        public void FlushDns()
        {
            try
            {
                DnsFlushResolverCache();
            }
            catch
            {
            }

            try
            {
                var psi = new ProcessStartInfo
                {
                    FileName = "ipconfig",
                    Arguments = "/flushdns",
                    CreateNoWindow = true,
                    UseShellExecute = false,
                    RedirectStandardOutput = true
                };
                using (var proc = Process.Start(psi))
                {
                    proc.WaitForExit(3000);
                }
                Logger.Log("Кэш DNS успешно сброшен (ipconfig /flushdns)");
            }
            catch (Exception ex)
            {
                Logger.Log("Сброс DNS: " + ex.Message);
            }
        }

        private void CleanOldBackups(int keepCount)
        {
            try
            {
                var files = new DirectoryInfo(_backupDir).GetFiles("hosts_*.bak");
                if (files.Length <= keepCount) return;

                Array.Sort(files, (a, b) => b.CreationTime.CompareTo(a.CreationTime));

                for (int i = keepCount; i < files.Length; i++)
                {
                    files[i].Delete();
                }
            }
            catch
            {
            }
        }
    }
}
