#!/system/bin/sh
# blocksrv.sh — применяет blocklist.conf в hosts без ребута
# Вызывается из action.sh и напрямую из WebUI через ksu.exec

DATA_DIR="/data/adb/unlocker_zrpb"
MODULE_DIR="/data/adb/modules/unlocker_zrpb"
HOSTS_DATA="$DATA_DIR/hosts"
HOSTS_RAW="$DATA_DIR/hosts.raw"
BLACKLIST="$DATA_DIR/blacklist.conf"
BLOCKLIST="$DATA_DIR/blocklist.conf"
HOSTS_SYSTEM="/system/etc/hosts"
LOG_FILE="$DATA_DIR/logs.txt"

_log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] [blocksrv] $1" >> "$LOG_FILE"; }

_apply_hosts() {
    # Сначала nsenter в namespace PID 1 (init) — правильный порядок
    if nsenter -t 1 -m -- sh -c "
        umount '$HOSTS_SYSTEM' 2>/dev/null
        umount '$HOSTS_SYSTEM' 2>/dev/null
        mount --bind '$HOSTS_DATA' '$HOSTS_SYSTEM'
    " 2>/dev/null; then
        _log "OK: nsenter mount --bind в namespace PID 1"
        return 0
    fi
    # Fallback: прямой mount в текущем namespace
    umount "$HOSTS_SYSTEM" 2>/dev/null
    umount "$HOSTS_SYSTEM" 2>/dev/null
    if mount --bind "$HOSTS_DATA" "$HOSTS_SYSTEM" 2>/dev/null; then
        _log "OK: mount --bind в текущем namespace"
        return 0
    fi
    _log "ERROR: mount --bind не удался"
    return 1
}

_log "=== blocksrv.sh запущен ==="

# Берём raw hosts как основу
if [ ! -f "$HOSTS_RAW" ]; then
    _log "ERROR: hosts.raw не найден, запусти Action сначала"
    echo "ERROR: сначала запусти Action для загрузки hosts"
    exit 1
fi

# Пересобираем hosts с нуля
{
    echo "127.0.0.1 localhost"
    echo "::1 localhost"
    echo ""
} > "$HOSTS_DATA"

grep -v "^127\.0\.0\.1[[:space:]]*localhost" "$HOSTS_RAW" \
    | grep -v "^::1[[:space:]]*localhost" \
    | grep -v "^[[:space:]]*$" \
    >> "$HOSTS_DATA"

# Blacklist
if [ -f "$BLACKLIST" ] && [ -s "$BLACKLIST" ]; then
    while IFS= read -r domain; do
        domain=$(printf '%s' "$domain" | tr -d '\r')
        [ -z "$domain" ] && continue
        case "$domain" in '#'*) continue ;; esac
        grep -v "[[:space:]]${domain}$" "$HOSTS_DATA" > "$HOSTS_DATA.bl"
        mv "$HOSTS_DATA.bl" "$HOSTS_DATA"
    done < "$BLACKLIST"
fi

# BlockSRV
if [ -f "$BLOCKLIST" ] && [ -s "$BLOCKLIST" ]; then
    echo "" >> "$HOSTS_DATA"
    echo "# === BlockSRV ===" >> "$HOSTS_DATA"
    count=0
    while IFS= read -r domain; do
        domain=$(printf '%s' "$domain" | tr -d '\r')
        [ -z "$domain" ] && continue
        case "$domain" in '#'*) continue ;; esac
        echo "0.0.0.0 $domain" >> "$HOSTS_DATA"
        case "$domain" in www.*) ;; *) echo "0.0.0.0 www.$domain" >> "$HOSTS_DATA" ;; esac
        count=$((count + 1))
        _log "Заблокирован: $domain"
    done < "$BLOCKLIST"
    _log "BlockSRV: $count доменов добавлено"
    echo "BlockSRV: $count доменов"
else
    _log "blocklist.conf пустой или не найден"
    echo "blocklist.conf пустой"
fi

chmod 644 "$HOSTS_DATA"
cp -f "$HOSTS_DATA" "$MODULE_DIR/system/etc/hosts" 2>/dev/null

if _apply_hosts; then
    _log "OK: hosts применён"
    echo "OK"
else
    _log "ERROR: hosts не применён"
    echo "ERROR: mount не удался"
    exit 1
fi

_log "=== blocksrv.sh завершён ==="
