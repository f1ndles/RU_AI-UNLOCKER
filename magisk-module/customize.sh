#!/system/bin/sh
# customize.sh — запускается при установке модуля

DATA_DIR="/data/adb/unlocker_zrpb"
MODULE_HOSTS="$MODPATH/system/etc/hosts"

ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print "   Разблокировщик сервисов РФ"
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print ""
ui_print "⚙ Настройка..."

mkdir -p "$DATA_DIR"
mkdir -p "$MODPATH/system/etc"

if [ -f "$MODULE_HOSTS" ] && [ -s "$MODULE_HOSTS" ]; then
    cp -f "$MODULE_HOSTS" "$DATA_DIR/hosts.raw"
    cp -f "$MODULE_HOSTS" "$DATA_DIR/hosts"
    chmod 644 "$DATA_DIR/hosts"
    ui_print "✅ Hosts сохранён"
else
    echo "127.0.0.1 localhost" > "$DATA_DIR/hosts"
    echo "::1 localhost" >> "$DATA_DIR/hosts"
    cp "$DATA_DIR/hosts" "$DATA_DIR/hosts.raw"
    cp "$DATA_DIR/hosts" "$MODULE_HOSTS"
    ui_print "⚠ Hosts не найден. Нажмите Action после установки."
fi

[ -f "$DATA_DIR/blacklist.conf" ] || touch "$DATA_DIR/blacklist.conf"
[ -f "$DATA_DIR/blocklist.conf" ] || touch "$DATA_DIR/blocklist.conf"

chmod 755 "$MODPATH/action.sh"
chmod 755 "$MODPATH/post-fs-data.sh"
chmod 644 "$DATA_DIR/hosts"

ui_print ""
ui_print "✅ Установка завершена!"
ui_print "• Нажмите Action для обновления hosts"
ui_print "• WebUI доступен в KernelSU / APatch"
ui_print "• Перезагрузка НЕ требуется"
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
