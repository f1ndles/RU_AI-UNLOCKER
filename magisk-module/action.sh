#!/system/bin/sh
# action.sh v23.2
# - Сравнение версии по дате в комментарии hosts
# - Зеркало: raw.githubusercontent.com/ImMALWARE/dns.malw.link
# - Исправлен grep regex для busybox
# - Фикс READ ONLY через nsenter в namespace PID 1 (правильный порядок)

MODULE_DIR="/data/adb/modules/unlocker_zrpb"
DATA_DIR="/data/adb/unlocker_zrpb"

HOSTS_DATA="$DATA_DIR/hosts"
HOSTS_TMP="$DATA_DIR/hosts.tmp"
HOSTS_RAW="$DATA_DIR/hosts.raw"
BLACKLIST="$DATA_DIR/blacklist.conf"
BLOCKLIST="$DATA_DIR/blocklist.conf"
LOG_FILE="$DATA_DIR/logs.txt"

HOSTS_SYSTEM="/system/etc/hosts"
HOSTS_URL="https://raw.githubusercontent.com/ImMALWARE/dns.malw.link/refs/heads/master/hosts"

mkdir -p "$DATA_DIR"

# ── лог ──────────────────────────────────────────
_log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" >> "$LOG_FILE"; }
_print() { echo "$1"; _log "$1"; }
_err()   { echo "ERROR: $1"; _log "ERROR: $1"; }

# ── скачать файл ─────────────────────────────────
_download() {
    local url="$1" out="$2"
    if command -v curl >/dev/null 2>&1; then
        curl -fsSL --connect-timeout 15 --retry 2 -o "$out" "$url" 2>>"$LOG_FILE"
    elif command -v wget >/dev/null 2>&1; then
        wget -q --timeout=15 -O "$out" "$url" 2>>"$LOG_FILE"
    else
        busybox wget -q -O "$out" "$url" 2>>"$LOG_FILE"
    fi
}

# ── вытащить дату из hosts ───────────────────────
# Формат строки: # Последнее обновление: 30 мая 2026
_get_date() {
    local file="$1"
    grep "обновление:" "$file" 2>/dev/null | head -1 | sed 's/.*обновление:[[:space:]]*//'
}

# Конвертируем "30 мая 2026" -> 20260530 для числового сравнения
_date_to_num() {
    local str="$1"
    local day mon year num_mon

    day=$(echo "$str" | awk '{print $1}')
    mon=$(echo "$str" | awk '{print $2}')
    year=$(echo "$str" | awk '{print $3}')

    case "$mon" in
        января|январь)     num_mon="01" ;;
        февраля|февраль)   num_mon="02" ;;
        марта|март)        num_mon="03" ;;
        апреля|апрель)     num_mon="04" ;;
        мая|май)           num_mon="05" ;;
        июня|июнь)         num_mon="06" ;;
        июля|июль)         num_mon="07" ;;
        августа|август)    num_mon="08" ;;
        сентября|сентябрь) num_mon="09" ;;
        октября|октябрь)   num_mon="10" ;;
        ноября|ноябрь)     num_mon="11" ;;
        декабря|декабрь)   num_mon="12" ;;
        *)                 num_mon="00" ;;
    esac

    [ ${#day} -eq 1 ] && day="0$day"
    echo "${year}${num_mon}${day}"
}

# ── apply hosts без ребута ───────────────────────
# Ключевая идея:
#   - action.sh может запускаться в дочернем mount namespace (особенно из WebUI)
#   - Поэтому сначала пробуем nsenter в namespace PID 1 (init), где /system монтируется
#   - Только если nsenter нет — падаем на прямой mount в текущем namespace
_apply_hosts() {
    local src="$1"

    # Метод 1: nsenter в namespace init (PID 1) — работает на KSU/APatch/Magisk
    # Снимаем старый bind и ставим новый, всё в namespace PID 1
    if nsenter -t 1 -m -- sh -c "
        umount '$HOSTS_SYSTEM' 2>/dev/null
        umount '$HOSTS_SYSTEM' 2>/dev/null
        mount --bind '$src' '$HOSTS_SYSTEM'
    " 2>/dev/null; then
        _log "OK: nsenter mount --bind в namespace PID 1"
        return 0
    fi

    # Метод 2: прямой bind mount в текущем namespace
    umount "$HOSTS_SYSTEM" 2>/dev/null
    umount "$HOSTS_SYSTEM" 2>/dev/null
    if mount --bind "$src" "$HOSTS_SYSTEM" 2>/dev/null; then
        _log "OK: mount --bind в текущем namespace"
        return 0
    fi

    # Метод 3: remount rw + cp (крайний случай, только если /system не overlayfs)
    mount -o remount,rw /system 2>/dev/null
    if cp -f "$src" "$HOSTS_SYSTEM" 2>/dev/null; then
        mount -o remount,ro /system 2>/dev/null
        _log "OK: прямая запись (fallback cp)"
        return 0
    fi
    mount -o remount,ro /system 2>/dev/null

    _err "Все методы mount не сработали"
    return 1
}

# ── собрать итоговый hosts ───────────────────────
_build_hosts() {
    local raw="$1" out="$2"

    {
        echo "127.0.0.1 localhost"
        echo "::1 localhost"
        echo ""
    } > "$out"

    grep -v "^127\.0\.0\.1[[:space:]]*localhost" "$raw" \
        | grep -v "^::1[[:space:]]*localhost" \
        | grep -v "^[[:space:]]*$" \
        >> "$out"

    if [ -f "$BLACKLIST" ] && [ -s "$BLACKLIST" ]; then
        local count=0
        while IFS= read -r domain; do
            [ -z "$domain" ] && continue
            case "$domain" in '#'*) continue ;; esac
            grep -v "[[:space:]]${domain}$" "$out" > "${out}.bl"
            mv "${out}.bl" "$out"
            count=$((count + 1))
        done < "$BLACKLIST"
        _log "Blacklist: исключено $count доменов"
    fi

    if [ -f "$BLOCKLIST" ] && [ -s "$BLOCKLIST" ]; then
        echo "" >> "$out"
        echo "# === BlockSRV ===" >> "$out"
        local count=0
        while IFS= read -r domain; do
            [ -z "$domain" ] && continue
            case "$domain" in '#'*) continue ;; esac
            echo "0.0.0.0 $domain" >> "$out"
            case "$domain" in www.*) ;; *) echo "0.0.0.0 www.$domain" >> "$out" ;; esac
            count=$((count + 1))
        done < "$BLOCKLIST"
        _log "BlockSRV: $count доменов"
    fi

    chmod 644 "$out"
}

