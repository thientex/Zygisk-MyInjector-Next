#!/system/bin/sh
MODDIR=${0%/*}
export PATH=/system/bin:/system/xbin:$PATH
LOG=/data/local/tmp/myinjector_install.log
log() { echo "[MyInjector] $(date '+%Y-%m-%d %H:%M:%S') $*" >> "$LOG"; /system/bin/log -t MyInjector -p i "$*"; }

# Bound startup waits; a failed package manager must not leave an endless worker.
ready=0
for attempt in $(seq 1 120); do
    if [ "$(getprop sys.boot_completed)" = "1" ] && pm path android >/dev/null 2>&1; then
        ready=1
        break
    fi
    sleep 2
done
[ "$ready" = 1 ] || { log "System startup timed out"; exit 1; }
APK_PATH="$MODDIR/configapp.apk"
[ -f "$APK_PATH" ] || { log "Config APK missing"; exit 1; }
APK_HASH=$(sha256sum "$APK_PATH" | cut -d ' ' -f 1)
PREVIOUS_HASH=$(cat "$MODDIR/.installed-apk" 2>/dev/null)
if [ "$APK_HASH" != "$PREVIOUS_HASH" ] || ! pm path com.jiqiu.configapp >/dev/null 2>&1; then
    if pm install -r "$APK_PATH" >> "$LOG" 2>&1; then
        echo "$APK_HASH" > "$MODDIR/.installed-apk"
        log "Config APK installed"
    else
        log "APK installation failed; check signing certificate and install manually"
        exit 1
    fi
fi
chmod 755 "$MODDIR"
log "Module ready"
