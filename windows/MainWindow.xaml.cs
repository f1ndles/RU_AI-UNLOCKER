using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using RUAIUnlocker.Models;
using RUAIUnlocker.Services;

namespace RUAIUnlocker
{
    public partial class MainWindow : Window
    {
        private readonly HostsService _hostsService;
        private readonly UpdateService _updateService;
        private readonly AppUpdateService _appUpdateService;

        private HostsInfo _currentHostsInfo;
        private List<HostEntry> _allEntries;
        private string _pendingUpdateContent;
        private AppReleaseInfo _latestAppRelease;

        public MainWindow()
        {
            InitializeComponent();

            _hostsService = new HostsService();
            _updateService = new UpdateService(_hostsService);
            _appUpdateService = new AppUpdateService();
            _allEntries = new List<HostEntry>();

            LbLogs.ItemsSource = Logger.Logs;
            TxtAppVersion.Text = "v" + AppUpdateService.CURRENT_VERSION;
            TxtAppInfo.Text = string.Format("RU AI Unlocker v{0}  |  Репозиторий: github.com/f1ndles/RU_AI-UNLOCKER  |  Источник хостов: dns.malw.link", AppUpdateService.CURRENT_VERSION);

            Loaded += MainWindow_Loaded;
        }

        private async void MainWindow_Loaded(object sender, RoutedEventArgs e)
        {
            Logger.Log("RU AI Unlocker (Windows) запущен");
            RefreshLocalData();
            await CheckUpdatesAsync(false);
            await CheckAppUpdatesAsync(false);
        }

        private void RefreshLocalData()
        {
            try
            {
                _currentHostsInfo = _hostsService.ReadLocalHosts();
                _allEntries = _currentHostsInfo.Entries ?? new List<HostEntry>();

                TxtLastUpdate.Text = string.IsNullOrEmpty(_currentHostsInfo.LastUpdate)
                    ? "Не определена"
                    : _currentHostsInfo.LastUpdate;

                TxtEntriesCount.Text = _currentHostsInfo.EntriesCount.ToString();

                if (_currentHostsInfo.FileSize < 1024)
                {
                    TxtFileSize.Text = string.Format("{0} B", _currentHostsInfo.FileSize);
                }
                else if (_currentHostsInfo.FileSize < 1024 * 1024)
                {
                    TxtFileSize.Text = string.Format("{0:F1} KB", _currentHostsInfo.FileSize / 1024.0);
                }
                else
                {
                    TxtFileSize.Text = string.Format("{0:F1} MB", _currentHostsInfo.FileSize / (1024.0 * 1024.0));
                }

                ApplyFilter();
                RefreshBackups();
            }
            catch (Exception ex)
            {
                Logger.Log("Ошибка обновления данных: " + ex.Message);
            }
        }

        private void RefreshBackups()
        {
            try
            {
                var backups = _hostsService.GetBackups();
                DgBackups.ItemsSource = backups;
            }
            catch
            {
            }
        }

        private void ApplyFilter()
        {
            string query = TxtSearch.Text != null ? TxtSearch.Text.Trim().ToLowerInvariant() : string.Empty;

            if (string.IsNullOrEmpty(query))
            {
                DgHosts.ItemsSource = _allEntries;
                TxtFilterCount.Text = string.Format("Всего записей: {0}", _allEntries.Count);
            }
            else
            {
                var filtered = _allEntries.Where(x =>
                    (x.Domain != null && x.Domain.ToLowerInvariant().Contains(query)) ||
                    (x.Ip != null && x.Ip.Contains(query)) ||
                    (x.Section != null && x.Section.ToLowerInvariant().Contains(query))
                ).ToList();

                DgHosts.ItemsSource = filtered;
                TxtFilterCount.Text = string.Format("Найдено: {0} из {1}", filtered.Count, _allEntries.Count);
            }
        }

