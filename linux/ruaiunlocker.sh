#!/usr/bin/env bash

HOSTS_URL="https://raw.githubusercontent.com/ImMALWARE/dns.malw.link/refs/heads/master/hosts"
GITHUB_API="https://api.github.com/repos/f1ndles/RU_AI-UNLOCKER/releases/latest"
GUI_RAW_URL="https://raw.githubusercontent.com/f1ndles/RU_AI-UNLOCKER/main/linux/ruaiunlocker_gui.py"
HOSTS_FILE="/etc/hosts"
BACKUP_DIR="/etc/ruaiunlocker/backups"
VERSION="1.0.0"

check_root() {
    if [ "$(id -u)" -ne 0 ]; then
        echo "[!] Для этой операции требуются права суперпользователя (root)."
        exec sudo bash "$0" "$@"
    fi
}

has_display() {
    if [ -n "$DISPLAY" ] || [ -n "$WAYLAND_DISPLAY" ]; then
        return 0
    fi
    return 1
}

flush_dns() {
    echo "[*] Сброс кэша DNS..."
    if command -v resolvectl >/dev/null 2>&1; then
        resolvectl flush-caches || true
    elif command -v systemd-resolve >/dev/null 2>&1; then
        systemd-resolve --flush-caches || true
    fi
    if command -v systemctl >/dev/null 2>&1; then
        systemctl restart nscd 2>/dev/null || true
        systemctl restart systemd-resolved 2>/dev/null || true
    fi
    echo "[+] Кэш DNS успешно сброшен."
}

create_backup() {
    mkdir -p "$BACKUP_DIR"
    local timestamp
    timestamp=$(date +"%Y%m%d_%H%M%S")
    local backup_file="$BACKUP_DIR/hosts.bak.$timestamp"
    if [ -f "$HOSTS_FILE" ]; then
        cp "$HOSTS_FILE" "$backup_file"
        echo "[+] Создан бэкап: $backup_file"
    fi
}

get_local_hosts_date() {
    if [ -f "$HOSTS_FILE" ]; then
        grep -i "Последнее обновление:" "$HOSTS_FILE" | head -n 1 | awk -F ':' '{print $2}' | xargs || echo ""
    else
        echo ""
    fi
}

update_hosts() {
    check_root
    echo "[*] Скачивание актуального hosts файла..."
    local tmp_file
    tmp_file=$(mktemp)
    if curl -sSL -H "User-Agent: RUAIUnlocker-Linux/$VERSION" "$HOSTS_URL" -o "$tmp_file"; then
        if [ -s "$tmp_file" ]; then
            create_backup
            cp "$tmp_file" "$HOSTS_FILE"
            chmod 644 "$HOSTS_FILE"
            rm -f "$tmp_file"
            flush_dns
            local update_date
            update_date=$(get_local_hosts_date)
            echo "[+] Hosts успешно обновлен! Дата: ${update_date:-не указана}"
        else
            rm -f "$tmp_file"
            echo "[-] Ошибка: скачанный файл hosts пуст."
            return 1
        fi
    else
        rm -f "$tmp_file"
        echo "[-] Ошибка при скачивании hosts с репозитория."
        return 1
    fi
}

check_hosts_update() {
    echo "[*] Проверка версии hosts..."
    local local_date
    local_date=$(get_local_hosts_date)
    local remote_date
    remote_date=$(curl -sSL -H "User-Agent: RUAIUnlocker-Linux/$VERSION" "$HOSTS_URL" | grep -i "Последнее обновление:" | head -n 1 | awk -F ':' '{print $2}' | xargs || echo "")
    
    echo "  Текущая версия hosts: ${local_date:-Не определена}"
    echo "  Доступная версия:     ${remote_date:-Не удалось определить}"

    if [ -n "$remote_date" ] && [ "$local_date" != "$remote_date" ]; then
        echo "[!] Доступно обновление hosts!"
        return 0
    else
        echo "[+] Hosts актуален."
        return 1
    fi
}

