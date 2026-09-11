#!/system/bin/sh
# post-fs-data.sh — запускается РАНО, до того как система читает /system/etc/hosts
# Именно здесь делаем bind mount — это единственный надёжный способ
# работающий на Magisk, KSU и APatch без READ ONLY

MODULE_DIR="/data/adb/modules/unlocker_zrpb"
DATA_DIR="/data/adb/unlocker_zrpb"
HOSTS_DATA="$DATA_DIR/hosts"
HOSTS_SYSTEM="/system/etc/hosts"
LOG_FILE="$DATA_DIR/logs.txt"

mkdir -p "$DATA_DIR"

_log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [post-fs-data] $1" >> "$LOG_FILE"
}

_log "=== Запуск post-fs-data.sh ==="

# Если пользовательский hosts ещё не создан — копируем из модуля как основу
if [ ! -f "$HOSTS_DATA" ]; then
    if [ -f "$MODULE_DIR/system/etc/hosts" ]; then
        cp "$MODULE_DIR/system/etc/hosts" "$HOSTS_DATA"
        chmod 644 "$HOSTS_DATA"
        _log "Hosts скопирован из модуля в $DATA_DIR"
    else
        _log "WARN: hosts в модуле не найден, bind mount не выполнен"
        exit 0
    fi
fi

# Применяем наш hosts через bind mount
# Это работает на любом root менеджере, /system при этом остаётся read-only —
# мы просто «перекрываем» один файл поверх него
if mount --bind "$HOSTS_DATA" "$HOSTS_SYSTEM"; then
    _log "OK: mount --bind $HOSTS_DATA -> $HOSTS_SYSTEM"
else
    _log "ERROR: mount --bind не удался, пробуем nsenter"
    # Fallback для некоторых конфигураций KSU
    nsenter -t 1 -m -- mount --bind "$HOSTS_DATA" "$HOSTS_SYSTEM" 2>/dev/null \
        && _log "OK: nsenter mount --bind успешен" \
        || _log "ERROR: оба метода bind mount не сработали"
fi

_log "=== post-fs-data.sh завершён ==="
