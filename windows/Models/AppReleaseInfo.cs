using System;

namespace RUAIUnlocker.Models
{
    public class AppReleaseInfo
    {
        public bool HasUpdate { get; set; }
        public string TagName { get; set; }
        public string Version { get; set; }
        public string ReleaseName { get; set; }
        public string Changelog { get; set; }
        public string DownloadUrl { get; set; }
        public string HtmlUrl { get; set; }
        public long AssetSize { get; set; }
        public string ErrorMessage { get; set; }

        public AppReleaseInfo()
        {
            TagName = string.Empty;
            Version = string.Empty;
            ReleaseName = string.Empty;
            Changelog = string.Empty;
            DownloadUrl = string.Empty;
            HtmlUrl = string.Empty;
            ErrorMessage = string.Empty;
        }
    }
}