check_app_update() {
    echo "[*] Проверка обновлений программы на GitHub..."
    local release_json
    release_json=$(curl -sSL -H "User-Agent: RUAIUnlocker-Linux/$VERSION" "$GITHUB_API" 2>/dev/null || echo "")
    local tag_name=""
    local download_url=""
    if [ -n "$release_json" ] && ! echo "$release_json" | grep -q "API rate limit"; then
        tag_name=$(echo "$release_json" | grep -o '"tag_name": *"[^"]*"' | head -n 1 | awk -F '"' '{print $4}' | sed 's/^[vV]//')
        download_url=$(echo "$release_json" | grep -o '"browser_download_url": *"[^"]*ruaiunlocker[^"]*"' | head -n 1 | awk -F '"' '{print $4}')
    fi

    if [ -z "$tag_name" ]; then
        local atom_xml
        atom_xml=$(curl -sSL -H "User-Agent: RUAIUnlocker-Linux/$VERSION" "https://github.com/f1ndles/RU_AI-UNLOCKER/releases.atom" 2>/dev/null || echo "")
        for t in $(echo "$atom_xml" | grep -o 'releases/tag/[^"]*' | cut -d'/' -f3); do
            local candidate="https://github.com/f1ndles/RU_AI-UNLOCKER/releases/download/$t/ruaiunlocker.sh"
            if curl -sI "$candidate" | grep -qE "HTTP/.* (200|302)"; then
                tag_name=$(echo "$t" | sed 's/^[vV]//')
                download_url="$candidate"
                break
            fi
        done
    fi

    if [ -n "$tag_name" ] && [ "$tag_name" != "$VERSION" ]; then
        echo "[!] Доступна новая версия программы: v$tag_name (текущая: v$VERSION)"
        if [ -n "$download_url" ]; then
            echo "  Ссылка для скачивания: $download_url"
            return 0
        fi
    else
        echo "[+] Программа актуальна (v$VERSION)."
    fi
    return 1
}

self_update() {
    check_root
    echo "[*] Поиск обновления скрипта..."
    local download_url=""
    local release_json
    release_json=$(curl -sSL -H "User-Agent: RUAIUnlocker-Linux/$VERSION" "$GITHUB_API" 2>/dev/null || echo "")
    if [ -n "$release_json" ] && ! echo "$release_json" | grep -q "API rate limit"; then
        download_url=$(echo "$release_json" | grep -o '"browser_download_url": *"[^"]*ruaiunlocker\.sh"' | head -n 1 | awk -F '"' '{print $4}')
    fi

    if [ -z "$download_url" ]; then
        local atom_xml
        atom_xml=$(curl -sSL -H "User-Agent: RUAIUnlocker-Linux/$VERSION" "https://github.com/f1ndles/RU_AI-UNLOCKER/releases.atom" 2>/dev/null || echo "")
        for t in $(echo "$atom_xml" | grep -o 'releases/tag/[^"]*' | cut -d'/' -f3); do
            local candidate="https://github.com/f1ndles/RU_AI-UNLOCKER/releases/download/$t/ruaiunlocker.sh"
            if curl -sI "$candidate" | grep -qE "HTTP/.* (200|302)"; then
                download_url="$candidate"
                break
            fi
        done
    fi

    if [ -n "$download_url" ]; then
        echo "[*] Скачивание новой версии с $download_url..."
        local script_path
        script_path=$(readlink -f "$0")
        local tmp_script
        tmp_script=$(mktemp)
        if curl -sSL "$download_url" -o "$tmp_script"; then
            chmod +x "$tmp_script"
            mv "$tmp_script" "$script_path"
            echo "[+] Программа успешно обновлена! Перезапустите скрипт."
            exit 0
        else
            rm -f "$tmp_script"
            echo "[-] Ошибка при скачивании обновления."
            return 1
        fi
    else
        echo "[-] Скрипт ruaiunlocker.sh не найден во вложениях последнего релиза."
        return 1
    fi
}

