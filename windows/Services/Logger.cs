using System;
using System.Collections.ObjectModel;
using System.IO;
using System.Windows;

namespace RUAIUnlocker.Services
{
    public class Logger
    {
        private static readonly object _lock = new object();
        public static ObservableCollection<string> Logs { get; private set; }
        private static string _logFilePath;

        static Logger()
        {
            Logs = new ObservableCollection<string>();
            try
            {
                string dir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "RUAIUnlocker");
                if (!Directory.Exists(dir))
                {
                    Directory.CreateDirectory(dir);
                }
                _logFilePath = Path.Combine(dir, "logs.txt");
            }
            catch
            {
            }
        }

        public static void Log(string message)
        {
            string line = string.Format("[{0:yyyy-MM-dd HH:mm:ss}] {1}", DateTime.Now, message);

            try
            {
                if (Application.Current != null && Application.Current.Dispatcher != null)
                {
                    Application.Current.Dispatcher.Invoke(() =>
                    {
                        Logs.Add(line);
                        if (Logs.Count > 1000) Logs.RemoveAt(0);
                    });
                }
            }
            catch
            {
            }

            try
            {
                lock (_lock)
                {
                    if (!string.IsNullOrEmpty(_logFilePath))
                    {
                        File.AppendAllText(_logFilePath, line + Environment.NewLine);
                    }
                }
            }
            catch
            {
            }
        }

        public static string GetLogFilePath()
        {
            return _logFilePath;
        }

        public static void Clear()
        {
            try
            {
                if (Application.Current != null && Application.Current.Dispatcher != null)
                {
                    Application.Current.Dispatcher.Invoke(() => Logs.Clear());
                }
                lock (_lock)
                {
                    if (File.Exists(_logFilePath))
                    {
                        File.WriteAllText(_logFilePath, string.Empty);
                    }
                }
            }
            catch
            {
            }
        }
    }
}