        private async Task CheckUpdatesAsync(bool showMessageIfNoUpdate)
        {
            SetLoading(true, "Проверка обновлений...");

            try
            {
                string localDate = _currentHostsInfo != null ? _currentHostsInfo.LastUpdate : string.Empty;
                var result = await _updateService.CheckForUpdateAsync(localDate);

                if (result.HasUpdate)
                {
                    _pendingUpdateContent = result.RemoteContent;
                    UpdateBannerText.Text = string.Format("Новая версия hosts от: {0}", result.RemoteDate);
                    UpdateBannerBorder.Visibility = Visibility.Visible;
                    SetLoading(false, string.Format("Доступно обновление от {0}", result.RemoteDate));

                    if (showMessageIfNoUpdate)
                    {
                        var answer = MessageBox.Show(
                            string.Format("Доступно обновление hosts от {0}.\n\nУстановить сейчас?", result.RemoteDate),
                            "Обновление найдено",
                            MessageBoxButton.YesNo,
                            MessageBoxImage.Information);

                        if (answer == MessageBoxResult.Yes)
                        {
                            await PerformUpdateAsync();
                        }
                    }
                }
                else
                {
                    UpdateBannerBorder.Visibility = Visibility.Collapsed;
                    SetLoading(false, "У вас актуальная версия hosts");

                    if (showMessageIfNoUpdate)
                    {
                        MessageBox.Show("У вас уже установлена актуальная версия hosts.", "Проверка обновлений", MessageBoxButton.OK, MessageBoxImage.Information);
                    }
                }
            }
            catch (Exception ex)
            {
                SetLoading(false, "Ошибка: " + ex.Message);
            }
        }

        private async Task PerformUpdateAsync()
        {
            SetLoading(true, "Загрузка и применение hosts...");

            try
            {
                string content = _pendingUpdateContent;
                if (string.IsNullOrEmpty(content))
                {
                    content = await _updateService.DownloadHostsContentAsync();
                }

                if (string.IsNullOrEmpty(content))
                {
                    MessageBox.Show("Не удалось загрузить hosts файл из репозитория.", "Ошибка", MessageBoxButton.OK, MessageBoxImage.Error);
                    SetLoading(false, "Ошибка загрузки");
                    return;
                }

                bool success = _updateService.ApplyUpdate(content);
                if (success)
                {
                    _pendingUpdateContent = null;
                    UpdateBannerBorder.Visibility = Visibility.Collapsed;
                    RefreshLocalData();
                    SetLoading(false, "Hosts успешно обновлен и DNS сброшен!");
                    MessageBox.Show("Hosts файл успешно обновлен!\nКэш DNS очищен.", "Успех", MessageBoxButton.OK, MessageBoxImage.Information);
                }
                else
                {
                    SetLoading(false, "Не удалось обновить hosts");
                    MessageBox.Show("Не удалось записать файл hosts. Убедитесь, что программа запущена от имени Администратора.", "Ошибка", MessageBoxButton.OK, MessageBoxImage.Error);
                }
            }
            catch (Exception ex)
            {
                SetLoading(false, "Ошибка: " + ex.Message);
                MessageBox.Show("Ошибка: " + ex.Message, "Ошибка", MessageBoxButton.OK, MessageBoxImage.Error);
            }
        }

        private async void BtnCheckUpdate_Click(object sender, RoutedEventArgs e)
        {
            await CheckUpdatesAsync(true);
        }

        private async void BtnCheckAppUpdate_Click(object sender, RoutedEventArgs e)
        {
            await CheckAppUpdatesAsync(true);
        }

        private async Task CheckAppUpdatesAsync(bool showNotification)
        {
            try
            {
                var result = await _appUpdateService.CheckForAppUpdateAsync();
                _latestAppRelease = result;

                if (result != null && result.HasUpdate)
                {
                    AppUpdateBannerBorder.Visibility = Visibility.Visible;
                    TxtNewAppVersion.Text = "v" + result.Version;
                    string note = string.IsNullOrEmpty(result.Changelog) ? "Доступно обновление на GitHub" : result.Changelog.Trim();
                    if (note.Length > 150) note = note.Substring(0, 147) + "...";
                    TxtAppChangelog.Text = note;

                    if (showNotification)
                    {
                        MessageBox.Show(
                            string.Format("Найдена новая версия программы: v{0}\n\nЧто нового:\n{1}", result.Version, result.Changelog),
                            "Обновление программы",
                            MessageBoxButton.OK,
                            MessageBoxImage.Information);
                    }
                }
                else
                {
                    AppUpdateBannerBorder.Visibility = Visibility.Collapsed;
                    if (showNotification)
                    {
                        if (result != null && !string.IsNullOrEmpty(result.ErrorMessage))
                        {
                            MessageBox.Show("Не удалось проверить обновления: " + result.ErrorMessage, "Проверка обновлений", MessageBoxButton.OK, MessageBoxImage.Warning);
                        }
                        else
                        {
                            MessageBox.Show(string.Format("У вас установлена актуальная версия программы (v{0}).", AppUpdateService.CURRENT_VERSION), "Обновление программы", MessageBoxButton.OK, MessageBoxImage.Information);
                        }
                    }
                }
            }
            catch (Exception ex)
            {
                if (showNotification)
                {
                    MessageBox.Show("Ошибка при проверке обновлений программы: " + ex.Message, "Ошибка", MessageBoxButton.OK, MessageBoxImage.Error);
                }
            }
        }