list_hosts_entries() {
    local filter="$1"
    if [ ! -f "$HOSTS_FILE" ]; then
        echo "[-] Файл $HOSTS_FILE не найден."
        return 1
    fi

    echo "========================================="
    echo "          Записи файла hosts             "
    echo "========================================="
    printf "%-6s %-18s %-45s\n" "#" "IP-адрес" "Домен"
    echo "-----------------------------------------"

    local count=0
    local line_num=0
    while IFS= read -r line || [ -n "$line" ]; do
        local trimmed
        trimmed=$(echo "$line" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')
        if [ -z "$trimmed" ] || [[ "$trimmed" =~ ^# ]]; then
            continue
        fi

        local ip domain
        ip=$(echo "$trimmed" | awk '{print $1}')
        domain=$(echo "$trimmed" | awk '{print $2}')

        if [ -n "$ip" ] && [ -n "$domain" ]; then
            line_num=$((line_num + 1))
            if [ -n "$filter" ]; then
                if echo "$domain $ip" | grep -qi "$filter"; then
                    count=$((count + 1))
                    printf "%-6s %-18s %-45s\n" "[$line_num]" "$ip" "$domain"
                fi
            else
                count=$((count + 1))
                printf "%-6s %-18s %-45s\n" "[$line_num]" "$ip" "$domain"
            fi
        fi
    done < "$HOSTS_FILE"

    echo "========================================="
    echo "Всего найдено записей: $count"
}

add_hosts_entry() {
    check_root
    local ip="$1"
    local domain="$2"

    if [ -z "$ip" ] || [ -z "$domain" ]; then
        echo -n "Введите IP-адрес (по умолчанию 127.0.0.1): "
        read -r ip
        ip="${ip:-127.0.0.1}"
        echo -n "Введите доменное имя: "
        read -r domain
    fi

    if [ -z "$domain" ]; then
        echo "[-] Домен не может быть пустым."
        return 1
    fi

    create_backup
    printf "\n%s\t%s\n" "$ip" "$domain" >> "$HOSTS_FILE"
    flush_dns
    echo "[+] Запись '$ip $domain' успешно добавлена в $HOSTS_FILE!"
}

delete_hosts_entry() {
    check_root
    local target="$1"

    if [ -z "$target" ]; then
        list_hosts_entries ""
        echo -n "Введите домен или точный IP для удаления: "
        read -r target
    fi

    if [ -z "$target" ]; then
        echo "[-] Ничего не введено."
        return 1
    fi

    if ! grep -qi "[[:space:]]$target" "$HOSTS_FILE" && ! grep -qi "^$target[[:space:]]" "$HOSTS_FILE"; then
        echo "[-] Запись '$target' не найдена в $HOSTS_FILE."
        return 1
    fi

    create_backup
    local tmp_file
    tmp_file=$(mktemp)
    grep -vi "[[:space:]]$target" "$HOSTS_FILE" | grep -vi "^$target[[:space:]]" > "$tmp_file"
    cp "$tmp_file" "$HOSTS_FILE"
    chmod 644 "$HOSTS_FILE"
    rm -f "$tmp_file"
    flush_dns
    echo "[+] Запись '$target' успешно удалена из $HOSTS_FILE!"
}

rollback() {
    check_root
    if [ ! -d "$BACKUP_DIR" ]; then
        echo "[-] Папка бэкапов не найдена."
        return 1
    fi

    local backups
    backups=($(ls -t "$BACKUP_DIR"/hosts.bak.* 2>/dev/null || true))
    if [ ${#backups[@]} -eq 0 ]; then
        echo "[-] Нет доступных бэкапов."
        return 1
    fi

    echo "Доступные резервные копии:"
    for i in "${!backups[@]}"; do
        echo "  $((i + 1))) ${backups[$i]}"
    done

    echo -n "Выберите номер для восстановления (1-${#backups[@]}): "
    read -r choice
    if [[ "$choice" =~ ^[0-9]+$ ]] && [ "$choice" -ge 1 ] && [ "$choice" -le "${#backups[@]}" ]; then
        local chosen="${backups[$((choice - 1))]}"
        cp "$chosen" "$HOSTS_FILE"
        chmod 644 "$HOSTS_FILE"
        flush_dns
        echo "[+] Бэкап $chosen успешно применен!"
    else
        echo "[-] Неверный выбор."
    fi
}

check_and_install_gui_deps() {
    if command -v python3 >/dev/null 2>&1 && python3 -c "import tkinter" >/dev/null 2>&1; then
        return 0
    fi

    echo "========================================="
    echo "       Настройка окружения GUI           "
    echo "========================================="
    echo "Для запуска графического интерфейса требуется Python 3 и библиотека Tkinter."
    echo -n "Хотите установить необходимые пакеты автоматически? [Y/n]: "
    read -r ans
    ans="${ans:-Y}"
    if [[ "$ans" =~ ^[YyДд]$ ]]; then
        echo "[*] Установка необходимых пакетов..."
        if command -v apt-get >/dev/null 2>&1; then
            sudo apt-get update && sudo apt-get install -y python3 python3-tk
        elif command -v dnf >/dev/null 2>&1; then
            sudo dnf install -y python3 python3-tkinter
        elif command -v pacman >/dev/null 2>&1; then
            sudo pacman -S --noconfirm python tk
        elif command -v zypper >/dev/null 2>&1; then
            sudo zypper install -y python3 python3-tk
        elif command -v yum >/dev/null 2>&1; then
            sudo yum install -y python3 python3-tkinter
        else
            echo "[-] Пакетный менеджер не распознан. Установите python3-tk вручную."
            return 1
        fi

        if command -v python3 >/dev/null 2>&1 && python3 -c "import tkinter" >/dev/null 2>&1; then
            echo "[+] Графическое окружение успешно настроено!"
            return 0
        else
            echo "[-] Не удалось настроить Tkinter."
            return 1
        fi
    else
        echo "[*] Запуск в консольном режиме..."
        return 1
    fi
}

launch_gui() {
    if ! check_and_install_gui_deps; then
        return 1
    fi

    local script_dir
    script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    local gui_file="$script_dir/ruaiunlocker_gui.py"

    if [ ! -f "$gui_file" ]; then
        if [ -f "/usr/local/bin/ruaiunlocker_gui.py" ]; then
            gui_file="/usr/local/bin/ruaiunlocker_gui.py"
        else
            echo "[*] Загрузка графического модуля с GitHub..."
            if curl -sSL "$GUI_RAW_URL" -o "$gui_file"; then
                chmod +x "$gui_file"
            else
                echo "[-] Не удалось загрузить ruaiunlocker_gui.py. Переход в консольный режим."
                return 1
            fi
        fi
    fi

    if [ "$(id -u)" -eq 0 ] && [ -n "$SUDO_USER" ]; then
        su - "$SUDO_USER" -c "DISPLAY=$DISPLAY WAYLAND_DISPLAY=$WAYLAND_DISPLAY XAUTHORITY=$XAUTHORITY python3 '$gui_file'"
    else
        python3 "$gui_file"
    fi
    return 0
}

show_menu() {
    clear
    echo "========================================="
    echo "     RU AI Unlocker for Linux (v$VERSION) "
    echo "========================================="
    echo "1) Запустить графический интерфейс (GUI)"
    echo "2) Обновить hosts из репозитория"
    echo "3) Проверить обновление hosts"
    echo "4) Проверить обновление программы"
    echo "5) Просмотреть все записи hosts"
    echo "6) Поиск по записям hosts"
    echo "7) Добавить запись в hosts"
    echo "8) Удалить запись из hosts"
    echo "9) Восстановить hosts из бэкапа"
    echo "10) Сбросить кэш DNS"
    echo "11) Обновить скрипт (self-update)"
    echo "0) Выход"
    echo "========================================="
    echo -n "Выберите действие [0-11]: "
    read -r opt
    case "$opt" in
        1) launch_gui || show_menu ;;
        2) update_hosts ;;
        3) check_hosts_update ;;
        4) check_app_update ;;
        5) list_hosts_entries "" ;;
        6) 
            echo -n "Введите строку для поиска домена/IP: "
            read -r q
            list_hosts_entries "$q"
            ;;
        7) add_hosts_entry "" "" ;;
        8) delete_hosts_entry "" ;;
        9) rollback ;;
        10) check_root && flush_dns ;;
        11) self_update ;;
        0) exit 0 ;;
        *) echo "Неверная команда" ;;
    esac
}

case "$1" in
    --gui|-g) launch_gui || show_menu ;;
    --cli) show_menu ;;
    --update|-u) update_hosts ;;
    --check|-c) check_hosts_update ;;
    --check-app) check_app_update ;;
    --list|-l) list_hosts_entries "$2" ;;
    --add) add_hosts_entry "$2" "$3" ;;
    --del) delete_hosts_entry "$2" ;;
    --self-update) self_update ;;
    --rollback|-r) rollback ;;
    --flush-dns|-f) check_root && flush_dns ;;
    *)
        if has_display && [ -t 0 ]; then
            if ! launch_gui; then
                show_menu
            fi
        elif [ -t 0 ]; then
            show_menu
        else
            update_hosts
        fi
        ;;
esac
