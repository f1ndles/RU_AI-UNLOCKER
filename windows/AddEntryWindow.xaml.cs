using System.Windows;

namespace RUAIUnlocker
{
    public partial class AddEntryWindow : Window
    {
        public string Ip { get; private set; }
        public string Domain { get; private set; }

        public AddEntryWindow()
        {
            InitializeComponent();
            TxtDomain.Focus();
        }

        private void BtnAdd_Click(object sender, RoutedEventArgs e)
        {
            string ip = TxtIp.Text != null ? TxtIp.Text.Trim() : string.Empty;
            string domain = TxtDomain.Text != null ? TxtDomain.Text.Trim() : string.Empty;

            if (string.IsNullOrEmpty(ip))
            {
                MessageBox.Show("Введите IP-адрес.", "Внимание", MessageBoxButton.OK, MessageBoxImage.Warning);
                TxtIp.Focus();
                return;
            }

            if (string.IsNullOrEmpty(domain))
            {
                MessageBox.Show("Введите домен.", "Внимание", MessageBoxButton.OK, MessageBoxImage.Warning);
                TxtDomain.Focus();
                return;
            }

            Ip = ip;
            Domain = domain;
            DialogResult = true;
            Close();
        }

        private void BtnCancel_Click(object sender, RoutedEventArgs e)
        {
            DialogResult = false;
            Close();
        }
    }
}