        private async void BtnApplyAppUpdate_Click(object sender, RoutedEventArgs e)
        {
            if (_latestAppRelease == null || !_latestAppRelease.HasUpdate) return;

            if (string.IsNullOrEmpty(_latestAppRelease.DownloadUrl))
            {
                var ask = MessageBox.Show(
                    "Файл .exe для Windows не найден во вложениях релиза.\nОткрыть страницу релиза на GitHub?",
                    "Обновление программы",
                    MessageBoxButton.YesNo,
                    MessageBoxImage.Question);
                if (ask == MessageBoxResult.Yes)
                {
                    _appUpdateService.OpenUrl(_latestAppRelease.HtmlUrl);
                }
                return;
            }

            var confirm = MessageBox.Show(
                string.Format("Скачать и установить версию v{0}?\n\nПосле скачивания программа автоматически обновится и перезапустится.", _latestAppRelease.Version),
                "Подтверждение обновления",
                MessageBoxButton.YesNo,
                MessageBoxImage.Question);

            if (confirm != MessageBoxResult.Yes) return;

            BtnApplyAppUpdate.IsEnabled = false;
            PrgAppUpdate.Visibility = Visibility.Visible;
            PrgAppUpdate.Value = 0;

            var progress = new Progress<int>(percent =>
            {
                PrgAppUpdate.Value = percent;
            });

            try
            {
                await _appUpdateService.DownloadAndInstallUpdateAsync(_latestAppRelease.DownloadUrl, progress);
            }
            catch (Exception ex)
            {
                BtnApplyAppUpdate.IsEnabled = true;
                PrgAppUpdate.Visibility = Visibility.Collapsed;
                MessageBox.Show("Ошибка при обновлении: " + ex.Message + "\n\nВы можете скачать обновление вручную с GitHub.", "Ошибка обновления", MessageBoxButton.OK, MessageBoxImage.Error);
                _appUpdateService.OpenUrl(_latestAppRelease.HtmlUrl);
            }
        }

        private void BtnOpenAppRelease_Click(object sender, RoutedEventArgs e)
        {
            _appUpdateService.OpenUrl(_latestAppRelease != null ? _latestAppRelease.HtmlUrl : null);
        }

        private async void BtnUpdateHosts_Click(object sender, RoutedEventArgs e)
        {
            await PerformUpdateAsync();
        }

        private void BtnCreateBackup_Click(object sender, RoutedEventArgs e)
        {
            var entry = _hostsService.CreateBackup();
            if (entry != null)
            {
                RefreshBackups();
                MessageBox.Show(string.Format("Бэкап успешно создан:\n{0}", entry.FileName), "Бэкап", MessageBoxButton.OK, MessageBoxImage.Information);
            }
            else
            {
                MessageBox.Show("Не удалось создать бэкап.", "Ошибка", MessageBoxButton.OK, MessageBoxImage.Error);
            }
        }

        private void BtnRestoreBackup_Click(object sender, RoutedEventArgs e)
        {
            var selected = DgBackups.SelectedItem as BackupEntry;
            if (selected == null)
            {
                MessageBox.Show("Выберите бэкап из списка для отката.", "Внимание", MessageBoxButton.OK, MessageBoxImage.Warning);
                return;
            }

            var confirm = MessageBox.Show(
                string.Format("Вы уверены, что хотите откатить hosts на версию от {0}?", selected.Date),
                "Подтверждение отката",
                MessageBoxButton.YesNo,
                MessageBoxImage.Question);

            if (confirm != MessageBoxResult.Yes) return;

            bool ok = _hostsService.RestoreBackup(selected.Path);
            if (ok)
            {
                RefreshLocalData();
                MessageBox.Show("Hosts файл успешно восстановлен из бэкапа!\nКэш DNS очищен.", "Успех", MessageBoxButton.OK, MessageBoxImage.Information);
            }
            else
            {
                MessageBox.Show("Не удалось восстановить бэкап.", "Ошибка", MessageBoxButton.OK, MessageBoxImage.Error);
            }
        }

        private void BtnFlushDns_Click(object sender, RoutedEventArgs e)
        {
            _hostsService.FlushDns();
            MessageBox.Show("Кэш DNS успешно сброшен!", "Сброс DNS", MessageBoxButton.OK, MessageBoxImage.Information);
        }

