namespace RUAIUnlocker.Models
{
    public class BackupEntry
    {
        public string FileName { get; set; }
        public string Date { get; set; }
        public long Size { get; set; }
        public string Path { get; set; }

        public string SizeFormatted
        {
            get
            {
                if (Size < 1024) return string.Format("{0} B", Size);
                if (Size < 1024 * 1024) return string.Format("{0:F1} KB", Size / 1024.0);
                return string.Format("{0:F1} MB", Size / (1024.0 * 1024.0));
            }
        }
    }
}
