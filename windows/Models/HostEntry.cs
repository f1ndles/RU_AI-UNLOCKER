namespace RUAIUnlocker.Models
{
    public class HostEntry
    {
        public string Ip { get; set; }
        public string Domain { get; set; }
        public string Section { get; set; }

        public HostEntry()
        {
            Ip = string.Empty;
            Domain = string.Empty;
            Section = string.Empty;
        }

        public HostEntry(string ip, string domain, string section = "")
        {
            Ip = ip;
            Domain = domain;
            Section = section;
        }

        public override string ToString()
        {
            return string.Format("{0} {1}", Ip, Domain);
        }
    }
}