        private void BtnOpenHostsFile_Click(object sender, RoutedEventArgs e)
        {
            try
            {
                Process.Start("notepad.exe", _hostsService.GetHostsPath());
            }
            catch (Exception ex)
            {
                MessageBox.Show("Ошибка открытия hosts: " + ex.Message, "Ошибка", MessageBoxButton.OK, MessageBoxImage.Error);
            }
        }

        private void BtnOpenBackupDir_Click(object sender, RoutedEventArgs e)
        {
            try
            {
                Process.Start("explorer.exe", _hostsService.GetBackupDir());
            }
            catch (Exception ex)
            {
                MessageBox.Show("Ошибка: " + ex.Message, "Ошибка", MessageBoxButton.OK, MessageBoxImage.Error);
            }
        }

        private void BtnCopyLogs_Click(object sender, RoutedEventArgs e)
        {
            try
            {
                string text = string.Join(Environment.NewLine, Logger.Logs);
                Clipboard.SetText(text);
                MessageBox.Show("Логи скопированы в буфер обмена.", "Журнал", MessageBoxButton.OK, MessageBoxImage.Information);
            }
            catch
            {
            }
        }

        private void BtnClearLogs_Click(object sender, RoutedEventArgs e)
        {
            Logger.Clear();
        }

        private void BtnOpenLogFile_Click(object sender, RoutedEventArgs e)
        {
            try
            {
                string logPath = Logger.GetLogFilePath();
                if (File.Exists(logPath))
                {
                    Process.Start("notepad.exe", logPath);
                }
            }
            catch
            {
            }
        }

        private void TxtSearch_TextChanged(object sender, TextChangedEventArgs e)
        {
            ApplyFilter();
        }

        private void BtnClearSearch_Click(object sender, RoutedEventArgs e)
        {
            TxtSearch.Text = string.Empty;
        }

        private void BtnAddEntry_Click(object sender, RoutedEventArgs e)
        {
            var addWindow = new AddEntryWindow();
            addWindow.Owner = this;
            if (addWindow.ShowDialog() == true)
            {
                string raw = _hostsService.ReadRawHosts();
                string newRaw = raw.TrimEnd() + Environment.NewLine + string.Format("{0} {1}", addWindow.Ip, addWindow.Domain) + Environment.NewLine;
                if (_hostsService.WriteLocalHosts(newRaw))
                {
                    RefreshLocalData();
                    MessageBox.Show("Запись успешно добавлена!", "Успех", MessageBoxButton.OK, MessageBoxImage.Information);
                }
            }
        }

        private void BtnDeleteEntry_Click(object sender, RoutedEventArgs e)
        {
            var selected = DgHosts.SelectedItem as HostEntry;
            if (selected == null)
            {
                MessageBox.Show("Выберите запись для удаления.", "Внимание", MessageBoxButton.OK, MessageBoxImage.Warning);
                return;
            }

            var confirm = MessageBox.Show(
                string.Format("Удалить запись '{0} {1}'?", selected.Ip, selected.Domain),
                "Удаление",
                MessageBoxButton.YesNo,
                MessageBoxImage.Question);

            if (confirm != MessageBoxResult.Yes) return;

            string raw = _hostsService.ReadRawHosts();
            var lines = new List<string>(raw.Split(new string[] { "\r\n", "\n" }, StringSplitOptions.None));
            lines.RemoveAll(line =>
            {
                string t = line.Trim();
                if (t.StartsWith("#") || string.IsNullOrEmpty(t)) return false;
                string[] parts = t.Split(new char[] { ' ', '\t' }, StringSplitOptions.RemoveEmptyEntries);
                return parts.Length >= 2 && string.Equals(parts[1], selected.Domain, StringComparison.OrdinalIgnoreCase);
            });

            string updated = string.Join(Environment.NewLine, lines);
            if (_hostsService.WriteLocalHosts(updated))
            {
                RefreshLocalData();
                MessageBox.Show("Запись успешно удалена!", "Успех", MessageBoxButton.OK, MessageBoxImage.Information);
            }
        }

        private void SetLoading(bool loading, string statusText)
        {
            TxtStatus.Text = statusText;
            PbStatus.Visibility = loading ? Visibility.Visible : Visibility.Collapsed;
            PbStatus.IsIndeterminate = loading;
            BtnCheckUpdate.IsEnabled = !loading;
            BtnUpdateHosts.IsEnabled = !loading;
        }
    }
}