# ── MAIN ─────────────────────────────────────────
main() {
    _log "=== action.sh запущен ==="

    _print "⬇ Скачиваю hosts для проверки версии..."

    if ! _download "$HOSTS_URL" "$HOSTS_TMP"; then
        _err "Не удалось подключиться"
        if [ -f "$HOSTS_RAW" ]; then
            _print "⚠ Нет сети. Применяю текущий hosts..."
            _build_hosts "$HOSTS_RAW" "$HOSTS_DATA"
            _apply_hosts "$HOSTS_DATA" \
                && _print "✅ Hosts применён (без обновления)" \
                || _print "⚠ Ошибка применения"
        else
            _err "Нет сети и нет кэша"
        fi
        exit 1
    fi

    if [ ! -s "$HOSTS_TMP" ]; then
        _err "Скачанный файл пустой"
        rm -f "$HOSTS_TMP"
        exit 1
    fi

    local remote_date_str remote_date_num current_date_str current_date_num
    remote_date_str="$(_get_date "$HOSTS_TMP")"
    remote_date_num="$(_date_to_num "$remote_date_str")"

    _print "📅 Дата на сервере: $remote_date_str"

    if [ -f "$HOSTS_RAW" ]; then
        current_date_str="$(_get_date "$HOSTS_RAW")"
        current_date_num="$(_date_to_num "$current_date_str")"
        _print "📅 Текущая дата:    $current_date_str"
    else
        current_date_num="0"
        _print "📅 Текущая дата:    нет кэша"
    fi

    if [ "$remote_date_num" -gt "$current_date_num" ] 2>/dev/null; then
        _print "🆕 Найдено обновление! Применяю..."
        mv "$HOSTS_TMP" "$HOSTS_RAW"
    else
        _print "✅ Версия актуальна ($remote_date_str)"
        rm -f "$HOSTS_TMP"
    fi

    _print "🔧 Применяю фильтры..."
    _build_hosts "$HOSTS_RAW" "$HOSTS_DATA"

    cp -f "$HOSTS_DATA" "$MODULE_DIR/system/etc/hosts" 2>/dev/null

    _print "🔗 Монтирую без перезагрузки..."
    if _apply_hosts "$HOSTS_DATA"; then
        local lines
        lines=$(grep -c "" "$HOSTS_DATA" 2>/dev/null || echo "?")
        _print "✅ Готово! Hosts применён без перезагрузки ($lines строк)"
    else
        _print "⚠ Hosts обновлён в файле, но применение не удалось. Перезагрузите."
    fi

    _log "=== action.sh завершён ==="
}

main
