#!/system/bin/sh
# Preserve the user's configuration/library when the module manager stages an update.
PREVIOUS=/data/adb/modules/zygisk-myinjector
if [ "$MODPATH" != "$PREVIOUS" ] && [ -d "$PREVIOUS" ]; then
    if [ -f "$PREVIOUS/config.json" ]; then
        ui_print "- Preserving MyInjector configuration"
        cp -af "$PREVIOUS/config.json" "$MODPATH/config.json" || abort "Cannot preserve configuration"
    fi
    if [ -d "$PREVIOUS/so_files" ]; then
        ui_print "- Preserving SO library"
        cp -af "$PREVIOUS/so_files" "$MODPATH/" || abort "Cannot preserve SO library"
    fi
fi
set_perm_recursive "$MODPATH" 0 0 0755 0644
set_perm "$MODPATH/service.sh" 0 0 0755
if [ -d "$MODPATH/so_files" ]; then
    set_perm_recursive "$MODPATH/so_files" 0 0 0755 0755
fi
ui_print "- MyInjector: open the SO library after reboot"
