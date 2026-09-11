using System.Collections.Generic;

namespace RUAIUnlocker.Models
{
    public class HostsInfo
    {
        public string LastUpdate { get; set; }
        public int EntriesCount { get; set; }
        public long FileSize { get; set; }
        public List<HostEntry> Entries { get; set; }

        public HostsInfo()
        {
            LastUpdate = string.Empty;
            EntriesCount = 0;
            FileSize = 0;
            Entries = new List<HostEntry>();
        }
    }
}
