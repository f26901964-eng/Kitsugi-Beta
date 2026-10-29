#!/system/bin/sh
# kbd2touch DAEMON'larini oldurur; betiklerin/kabuklarin KENDINI oldurmez.
# (pkill -f kbd2touch kullanilmaz: bu betigin kendi cmdline'i da eslesir
#  ve script daha daemon'u baslatmadan intihar eder!)
kill_daemon() {
    for p in /proc/[0-9]*; do
        pid=${p#/proc/}
        [ "$pid" = "$$" ] && continue
        [ "$pid" = "$PPID" ] && continue
        cmd=$(tr '\0' ' ' < "$p/cmdline" 2>/dev/null) || continue
        case "$cmd" in
            *kbd2touch*)
                case "$cmd" in
                    *.sh*|*--check*|*--calibrate*|*--list-devices*|*--map*|*--test*) : ;;
                    *) kill "$pid" 2>/dev/null ;;
                esac ;;
        esac
    done
    return 0
}

kill_daemon
rm -rf /data/adb/kbd2touch
