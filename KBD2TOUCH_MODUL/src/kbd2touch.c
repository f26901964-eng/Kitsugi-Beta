/*
 * kbd2touch — fiziksel klavye -> gercek dokunus / sanal gamepad donusturucu
 * Xiaomi Pad 6 (pipa) + SukiSU Ultra / KernelSU / Magisk icin yazildi.
 *
 * Nasil calisir:
 *   1) /dev/input/event* altindaki klavyeyi bulur (EVIOCGRAB ile tamamen ele gecirir)
 *   2) /dev/uinput ile sanal COKLU-DOKUNMATIK ekran (Protocol B, 10 parmak) olusturur
 *   3) Istege bagli olarak /dev/uinput ile sanal Xbox gamepad olusturur
 *   4) Config dosyasindaki eslesmelere gore tus -> dokunus/surukleme/gamepad cevrimi yapar
 *   5) On plandaki uygulamayi izleyip (dumpsys window) profil degistirir
 *
 * INJECT_EVENTS izni GEREKMEZ (kernel uinput), bu yuzden MIUI'nin
 * "USB hata ayiklama (Guvenlik ayarlari)" kisitlamasina takilmaz.
 *
 * Derleme (Android arm64, statik musl):
 *   zig cc -target aarch64-linux-musl -O2 -static -o kbd2touch kbd2touch.c
 * Test derlemesi (Linux x86_64):
 *   cc -O2 -Wall -o kbd2touch kbd2touch.c
 *
 * Lisans: MIT — istedigin gibi kullan, degistir, dagit.
 */

#define _GNU_SOURCE
#include <ctype.h>
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <math.h>
#include <poll.h>
#include <signal.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <sys/time.h>
#include <time.h>
#include <unistd.h>

#include <linux/input.h>
#include <linux/uinput.h>

#define VERSION        "1.0.4"
#define MAX_BINDS      512
#define MAX_NAME       96
#define MAX_LINE       1024
#define MAX_SLOTS      10
#define PROFILE_SLOTS  32

#ifndef UI_DEV_SETUP
struct uinput_setup { struct input_id id; char name[UINPUT_MAX_NAME_SIZE]; uint32_t ff_effects_max; };
#define UI_DEV_SETUP  _IOW(UINPUT_IOCTL_BASE, 3, struct uinput_setup)
#define UI_ABS_SETUP  _IOW(UINPUT_IOCTL_BASE, 2, struct uinput_abs_setup)
#endif

/* ------------------------------------------------------------------ */
/* log                                                                 */
/* ------------------------------------------------------------------ */
static char g_home[160] = "/data/adb/kbd2touch";
static char g_logpath[256] = "/data/adb/kbd2touch/log.txt";
static char g_statuspath[256] = "/data/adb/kbd2touch/status.txt";
static FILE *g_log = NULL;
static int g_verbose = 0;

static void logmsg(const char *fmt, ...) {
    char buf[1024];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(buf, sizeof buf, fmt, ap);
    va_end(ap);

    time_t t = time(NULL);
    struct tm tm;
    localtime_r(&t, &tm);
    char ts[32];
    strftime(ts, sizeof ts, "%H:%M:%S", &tm);

    if (g_log) { fprintf(g_log, "[%s] %s\n", ts, buf); fflush(g_log); }
    fprintf(stderr, "[%s] %s\n", ts, buf);
}
static void logv(const char *fmt, ...) {
    if (!g_verbose) return;
    char buf[1024];
    va_list ap; va_start(ap, fmt); vsnprintf(buf, sizeof buf, fmt, ap); va_end(ap);
    logmsg("%s", buf);
}

/* ------------------------------------------------------------------ */
/* key isimleri                                                        */
/* ------------------------------------------------------------------ */
static const struct { const char *name; unsigned short code; } kExtraKeys[] = {
    {"SPACE", KEY_SPACE}, {"ENTER", KEY_ENTER}, {"KPENTER", KEY_KPENTER},
    {"BACKSPACE", KEY_BACKSPACE}, {"HOME_ANDROID", KEY_HOME},
    {"TAB", KEY_TAB}, {"ESC", KEY_ESC}, {"ESCAPE", KEY_ESC},
    {"BACK", KEY_BACK}, {"ANDROID_BACK", KEY_BACK},
    {"CAPSLOCK", KEY_CAPSLOCK},
    {"SHIFT", KEY_LEFTSHIFT}, {"CTRL", KEY_LEFTCTRL}, {"CONTROL", KEY_LEFTCTRL},
    {"ALT", KEY_LEFTALT}, {"META", KEY_LEFTMETA}, {"WIN", KEY_LEFTMETA},
    {"LSHIFT", KEY_LEFTSHIFT},
    {"RSHIFT", KEY_RIGHTSHIFT}, {"LCTRL", KEY_LEFTCTRL}, {"RCTRL", KEY_RIGHTCTRL},
    {"LALT", KEY_LEFTALT}, {"RALT", KEY_RIGHTALT}, {"LMETA", KEY_LEFTMETA},
    {"RMETA", KEY_RIGHTMETA}, {"HOME", KEY_HOME}, {"END", KEY_END},
    {"PAGEUP", KEY_PAGEUP}, {"PAGEDOWN", KEY_PAGEDOWN}, {"INSERT", KEY_INSERT},
    {"DELETE", KEY_DELETE}, {"UP", KEY_UP}, {"DOWN", KEY_DOWN},
    {"LEFT", KEY_LEFT}, {"RIGHT", KEY_RIGHT},
    {"F1", KEY_F1}, {"F2", KEY_F2}, {"F3", KEY_F3}, {"F4", KEY_F4},
    {"F5", KEY_F5}, {"F6", KEY_F6}, {"F7", KEY_F7}, {"F8", KEY_F8},
    {"F9", KEY_F9}, {"F10", KEY_F10}, {"F11", KEY_F11}, {"F12", KEY_F12},
    {"MINUS", KEY_MINUS}, {"EQUAL", KEY_EQUAL}, {"LEFTBRACE", KEY_LEFTBRACE},
    {"RIGHTBRACE", KEY_RIGHTBRACE}, {"SEMICOLON", KEY_SEMICOLON},
    {"APOSTROPHE", KEY_APOSTROPHE}, {"GRAVE", KEY_GRAVE}, {"BACKSLASH", KEY_BACKSLASH},
    {"COMMA", KEY_COMMA}, {"DOT", KEY_DOT}, {"SLASH", KEY_SLASH},
    {"102ND", KEY_102ND}, {"VOLUMEUP", KEY_VOLUMEUP}, {"VOLUMEDOWN", KEY_VOLUMEDOWN},
    {"POWER", KEY_POWER}, {"MENU", KEY_MENU},
    {NULL, 0}
};

/* Linux input-event-codes: harfler A-Z SIRALI DEGILDIR (KEY_A=30, KEY_W=17...).
 * Bu yuzden acik tablo kullaniyoruz. */
static const struct { char c; unsigned short code; } kLetters[26] = {
    {'A', KEY_A}, {'B', KEY_B}, {'C', KEY_C}, {'D', KEY_D}, {'E', KEY_E},
    {'F', KEY_F}, {'G', KEY_G}, {'H', KEY_H}, {'I', KEY_I}, {'J', KEY_J},
    {'K', KEY_K}, {'L', KEY_L}, {'M', KEY_M}, {'N', KEY_N}, {'O', KEY_O},
    {'P', KEY_P}, {'Q', KEY_Q}, {'R', KEY_R}, {'S', KEY_S}, {'T', KEY_T},
    {'U', KEY_U}, {'V', KEY_V}, {'W', KEY_W}, {'X', KEY_X}, {'Y', KEY_Y},
    {'Z', KEY_Z}
};

/* "SPACE", "F9", "A", "1", "KEY_UP" -> Linux input keycode */
#define KEY_WHEEL_UP   0x3001   /* sahte kod: fare tekerlegi yukari */
#define KEY_WHEEL_DOWN 0x3002   /* sahte kod: fare tekerlegi asagi */

static int parse_keyname(const char *s) {
    if (!s || !*s) return -1;
    char up[64]; size_t i = 0;
    for (; s[i] && i < sizeof up - 1; i++) up[i] = (char)toupper((unsigned char)s[i]);
    up[i] = 0;

    const char *p = up;
    if (strncmp(p, "KEY_", 4) == 0) p += 4;

    for (int k = 0; kExtraKeys[k].name; k++)
        if (strcmp(kExtraKeys[k].name, p) == 0) return kExtraKeys[k].code;

    /* tek karakter: harf veya rakam */
    if (p[0] && !p[1]) {
        if (p[0] >= 'A' && p[0] <= 'Z') return kLetters[p[0] - 'A'].code;
        if (p[0] >= '0' && p[0] <= '9') {
            if (p[0] == '0') return KEY_0;
            return KEY_1 + (p[0] - '1');
        }
    }
    if (strncmp(p, "BTN_", 4) == 0 || !strcmp(p, "SOL_TIK") || !strcmp(p, "SAG_TIK") ||
        !strcmp(p, "ORTA_TIK") || !strcmp(p, "TEKERLEK_UP") || !strcmp(p, "TEKERLEK_DOWN") ||
        !strcmp(p, "WHEEL_UP") || !strcmp(p, "WHEEL_DOWN")) {
        /* BTN_MOUSE / BTN_LEFT / BTN_RIGHT / BTN_MIDDLE + Turkce takma adlar */
        if (!strcmp(p, "BTN_LEFT") || !strcmp(p, "BTN_MOUSE") || !strcmp(p, "SOL_TIK")) return BTN_LEFT;
        if (!strcmp(p, "BTN_RIGHT") || !strcmp(p, "SAG_TIK")) return BTN_RIGHT;
        if (!strcmp(p, "BTN_MIDDLE") || !strcmp(p, "ORTA_TIK")) return BTN_MIDDLE;
        if (!strcmp(p, "TEKERLEK_UP") || !strcmp(p, "WHEEL_UP")) return KEY_WHEEL_UP;
        if (!strcmp(p, "TEKERLEK_DOWN") || !strcmp(p, "WHEEL_DOWN")) return KEY_WHEEL_DOWN;
    }
    long v = strtol(p, NULL, 0);
    if (v > 0 && v < KEY_MAX) return (int)v;
    return -1;
}

/* ------------------------------------------------------------------ */
/* veri yapilari                                                       */
/* ------------------------------------------------------------------ */
typedef enum { ACT_NONE = 0, ACT_TAP, ACT_SWIPE, ACT_KEY, ACT_GP_BUTTON, ACT_GP_STICK } act_t;

typedef struct {
    int   used;
    int   keycode;          /* tetikleyici tus (Linux input)          */
    char  app[64];          /* "" = genel profil, yoksa paket adi      */
    act_t action;

    /* touch */
    int   x, y, x2, y2;
    int   duration_ms;      /* tap basili kalma / swipe suresi         */
    int   hold;             /* 1 = tus birakana kadar basili tut       */
    int   repeat_ms;        /* >0 = bu aralikla tekrarla (tus basiliyken) */

    /* key eylemi (Android keycode degil, Linux keycode gonderilir)     */
    int   out_key;
    int   out_hold_ms;

    /* gamepad */
    int   gp_button;        /* BTN_* */
    int   gp_axis;          /* 0 = LX, 1 = LY, 2 = RX, 3 = RY */
    int   gp_x, gp_y;       /* -32767..32767 */

    /* calisma zamani durumu */
    int   pressed;          /* su an tetikleyici basili mi              */
    int   slot;             /* ayrilmis touch slot (-1 = yok)           */
    int   curx, cury;       /* su anki parmak konumu                    */
    int   swiping;
    struct timespec swipe_start;
    int   repeat_last_ms;
    char  label[48];
    char  src[64];            /* "" = config.ini (genel), yoksa profil dosya adi */
    int   manual;             /* 1 = yalnizca F10 ile elle secilince kullanilir */
} Bind;

static Bind g_binds[MAX_BINDS];
static int  g_nbinds = 0;

typedef struct {
    int   screen_w, screen_h;     /* mantiksal ekran (oyunun gordugu) */
    int   touch_max_x, touch_max_y;
    int   rotation;               /* 0/90/180/270 */
    int   swap_xy, invert_x, invert_y;
    int   grab_keyboard;      /* 0 = hic ele gecirme (eski anahtar) */
    int   grab_mouse;         /* 1 = eslenen oyun acikken fareyi de yakala */
    char  mouse_device[128];  /* elle fare dugumu (bos = otomatik) */
    int   grab_mode;          /* 0=asla 1=sadece eslenen uygulama acikken 2=her zaman */
    int   tap_ms;
    int   swipe_ms;
    int   gamepad_enable;
    int   virtual_keyboard;   /* 0 ise sanal klavye cihazi olusturulmaz (yazilim klavyesi kaybolmasin diye) */
    char  gamepad_name[64];
    int   toggle_key, profile_key, calib_key, quit_key;
    int   auto_profile;
    int   auto_profile_ms;
    int   enable_on_boot;
    int   slot_count;
    char  device_path[128];
    char  device_match[64];
    int   jitter;                 /* dokunuşa eklenecek rastgele piksel */
} Cfg;

static Cfg g_cfg = {
    .screen_w = 2880, .screen_h = 1800,
    .touch_max_x = 2879, .touch_max_y = 1799,
    .rotation = 0, .swap_xy = 0, .invert_x = 0, .invert_y = 0,
    .grab_keyboard = 1, .grab_mode = 1, .grab_mouse = 1, .tap_ms = 45, .swipe_ms = 90,
    .gamepad_enable = 0, .virtual_keyboard = 1,
    .gamepad_name = "Microsoft X-Box 360 pad",
    .toggle_key = KEY_F9, .profile_key = KEY_F10,
    .calib_key = KEY_F11, .quit_key = -1,
    .auto_profile = 1, .auto_profile_ms = 1500,
    .enable_on_boot = 1, .slot_count = MAX_SLOTS, .jitter = 0,
};

static const char *g_cfgpath = "/data/adb/kbd2touch/config.ini";
static char g_profiledir[256] = "/data/adb/kbd2touch/profiles";
static char g_profile_files[PROFILE_SLOTS][384];
static int  g_profile_count = 0, g_profile_idx = 0;
static char g_current_app[128] = "";
static char g_active_profile[64] = "";   /* F10 ile elle secilen profil (bos = otomatik) */
static int  g_enabled = 1;

/* ------------------------------------------------------------------ */
/* uinput: sanal dokunmatik                                            */
/* ------------------------------------------------------------------ */
static int g_touch_fd = -1;
static int g_pad_fd = -1;
static int g_pad_auto = 0;         /* pad, ensure_pad_for_context ile mi olustu */
static int g_pad_auto_failed = 0;  /* otomatik olusturma denemesi basarisiz mi */
static int g_key_fd = -1;
static int g_kbd_fd = -1;      /* fiziksel klavyenin evdev dugumu */
static int g_mouse_fd = -1;      /* fiziksel farenin evdev dugumu (BT/USB) */
static char g_mouse_path[160] = "";
static int g_grabbed = 0;      /* klavye su an exclusive mi */      /* sanal klavye: KEY_BACK / KEY_HOME vb. gondermek icin */

static void ev(int fd, int type, int code, int val) {
    struct input_event e;
    memset(&e, 0, sizeof e);
    gettimeofday(&e.time, NULL);
    e.type = (__u16)type; e.code = (__u16)code; e.value = (__s32)val;
    if (write(fd, &e, sizeof e) < 0 && errno != ENODEV)
        logmsg("write uinput hata: %s", strerror(errno));
}
static void syn(int fd) { ev(fd, EV_SYN, SYN_REPORT, 0); }

static void abs_setup(int fd, int code, int max, int fuzz) {
    struct uinput_abs_setup s;
    memset(&s, 0, sizeof s);
    s.code = (__u16)code;
    s.absinfo.maximum = max;
    s.absinfo.fuzz = fuzz;
    s.absinfo.flat = 0;
    s.absinfo.resolution = 0;
    if (ioctl(fd, UI_ABS_SETUP, &s) < 0)
        logmsg("UI_ABS_SETUP(%d) basarisiz: %s", code, strerror(errno));
}

static int touch_create(void) {
    int fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
    if (fd < 0) fd = open("/dev/input/uinput", O_WRONLY | O_NONBLOCK);
    if (fd < 0) {
        if (errno == ENOENT)
            logmsg("HATA: /dev/uinput YOK -> kernel'de CONFIG_INPUT_UINPUT kapali olabilir. "
                   "Kontrol: su -c \"zcat /proc/config.gz | grep UINPUT\" veya ls -l /dev/uinput");
        else
            logmsg("HATA: /dev/uinput acilamadi: %s (root/SELinux izin problemi)", strerror(errno));
        return -1;
    }

    ioctl(fd, UI_SET_EVBIT, EV_KEY);
    ioctl(fd, UI_SET_KEYBIT, BTN_TOUCH);
    ioctl(fd, UI_SET_KEYBIT, BTN_TOOL_FINGER);
    ioctl(fd, UI_SET_EVBIT, EV_ABS);

    int mx = g_cfg.touch_max_x, my = g_cfg.touch_max_y;
    abs_setup(fd, ABS_X, mx, 0);
    abs_setup(fd, ABS_Y, my, 0);
    abs_setup(fd, ABS_MT_SLOT, MAX_SLOTS - 1, 0);
    abs_setup(fd, ABS_MT_TOUCH_MAJOR, 255, 0);
    abs_setup(fd, ABS_MT_TOUCH_MINOR, 255, 0);
    abs_setup(fd, ABS_MT_WIDTH_MAJOR, 255, 0);
    abs_setup(fd, ABS_MT_POSITION_X, mx, 0);
    abs_setup(fd, ABS_MT_POSITION_Y, my, 0);
    abs_setup(fd, ABS_MT_TRACKING_ID, 65535, 0);
    ioctl(fd, UI_SET_PROPBIT, INPUT_PROP_DIRECT);

    struct uinput_setup us;
    memset(&us, 0, sizeof us);
    snprintf(us.name, sizeof us.name, "kbd2touch touchscreen");
    us.id.bustype = BUS_VIRTUAL;
    us.id.vendor  = 0x1234;
    us.id.product = 0x5678;
    us.id.version = 1;
    us.ff_effects_max = 0;
    if (ioctl(fd, UI_DEV_SETUP, &us) < 0) { logmsg("UI_DEV_SETUP hata: %s", strerror(errno)); close(fd); return -1; }
    if (ioctl(fd, UI_DEV_CREATE) < 0) { logmsg("UI_DEV_CREATE hata: %s", strerror(errno)); close(fd); return -1; }

    g_touch_fd = fd;
    logmsg("sanal dokunmatik olusturuldu (%dx%d, %d slot)", mx + 1, my + 1, g_cfg.slot_count);
    return 0;
}

/* Sanal klavye cihazi: Android'e gercek bir "hardware keyboard" gibi gorunur,
 * boylece KEY_BACK / KEY_HOME / KEY_VOLUME* gibi sistem tuslarini gonderebiliriz. */
static int keydev_create(void) {
    int fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
    if (fd < 0) fd = open("/dev/input/uinput", O_WRONLY | O_NONBLOCK);
    if (fd < 0) return -1;

    ioctl(fd, UI_SET_EVBIT, EV_KEY);
    /* 1..255 arasi tum standart tuslar (harf, rakam, oklar, F tuslari,
     * KEY_BACK=158, KEY_HOME=172, KEY_MENU=139, ses tuslari vb.) */
    for (int k = 1; k <= 255; k++) ioctl(fd, UI_SET_KEYBIT, k);
    ioctl(fd, UI_SET_KEYBIT, BTN_LEFT);
    ioctl(fd, UI_SET_KEYBIT, BTN_RIGHT);
    ioctl(fd, UI_SET_KEYBIT, BTN_MIDDLE);

    struct uinput_setup us;
    memset(&us, 0, sizeof us);
    snprintf(us.name, sizeof us.name, "kbd2touch keys");
    us.id.bustype = BUS_VIRTUAL;
    us.id.vendor  = 0x1234;
    us.id.product = 0x5679;
    us.id.version = 1;
    if (ioctl(fd, UI_DEV_SETUP, &us) < 0 || ioctl(fd, UI_DEV_CREATE) < 0) {
        logmsg("sanal klavye olusturulamadi: %s", strerror(errno));
        close(fd);
        return -1;
    }
    g_key_fd = fd;
    logmsg("sanal klavye cihazi olusturuldu (KEY_BACK/HOME/MENU/SES gonderilebilir)");
    usleep(200000);
    return 0;
}

static int pad_create(void) {
    if (g_pad_fd >= 0) return 0;   /* zaten var */
    int fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
    if (fd < 0) fd = open("/dev/input/uinput", O_WRONLY | O_NONBLOCK);
    if (fd < 0) return -1;

    ioctl(fd, UI_SET_EVBIT, EV_KEY);
    int buttons[] = { BTN_SOUTH, BTN_EAST, BTN_NORTH, BTN_WEST, BTN_TL, BTN_TR,
                      BTN_SELECT, BTN_START, BTN_MODE, BTN_THUMBL, BTN_THUMBR };
    /* LT / RT tetik eksenleri */
    ioctl(fd, UI_SET_ABSBIT, ABS_Z);
    ioctl(fd, UI_SET_ABSBIT, ABS_RZ);
    for (unsigned i = 0; i < sizeof buttons / sizeof buttons[0]; i++)
        ioctl(fd, UI_SET_KEYBIT, buttons[i]);

    ioctl(fd, UI_SET_EVBIT, EV_ABS);
    abs_setup(fd, ABS_X, 32767, 16);
    abs_setup(fd, ABS_Y, 32767, 16);
    abs_setup(fd, ABS_RX, 32767, 16);
    abs_setup(fd, ABS_RY, 32767, 16);
    abs_setup(fd, ABS_Z, 255, 0);
    abs_setup(fd, ABS_RZ, 255, 0);

    struct uinput_setup us;
    memset(&us, 0, sizeof us);
    snprintf(us.name, sizeof us.name, "%s", g_cfg.gamepad_name);
    us.id.bustype = BUS_USB;
    us.id.vendor  = 0x045e;   /* Microsoft  */
    us.id.product = 0x028e;   /* Xbox 360   */
    us.id.version = 0x0110;
    if (ioctl(fd, UI_DEV_SETUP, &us) < 0 || ioctl(fd, UI_DEV_CREATE) < 0) {
        logmsg("sanal gamepad olusturulamadi: %s", strerror(errno));
        close(fd);
        return -1;
    }
    g_pad_fd = fd;
    /* merkezle */
    ev(fd, EV_ABS, ABS_X, 0); ev(fd, EV_ABS, ABS_Y, 0);
    ev(fd, EV_ABS, ABS_RX, 0); ev(fd, EV_ABS, ABS_RY, 0);
    ev(fd, EV_ABS, ABS_Z, 0); ev(fd, EV_ABS, ABS_RZ, 0);
    syn(fd);
    logmsg("sanal gamepad olusturuldu: '%s' (Xbox 360 VID/PID)", g_cfg.gamepad_name);
    usleep(300000);
    return 0;
}

/* keycode -> okunabilir ad (log/test icin) */
static const char *keyname(int code) {
    static char b[8];
    for (int i = 0; i < 26; i++)
        if (kLetters[i].code == code) { b[0] = kLetters[i].c; b[1] = 0; return b; }
    if (code == KEY_0) return "0";
    if (code >= KEY_1 && code <= KEY_9) { b[0] = (char)('1' + (code - KEY_1)); b[1] = 0; return b; }
    for (int k = 0; kExtraKeys[k].name; k++)
        if (kExtraKeys[k].code == code) return kExtraKeys[k].name;
    if (code == BTN_LEFT) return "SOL_TIK";
    if (code == BTN_RIGHT) return "SAG_TIK";
    if (code == BTN_MIDDLE) return "ORTA_TIK";
    if (code == KEY_WHEEL_UP) return "TEKERLEK_UP";
    if (code == KEY_WHEEL_DOWN) return "TEKERLEK_DOWN";
    snprintf(b, sizeof b, "%d", code);
    return b;
}

static const char *actname(int a) {
    switch (a) {
        case ACT_TAP: return "tap";
        case ACT_SWIPE: return "swipe";
        case ACT_KEY: return "key";
        case ACT_GP_BUTTON: return "gp_button";
        case ACT_GP_STICK: return "gp_stick";
        default: return "?";
    }
}

/* ekran koordinati -> touch koordinati (donusum) */
static void map_point(int sx, int sy, int *tx, int *ty) {
    int W = g_cfg.screen_w > 0 ? g_cfg.screen_w : 2880;
    int H = g_cfg.screen_h > 0 ? g_cfg.screen_h : 1800;
    int MX = g_cfg.touch_max_x, MY = g_cfg.touch_max_y;
    if (sx < 0) sx = 0; if (sx > W) sx = W;
    if (sy < 0) sy = 0; if (sy > H) sy = H;
    int x = sx, y = sy;

    if (g_cfg.swap_xy) { int t = x; x = y; y = t; }

    switch (g_cfg.rotation) {
        case 90:  { int nx = H - y; int ny = x; x = nx; y = ny; break; }
        case 180: { x = W - x; y = H - y; break; }
        case 270: { int nx = y; int ny = W - x; x = nx; y = ny; break; }
        default: break;
    }
    if (g_cfg.invert_x) x = (g_cfg.rotation == 90 || g_cfg.rotation == 270 ? MY : MX) - x;
    if (g_cfg.invert_y) y = (g_cfg.rotation == 90 || g_cfg.rotation == 270 ? MX : MY) - y;

    int axmax = (g_cfg.rotation == 90 || g_cfg.rotation == 270 || g_cfg.swap_xy) ? MY : MX;
    int aymax = (g_cfg.rotation == 90 || g_cfg.rotation == 270 || g_cfg.swap_xy) ? MX : MY;
    if (x < 0) x = 0; if (x > axmax) x = axmax;
    if (y < 0) y = 0; if (y > aymax) y = aymax;

    if (g_cfg.jitter > 0) {
        x += (rand() % (2 * g_cfg.jitter + 1)) - g_cfg.jitter;
        y += (rand() % (2 * g_cfg.jitter + 1)) - g_cfg.jitter;
        if (x < 0) x = 0; if (x > axmax) x = axmax;
        if (y < 0) y = 0; if (y > aymax) y = aymax;
    }
    *tx = x; *ty = y;
}

/* Slot doluluk tablosu: touch_down ile 1, touch_up ile 0 olur.
 * Bind->slot tek basina yetmez cunku "sureli tap"lerde slot, tus
 * birakilmasini beklemeden pending kuyruguna devredilir. */
static int g_slot_busy[MAX_SLOTS];

static int slot_alloc(void) {
    int lim = g_cfg.slot_count;
    if (lim > MAX_SLOTS) lim = MAX_SLOTS;
    if (lim < 1) lim = 1;
    for (int i = 0; i < lim; i++)
        if (!g_slot_busy[i]) return i;
    return -1;
}

static void touch_down(int slot, int tx, int ty) {
    if (slot < 0 || slot >= MAX_SLOTS) return;
    g_slot_busy[slot] = 1;
    ev(g_touch_fd, EV_ABS, ABS_MT_SLOT, slot);
    ev(g_touch_fd, EV_ABS, ABS_MT_TRACKING_ID, slot + 1);
    ev(g_touch_fd, EV_ABS, ABS_MT_POSITION_X, tx);
    ev(g_touch_fd, EV_ABS, ABS_MT_POSITION_Y, ty);
    ev(g_touch_fd, EV_ABS, ABS_MT_TOUCH_MAJOR, 8);
    ev(g_touch_fd, EV_ABS, ABS_MT_TOUCH_MINOR, 8);
    ev(g_touch_fd, EV_KEY, BTN_TOUCH, 1);
    ev(g_touch_fd, EV_KEY, BTN_TOOL_FINGER, 1);
    ev(g_touch_fd, EV_ABS, ABS_X, tx);
    ev(g_touch_fd, EV_ABS, ABS_Y, ty);
    syn(g_touch_fd);
}
static void touch_move(int slot, int tx, int ty) {
    ev(g_touch_fd, EV_ABS, ABS_MT_SLOT, slot);
    ev(g_touch_fd, EV_ABS, ABS_MT_POSITION_X, tx);
    ev(g_touch_fd, EV_ABS, ABS_MT_POSITION_Y, ty);
    syn(g_touch_fd);
}
static void touch_up(int slot) {
    if (slot < 0 || slot >= MAX_SLOTS) return;
    g_slot_busy[slot] = 0;
    ev(g_touch_fd, EV_ABS, ABS_MT_SLOT, slot);
    ev(g_touch_fd, EV_ABS, ABS_MT_TRACKING_ID, -1);
    syn(g_touch_fd);
    int any = 0;
    for (int i = 0; i < MAX_SLOTS; i++) if (g_slot_busy[i]) { any = 1; break; }
    if (!any) {
        ev(g_touch_fd, EV_KEY, BTN_TOUCH, 0);
        ev(g_touch_fd, EV_KEY, BTN_TOOL_FINGER, 0);
        syn(g_touch_fd);
    }
}

/* ------------------------------------------------------------------ */
/* config okuma                                                        */
/* ------------------------------------------------------------------ */
/* Satir sonu yorumlarini (# veya ;) keser.
 * "toggle_key = F9   # ac/kapa" -> "F9"  (yorum kesilmezse tus adi cozulemez) */
static void strip_inline_comment(char *v) {
    for (char *p = v; *p; p++) {
        if ((*p == '#' || *p == ';') && (p == v || isspace((unsigned char)p[-1]))) {
            *p = 0;
            break;
        }
    }
    char *e = v + strlen(v);
    while (e > v && isspace((unsigned char)e[-1])) *--e = 0;
}

static char *trim(char *s) {
    while (*s && isspace((unsigned char)*s)) s++;
    char *e = s + strlen(s);
    while (e > s && isspace((unsigned char)e[-1])) *--e = 0;
    return s;
}

static void cfg_int(Cfg *c, const char *k, const char *v) {
    int n = (int)strtol(v, NULL, 0);
    #define EQ(x) (strcasecmp(k, x) == 0)
    if (EQ("screen_width")) c->screen_w = n;
    else if (EQ("screen_height")) c->screen_h = n;
    else if (EQ("touch_max_x")) c->touch_max_x = n;
    else if (EQ("touch_max_y")) c->touch_max_y = n;
    else if (EQ("rotation")) c->rotation = n;
    else if (EQ("swap_xy")) c->swap_xy = n;
    else if (EQ("invert_x")) c->invert_x = n;
    else if (EQ("invert_y")) c->invert_y = n;
    else if (EQ("grab_keyboard")) { c->grab_keyboard = n; if (!n) c->grab_mode = 0; }
    else if (EQ("grab_mode")) c->grab_mode = n;
    else if (EQ("grab_mouse")) c->grab_mouse = n;
    else if (EQ("mouse_device")) snprintf(c->mouse_device, sizeof c->mouse_device, "%s", v);
    else if (EQ("tap_ms")) c->tap_ms = n;
    else if (EQ("swipe_ms")) c->swipe_ms = n;
    else if (EQ("gamepad")) c->gamepad_enable = n;
    else if (EQ("virtual_keyboard")) c->virtual_keyboard = n;
    else if (EQ("toggle_key")) c->toggle_key = parse_keyname(v);
    else if (EQ("profile_key")) c->profile_key = parse_keyname(v);
    else if (EQ("calibrate_key")) c->calib_key = parse_keyname(v);
    else if (EQ("quit_key")) c->quit_key = parse_keyname(v);
    else if (EQ("auto_profile")) c->auto_profile = n;
    else if (EQ("auto_profile_ms")) c->auto_profile_ms = n;
    else if (EQ("enable_on_boot")) c->enable_on_boot = n;
    else if (EQ("slots")) c->slot_count = n;
    else if (EQ("jitter")) c->jitter = n;
    #undef EQ
}

/* Elle profil secildiyse (F10) yalnizca o profilin + config.ini'nin
 * genel eslesmeleri kullanilir. Otomatik modda on plandaki uygulamaya
 * uyan eslesme, o yoksa genel eslesme kullanilir. */
static Bind *bind_for_key(int key, const char *app) {
    Bind *fallback = NULL;
    for (int i = 0; i < g_nbinds; i++) {
        Bind *b = &g_binds[i];
        if (b->keycode != key) continue;

        if (g_active_profile[0]) {
            if (b->src[0] && strcasecmp(b->src, g_active_profile) != 0) continue;
            return b;   /* src bos (genel) veya secili profil */
        }
        if (b->manual) continue;   /* elle secilen profiller otomatik modda devre disi */
        if (app && *app && b->app[0] && strcasecmp(b->app, app) == 0) return b;
        if (!b->app[0]) fallback = b;
    }
    return fallback;
}

static Bind *new_bind(void) {
    if (g_nbinds >= MAX_BINDS) { logmsg("bind limiti (%d) doldu", MAX_BINDS); return NULL; }
    Bind *b = &g_binds[g_nbinds++];
    memset(b, 0, sizeof *b);
    b->used = 1; b->slot = -1; b->keycode = -1; b->gp_axis = -1;
    b->duration_ms = -1; b->out_hold_ms = 40;
    return b;
}

static int load_config(const char *path, int is_profile) {
    FILE *f = fopen(path, "r");
    if (!f) return -1;
    const int first_bind = g_nbinds;   /* bu yuklemede eklenen bind'larin baslangici */

    char line[MAX_LINE];
    char section[64] = "";
    Bind *cur = NULL;
    int in_global = 0;
    char file_app[64] = "";   /* profil dosyasinin bagli oldugu paket */
    int  file_manual = 0;     /* [app] auto = 0 ise profil sadece F10 ile secilir */
    char scope_app[64] = "";  /* [app:paket] bolumunden gelen kapsam */
    const char *base = strrchr(path, '/');
    char src_name[64] = "";
    if (is_profile) {
        base = base ? base + 1 : path;
        snprintf(src_name, sizeof src_name, "%s", base);
        char *dot = strrchr(src_name, '.');
        if (dot) *dot = 0;
    }

    while (fgets(line, sizeof line, f)) {
        char *s = trim(line);
        if (!*s || *s == '#' || *s == ';') continue;

        if (*s == '[') {
            char *e = strchr(s, ']');
            if (!e) continue;
            *e = 0;
            snprintf(section, sizeof section, "%s", s + 1);
            cur = NULL;
            if (strcasecmp(section, "app") == 0) { in_global = 2; continue; }
            in_global = (strcasecmp(section, "global") == 0 ||
                         strcasecmp(section, "screen") == 0 ||
                         strcasecmp(section, "gamepad") == 0 ||
                         strcasecmp(section, "keys") == 0);
            if (strncmp(section, "bind:", 5) == 0) {
                cur = new_bind();
                if (cur) {
                    cur->keycode = parse_keyname(section + 5);
                    snprintf(cur->src, sizeof cur->src, "%s", src_name);
                    cur->manual = file_manual;
                    if (scope_app[0])
                        snprintf(cur->app, sizeof cur->app, "%s", scope_app);
                    if (cur->keycode < 0)
                        logmsg("bilinmeyen tus adi: '%s' (config: %s)", section + 5, path);
                }
            } else if (strncmp(section, "app:", 4) == 0) {
                /* [app:paket] -> bu bolumdeki bind'lar bu uygulamaya ait */
                snprintf(scope_app, sizeof scope_app, "%s", section + 4);
                in_global = 0;
            } else {
                scope_app[0] = 0;
            }
            continue;
        }

        char *eq = strchr(s, '=');
        if (!eq) continue;
        *eq = 0;
        char *k = trim(s);
        char *v = trim(eq + 1);
        strip_inline_comment(v);

        if (in_global == 2) {   /* [app] bolumu: profilin baglanacagi paket adi */
            if (strcasecmp(k, "package") == 0 || strcasecmp(k, "app") == 0 ||
                strcasecmp(k, "name") == 0)
                snprintf(file_app, sizeof file_app, "%s", v);
            else if (strcasecmp(k, "auto") == 0)
                file_manual = (strtol(v, NULL, 0) == 0);
            continue;
        }

        /* [app:xxx] bolumu altindaki bind tanimi */
        if (cur) {
            if (strcasecmp(k, "app") == 0) { snprintf(cur->app, sizeof cur->app, "%s", v); continue; }
        }

        if (in_global || !cur) {
            /* global ayarlar */
            if (strncasecmp(section, "app:", 4) == 0 && strcasecmp(k, "bind") == 0) {
                /* [app:com.x] bind=SPACE  gibi kisa kullanim da desteklenmez; bind bolumleri kullan */
            }
            if (!cur) { cfg_int(&g_cfg, k, v);
                        if (strcasecmp(k, "gamepad_name") == 0) snprintf(g_cfg.gamepad_name, sizeof g_cfg.gamepad_name, "%s", v);
                        if (strcasecmp(k, "device") == 0) snprintf(g_cfg.device_path, sizeof g_cfg.device_path, "%s", v);
                        if (strcasecmp(k, "device_match") == 0) snprintf(g_cfg.device_match, sizeof g_cfg.device_match, "%s", v);
                        continue; }
        }

        if (!cur) continue;

        #define BEQ(x) (strcasecmp(k, x) == 0)
        if (BEQ("action")) {
            if (!strcasecmp(v, "tap")) cur->action = ACT_TAP;
            else if (!strcasecmp(v, "swipe")) cur->action = ACT_SWIPE;
            else if (!strcasecmp(v, "key")) cur->action = ACT_KEY;
            else if (!strcasecmp(v, "gamepad_button") || !strcasecmp(v, "gp_button")) cur->action = ACT_GP_BUTTON;
            else if (!strcasecmp(v, "gamepad_stick") || !strcasecmp(v, "gp_stick")) cur->action = ACT_GP_STICK;
            else logmsg("bilinmeyen action: %s", v);
        }
        else if (BEQ("x")) cur->x = atoi(v);
        else if (BEQ("y")) cur->y = atoi(v);
        else if (BEQ("x2")) cur->x2 = atoi(v);
        else if (BEQ("y2")) cur->y2 = atoi(v);
        else if (BEQ("ms") || BEQ("duration_ms") || BEQ("duration")) cur->duration_ms = atoi(v);
        else if (BEQ("hold")) cur->hold = atoi(v);
        else if (BEQ("repeat_ms") || BEQ("repeat")) cur->repeat_ms = atoi(v);
        else if (BEQ("out_key") || BEQ("key_out")) cur->out_key = parse_keyname(v);
        else if (BEQ("out_hold_ms")) cur->out_hold_ms = atoi(v);
        else if (BEQ("gp_button") || BEQ("button")) {
            if (!strcasecmp(v, "A") || !strcasecmp(v, "BTN_SOUTH")) cur->gp_button = BTN_SOUTH;
            else if (!strcasecmp(v, "B") || !strcasecmp(v, "BTN_EAST")) cur->gp_button = BTN_EAST;
            else if (!strcasecmp(v, "X") || !strcasecmp(v, "BTN_WEST")) cur->gp_button = BTN_WEST;
            else if (!strcasecmp(v, "Y") || !strcasecmp(v, "BTN_NORTH")) cur->gp_button = BTN_NORTH;
            else if (!strcasecmp(v, "LB") || !strcasecmp(v, "L1") || !strcasecmp(v, "BTN_TL")) cur->gp_button = BTN_TL;
            else if (!strcasecmp(v, "RB") || !strcasecmp(v, "R1") || !strcasecmp(v, "BTN_TR")) cur->gp_button = BTN_TR;
            else if (!strcasecmp(v, "LT") || !strcasecmp(v, "L2")) cur->gp_button = -100;
            else if (!strcasecmp(v, "RT") || !strcasecmp(v, "R2")) cur->gp_button = -101;
            else if (!strcasecmp(v, "START")) cur->gp_button = BTN_START;
            else if (!strcasecmp(v, "SELECT") || !strcasecmp(v, "BACK")) cur->gp_button = BTN_SELECT;
            else if (!strcasecmp(v, "GUIDE") || !strcasecmp(v, "MODE")) cur->gp_button = BTN_MODE;
            else if (!strcasecmp(v, "THUMBL") || !strcasecmp(v, "L3")) cur->gp_button = BTN_THUMBL;
            else if (!strcasecmp(v, "THUMBR") || !strcasecmp(v, "R3")) cur->gp_button = BTN_THUMBR;
            else if (!strcasecmp(v, "DPAD_UP")) cur->gp_button = BTN_TRIGGER_HAPPY1;
            else if (!strcasecmp(v, "DPAD_DOWN")) cur->gp_button = BTN_TRIGGER_HAPPY2;
            else if (!strcasecmp(v, "DPAD_LEFT")) cur->gp_button = BTN_TRIGGER_HAPPY3;
            else if (!strcasecmp(v, "DPAD_RIGHT")) cur->gp_button = BTN_TRIGGER_HAPPY4;
            else cur->gp_button = (int)strtol(v, NULL, 0);
        }
        else if (BEQ("axis")) {
            if (!strcasecmp(v, "LX")) cur->gp_axis = 0;
            else if (!strcasecmp(v, "LY")) cur->gp_axis = 1;
            else if (!strcasecmp(v, "RX")) cur->gp_axis = 2;
            else if (!strcasecmp(v, "RY")) cur->gp_axis = 3;
            else cur->gp_axis = atoi(v);
        }
        else if (BEQ("gx")) cur->gp_x = atoi(v);
        else if (BEQ("gy")) cur->gp_y = atoi(v);
        else if (BEQ("label") || BEQ("name")) snprintf(cur->label, sizeof cur->label, "%s", v);
        #undef BEQ
    }
    fclose(f);

    /* profil dosyasindaysa: dosya adindan uygulama paketini cikar */
    if (is_profile) {
        /* Profil hangi uygulamaya bagli?
         *   1) dosyada [app] package = ... yaziyorsa onu kullan
         *   2) yoksa dosya adi <paket>.ini ise onu kullan
         *   3) "genel/global/default" ise hicbir uygulamaya baglama (her yerde gecerli)
         */
        char app[64] = "";
        if (file_app[0]) {
            snprintf(app, sizeof app, "%s", file_app);
        } else {
            const char *base = strrchr(path, '/');
            base = base ? base + 1 : path;
            snprintf(app, sizeof app, "%s", base);
            char *dot = strrchr(app, '.');
            if (dot) *dot = 0;
        }
        if (strcmp(app, "genel") && strcmp(app, "global") && strcmp(app, "default")) {
            for (int i = first_bind; i < g_nbinds; i++)
                if (!g_binds[i].app[0]) snprintf(g_binds[i].app, sizeof g_binds[i].app, "%s", app);
            logv("profil '%s' -> uygulama '%s' (%d eslesme%s)", path, app,
                 g_nbinds - first_bind, file_manual ? ", sadece F10 ile" : "");
        } else {
            logv("profil '%s' -> genel (%d eslesme)", path, g_nbinds - first_bind);
        }
    }
    return 0;
}

static void scan_profiles(void) {
    g_profile_count = 0;
    DIR *d = opendir(g_profiledir);
    if (!d) return;
    struct dirent *e;
    while ((e = readdir(d)) && g_profile_count < PROFILE_SLOTS) {
        const char *n = e->d_name;
        size_t l = strlen(n);
        if (l < 5 || strcmp(n + l - 4, ".ini") != 0) continue;
        snprintf(g_profile_files[g_profile_count++], sizeof g_profile_files[0], "%s/%s", g_profiledir, n);
    }
    closedir(d);
    for (int i = 0; i < g_profile_count; i++)
        for (int j = i + 1; j < g_profile_count; j++)
            if (strcmp(g_profile_files[i], g_profile_files[j]) > 0) {
                char t[384]; strcpy(t, g_profile_files[i]);
                strcpy(g_profile_files[i], g_profile_files[j]);
                strcpy(g_profile_files[j], t);
            }
}

static void ensure_pad_for_context(void);   /* ileri bildirim (tanim asagida) */

static void reload_all(void) {
    /* aktif dokunuslari birak */
    for (int i = 0; i < g_nbinds; i++)
        if (g_binds[i].slot >= 0 && g_touch_fd >= 0) touch_up(g_binds[i].slot);
    g_nbinds = 0;
    memset(g_binds, 0, sizeof g_binds);

    if (load_config(g_cfgpath, 0) != 0)
        logmsg("UYARI: %s okunamadi, varsayilanlar kullaniliyor", g_cfgpath);

    scan_profiles();
    for (int i = 0; i < g_profile_count; i++)
        load_config(g_profile_files[i], 1);

    logmsg("config yuklendi: %d eslesme, %d profil, ekran %dx%d, rotation %d",
           g_nbinds, g_profile_count, g_cfg.screen_w, g_cfg.screen_h, g_cfg.rotation);
    for (int i = 0; i < g_nbinds; i++) {
        Bind *b = &g_binds[i];
        logv("  bind[%d] key=%d src='%s' app='%s' action=%d (%d,%d)->(%d,%d) hold=%d rep=%d outkey=%d gp=%d/%d",
             i, b->keycode, b->src, b->app, b->action, b->x, b->y, b->x2, b->y2,
             b->hold, b->repeat_ms, b->out_key, b->gp_button, b->gp_axis);
    }
    g_pad_auto_failed = 0;
    ensure_pad_for_context();
}

/* ------------------------------------------------------------------ */
/* klavye cihazi bulma                                                 */
/* ------------------------------------------------------------------ */
static int has_key(unsigned long *bits, int code) {
    return (bits[code / 64] >> (code % 64)) & 1UL;
}

static int looks_like_keyboard(int fd) {
    unsigned long evbits[(EV_MAX + 63) / 64] = {0};
    unsigned long keybits[(KEY_MAX + 63) / 64] = {0};
    if (ioctl(fd, EVIOCGBIT(0, sizeof evbits), evbits) < 0) return 0;
    if (!((evbits[EV_KEY / 64] >> (EV_KEY % 64)) & 1UL)) return 0;
    if (ioctl(fd, EVIOCGBIT(EV_KEY, sizeof keybits), keybits) < 0) return 0;

    /* en az A, S, D, SPACE ve ENTER'i desteklemeli */
    int need[] = { KEY_A, KEY_S, KEY_D, KEY_W, KEY_SPACE, KEY_ENTER, KEY_UP, KEY_DOWN };
    int ok = 0;
    for (unsigned i = 0; i < sizeof need / sizeof need[0]; i++)
        if (has_key(keybits, need[i])) ok++;
    return ok >= 6;
}

/* Fare mi? BT/USB fare: BTN_LEFT + REL_X/REL_Y. Touchpad'ler (ABS_MT) HARIC:
   dokunmatik imlec calismaya devam etsin. Sanal cihazlarimiz da elenir. */
static int looks_like_mouse(int fd) {
    unsigned long evbits[(EV_MAX + 63) / 64] = {0};
    unsigned long keybits[(KEY_MAX + 63) / 64] = {0};
    unsigned long relbits[(REL_MAX + 63) / 64] = {0};
    unsigned long absbits[(ABS_MAX + 63) / 64] = {0};
    if (ioctl(fd, EVIOCGBIT(0, sizeof evbits), evbits) < 0) return 0;
    if (!((evbits[EV_KEY / 64] >> (EV_KEY % 64)) & 1UL)) return 0;
    if (ioctl(fd, EVIOCGBIT(EV_KEY, sizeof keybits), keybits) < 0) return 0;
    if (!has_key(keybits, BTN_LEFT)) return 0;
    if (ioctl(fd, EVIOCGBIT(EV_ABS, sizeof absbits), absbits) == 0 &&
        (absbits[ABS_MT_SLOT / 64] >> (ABS_MT_SLOT % 64)) & 1UL) return 0;  /* touchpad */
    if ((evbits[EV_REL / 64] >> (EV_REL % 64)) & 1UL) {
        if (ioctl(fd, EVIOCGBIT(EV_REL, sizeof relbits), relbits) == 0 &&
            has_key(relbits, REL_X) && has_key(relbits, REL_Y)) return 1;
    }
    return !looks_like_keyboard(fd);
}

static int find_mouse(void) {
    if (g_cfg.mouse_device[0]) {
        int fd = open(g_cfg.mouse_device, O_RDONLY);
        if (fd >= 0) { snprintf(g_mouse_path, sizeof g_mouse_path, "%s", g_cfg.mouse_device); return fd; }
        logmsg("config'teki fare acilamadi: %s", g_cfg.mouse_device);
    }
    DIR *d = opendir("/dev/input");
    if (!d) return -1;
    struct dirent *e;
    int best = -1; char bestpath[160] = ""; char bestname[256] = "";
    while ((e = readdir(d))) {
        if (strncmp(e->d_name, "event", 5) != 0) continue;
        char path[320]; snprintf(path, sizeof path, "/dev/input/%s", e->d_name);
        int fd = open(path, O_RDONLY);
        if (fd < 0) continue;
        char name[256] = "";
        ioctl(fd, EVIOCGNAME(sizeof name), name);
        if (strcasestr(name, "kbd2touch")) { close(fd); continue; }  /* sanal cihazlarimiz */
        if (looks_like_mouse(fd)) {
            int score = strcasestr(name, "mouse") ? 100 : 1;
            if (best >= 0 && score < 100) { close(fd); continue; }
            if (best >= 0) close(best);
            best = fd;
            snprintf(bestpath, sizeof bestpath, "%.140s", path);
            snprintf(bestname, sizeof bestname, "%.200s", name);
            if (score >= 100) break;
        } else close(fd);
    }
    closedir(d);
    if (best >= 0) {
        snprintf(g_mouse_path, sizeof g_mouse_path, "%s", bestpath);
        logmsg("fare bulundu: %s -> '%s'", bestpath, bestname);
    }
    return best;
}

static int find_keyboard(void) {
    if (g_cfg.device_path[0]) {
        int fd = open(g_cfg.device_path, O_RDONLY);
        if (fd >= 0) { logmsg("klavye (config): %s", g_cfg.device_path); return fd; }
        logmsg("config'teki cihaz acilamadi: %s", g_cfg.device_path);
    }

    DIR *d = opendir("/dev/input");
    if (!d) { logmsg("/dev/input acilamadi"); return -1; }
    struct dirent *e;
    int best = -1; char bestname[256] = "";
    while ((e = readdir(d))) {
        if (strncmp(e->d_name, "event", 5) != 0) continue;
        char path[320]; snprintf(path, sizeof path, "/dev/input/%s", e->d_name);
        int fd = open(path, O_RDONLY);
        if (fd < 0) continue;

        char name[256] = "";
        ioctl(fd, EVIOCGNAME(sizeof name), name);

        if (g_cfg.device_match[0] && !strcasestr(name, g_cfg.device_match)) { close(fd); continue; }

        if (looks_like_keyboard(fd)) {
            logmsg("klavye adayi: %s -> '%s'", path, name);
            /* Xiaomi klavyesi / "keyboard" iceren isimleri tercih et */
            int score = 0;
            if (strcasestr(name, "xiaomi")) score += 100;
            if (strcasestr(name, "keyboard")) score += 50;
            if (strcasestr(name, "pogo")) score += 30;
            if (best < 0 || score > 0) {
                if (best >= 0) close(best);
                best = fd;
                snprintf(bestname, sizeof bestname, "%.180s", path);
                logmsg("  isim: '%s'", name);
                if (score >= 100) break;
            } else close(fd);
        } else close(fd);
    }
    closedir(d);
    if (best >= 0) logmsg("secilen klavye: %s", bestname);
    return best;
}

static void list_devices(void) {
    DIR *d = opendir("/dev/input");
    if (!d) { printf("/dev/input acilamadi\n"); return; }
    struct dirent *e;
    printf("%-22s %-40s %s\n", "NODE", "NAME", "SINIF");
    while ((e = readdir(d))) {
        if (strncmp(e->d_name, "event", 5) != 0) continue;
        char path[320]; snprintf(path, sizeof path, "/dev/input/%s", e->d_name);
        int fd = open(path, O_RDONLY);
        if (fd < 0) continue;
        char name[256] = ""; ioctl(fd, EVIOCGNAME(sizeof name), name);
        unsigned long evbits[(EV_MAX + 63) / 64] = {0};
        ioctl(fd, EVIOCGBIT(0, sizeof evbits), evbits);
        char cls[128] = "";
        #define HAS(t) ((evbits[(t)/64] >> ((t)%64)) & 1UL)
        if (HAS(EV_KEY)) strcat(cls, "KEY ");
        if (HAS(EV_REL)) strcat(cls, "REL(mouse) ");
        if (HAS(EV_ABS)) strcat(cls, "ABS ");
        if (looks_like_keyboard(fd)) strcat(cls, "=> KLAVYE ");
        else if (looks_like_mouse(fd)) strcat(cls, "=> FARE ");
        #undef HAS
        printf("%-22s %-40s %s\n", path, name, cls);
        close(fd);
    }
    closedir(d);
}

static void write_status(const char *fmt, ...);   /* ileri bildirim */

/* On plandaki uygulama icin uygulama-kapsamli (profil) eslesme var mi? */
static int app_has_profile(const char *app) {
    if (!app || !*app) return 0;
    for (int i = 0; i < g_nbinds; i++) {
        Bind *b = &g_binds[i];
        if (b->manual) continue;
        if (b->app[0] && b->src[0] && strcasecmp(b->app, app) == 0) return 1;
    }
    return 0;
}

/* Klavyeyi ele gecirip gecirmeme karari.
 * grab_mode=1 iken YALNIZCA eslenen bir oyun on plandayken grab edilir;
 * boylece tarayicida/sohbette yazı yazarken klavye NORMAL calismaya devam eder. */
static int should_grab(void) {
    if (!g_enabled || !g_cfg.grab_keyboard) return 0;
    if (g_cfg.grab_mode == 2) return 1;
    if (g_cfg.grab_mode == 1)
        return g_active_profile[0] || app_has_profile(g_current_app);
    return 0;
}

static void set_grab(int on) {
    if ((g_kbd_fd < 0 && g_mouse_fd < 0) || on == g_grabbed) return;
    int v = on ? 1 : 0;
    int ok = 0;
    if (g_kbd_fd >= 0) {
        if (ioctl(g_kbd_fd, EVIOCGRAB, &v) == 0) ok = 1;
        else logmsg("UYARI: klavye EVIOCGRAB %s basarisiz: %s", on ? "acma" : "kapama", strerror(errno));
    }
    if (g_mouse_fd >= 0 && g_cfg.grab_mouse) {
        if (ioctl(g_mouse_fd, EVIOCGRAB, &v) == 0) ok = 1;
        else logmsg("UYARI: fare EVIOCGRAB %s basarisiz: %s", on ? "acma" : "kapama", strerror(errno));
    }
    if (!ok) return;
    g_grabbed = on;
    logmsg("klavye%s %s", g_mouse_fd >= 0 && g_cfg.grab_mouse ? " + fare" : "",
           on ? "ELE GECIRILDI (oyun ham girdiyi gormez)"
              : "SERBEST BIRAKILDI (normal yazma/imlec aktif)");
    write_status("enabled=%d grab=%d app=%s", g_enabled, g_grabbed, g_current_app);
}

/* ------------------------------------------------------------------ */
/* on plandaki uygulama                                                */
/* ------------------------------------------------------------------ */
/* dumpsys window satirinden PAKET adini cikar.
 * Gercek AOSP/MIUI bicimi:
 *   mCurrentFocus=Window{3af0708 u0 com.pkg/com.pkg.Activity}
 *   mFocusedApp=ActivityRecord{abc1234 u0 com.pkg/.Act t2537}
 * KOK NEDEN (v1.0.4'te duzeltildi): eski parser '{' icindeki ILK bosluktan
 * sonrasini alip ilk '/'e kadari paket saniyordu; gercek bicimde hash'ten
 * sonra bir de kullanici kimligi ("u0") oldugu icin sonuc
 * "u0 com.pkg" cikiyordu -> hicbir profil eslesmiyordu (grab=0, pad yok). */
static int parse_focus_pkg(const char *line, char *out, size_t n) {
    const char *lt = strchr(line, '{');
    const char *gt = lt ? strchr(lt, '}') : NULL;
    if (!lt || !gt || gt <= lt) return -1;
    char buf[512];
    size_t len = (size_t)(gt - lt - 1);
    if (len >= sizeof buf) len = sizeof buf - 1;
    memcpy(buf, lt + 1, len);
    buf[len] = 0;
    out[0] = 0;
    char fallback[256] = "";
    char *save = NULL;
    for (char *t = strtok_r(buf, " \t", &save); t; t = strtok_r(NULL, " \t", &save)) {
        char *slash = strchr(t, '/');
        if (slash) {                       /* component: com.pkg/act */
            *slash = 0;
            if (*t && strchr(t, '.')) { snprintf(out, n, "%s", t); return 0; }
        } else if (!fallback[0] && strchr(t, '.')) {
            snprintf(fallback, sizeof fallback, "%s", t);   /* '/'siz component */
        }
    }
    if (fallback[0]) { snprintf(out, n, "%s", fallback); return 0; }
    return -1;
}

static int get_foreground_app(char *out, size_t n) {
    FILE *p = popen("dumpsys window 2>/dev/null | grep -E 'mCurrentFocus|mFocusedApp' | head -2", "r");
    if (!p) return -1;
    char line[1024];
    out[0] = 0;
    while (fgets(line, sizeof line, p)) {
        if (parse_focus_pkg(line, out, n) == 0 && out[0]) break;
    }
    pclose(p);
    return out[0] ? 0 : -1;
}

/* ------------------------------------------------------------------ */
/* eylem calistirma                                                    */
/* ------------------------------------------------------------------ */
static long ms_now(void) {
    struct timespec ts; clock_gettime(CLOCK_MONOTONIC, &ts);
    return ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

typedef struct { int slot; long until; } PendingUp;
static PendingUp g_pending[MAX_BINDS];
static int g_pending_n = 0;

static void pending_add(int slot, long until) {
    if (g_pending_n >= MAX_BINDS) return;
    g_pending[g_pending_n].slot = slot;
    g_pending[g_pending_n].until = until;
    g_pending_n++;
}

static void do_action_press(Bind *b) {
    if (!g_enabled) return;
    switch (b->action) {
        case ACT_TAP: {
            if (g_touch_fd < 0) return;
            int slot = slot_alloc();
            if (slot < 0) { logmsg("bos slot yok (ayni anda %d dokunus)", g_cfg.slot_count); return; }
            int tx, ty; map_point(b->x, b->y, &tx, &ty);
            b->slot = slot; b->curx = tx; b->cury = ty;
            touch_down(slot, tx, ty);
            int dur = b->duration_ms >= 0 ? b->duration_ms : g_cfg.tap_ms;
            if (b->hold) {
                /* tus birakilana kadar basili */
            } else {
                pending_add(slot, ms_now() + dur);
                b->slot = -1;   /* slot pending tarafindan birakilacak */
            }
            logv("TAP slot=%d ekran(%d,%d)->touch(%d,%d) dur=%dms hold=%d",
                 slot, b->x, b->y, tx, ty, dur, b->hold);
            break;
        }
        case ACT_SWIPE: {
            if (g_touch_fd < 0) return;
            int slot = slot_alloc();
            if (slot < 0) return;
            int x1, y1; map_point(b->x, b->y, &x1, &y1);
            b->slot = slot; b->curx = x1; b->cury = y1;
            b->swiping = 1;
            clock_gettime(CLOCK_MONOTONIC, &b->swipe_start);
            touch_down(slot, x1, y1);
            logv("SWIPE basladi slot=%d (%d,%d)->(%d,%d) %dms",
                 slot, b->x, b->y, b->x2, b->y2,
                 b->duration_ms >= 0 ? b->duration_ms : g_cfg.swipe_ms);
            break;
        }
        case ACT_KEY: {
            if (g_key_fd < 0) { logv("sanal klavye yok, KEY eylemi atlandi"); return; }
            int k = b->out_key > 0 ? b->out_key : b->keycode;
            ev(g_key_fd, EV_KEY, k, 1);
            syn(g_key_fd);
            if (!b->hold) pending_add(-1000 - k, ms_now() + (b->out_hold_ms > 0 ? b->out_hold_ms : 40));
            logv("KEY %d basildi", k);
            break;
        }
        case ACT_GP_BUTTON: {
            if (g_pad_fd < 0) return;
            if (b->gp_button == -100) { ev(g_pad_fd, EV_ABS, ABS_Z, 255); syn(g_pad_fd); return; }
            if (b->gp_button == -101) { ev(g_pad_fd, EV_ABS, ABS_RZ, 255); syn(g_pad_fd); return; }
            ev(g_pad_fd, EV_KEY, b->gp_button, 1);
            syn(g_pad_fd);
            if (!b->hold && b->gp_button >= 0)
                pending_add(-2000 - b->gp_button, ms_now() + (b->duration_ms >= 0 ? b->duration_ms : 60));
            logv("GAMEPAD buton %d basildi", b->gp_button);
            break;
        }
        case ACT_GP_STICK: {
            if (g_pad_fd < 0) return;
            int ax = b->gp_axis == 0 ? ABS_X : b->gp_axis == 1 ? ABS_Y :
                     b->gp_axis == 2 ? ABS_RX : ABS_RY;
            ev(g_pad_fd, EV_ABS, ax, b->gp_axis == 0 || b->gp_axis == 2 ? b->gp_x : b->gp_y);
            if (b->gp_x && b->gp_y) {
                int ax2 = b->gp_axis == 0 ? ABS_Y : b->gp_axis == 2 ? ABS_RY : ABS_X;
                ev(g_pad_fd, EV_ABS, ax2, b->gp_axis == 0 || b->gp_axis == 2 ? b->gp_y : b->gp_x);
            }
            syn(g_pad_fd);
            logv("GAMEPAD ekseni ayarlandi");
            break;
        }
        default: break;
    }
}

static void do_action_release(Bind *b) {
    switch (b->action) {
        case ACT_TAP:
            if (b->slot >= 0 && g_touch_fd >= 0) { touch_up(b->slot); b->slot = -1; }
            break;
        case ACT_SWIPE:
            if (b->slot >= 0 && g_touch_fd >= 0) { touch_up(b->slot); b->slot = -1; }
            b->swiping = 0;
            break;
        case ACT_KEY: {
            int k = b->out_key > 0 ? b->out_key : b->keycode;
            if (g_key_fd >= 0) { ev(g_key_fd, EV_KEY, k, 0); syn(g_key_fd); }
            break;
        }
        case ACT_GP_BUTTON:
            if (g_pad_fd >= 0) {
                if (b->gp_button == -100) { ev(g_pad_fd, EV_ABS, ABS_Z, 0); }
                else if (b->gp_button == -101) { ev(g_pad_fd, EV_ABS, ABS_RZ, 0); }
                else ev(g_pad_fd, EV_KEY, b->gp_button, 0);
                syn(g_pad_fd);
            }
            break;
        case ACT_GP_STICK: {
            if (g_pad_fd < 0) break;
            int ax = b->gp_axis == 0 ? ABS_X : b->gp_axis == 1 ? ABS_Y :
                     b->gp_axis == 2 ? ABS_RX : ABS_RY;
            ev(g_pad_fd, EV_ABS, ax, 0);
            if (b->gp_x && b->gp_y) {
                int ax2 = b->gp_axis == 0 ? ABS_Y : b->gp_axis == 2 ? ABS_RY : ABS_X;
                ev(g_pad_fd, EV_ABS, ax2, 0);
            }
            syn(g_pad_fd);
            break;
        }
        default: break;
    }
}

static void update_swipes(void) {
    for (int i = 0; i < g_nbinds; i++) {
        Bind *b = &g_binds[i];
        if (!b->swiping || b->slot < 0) continue;
        int dur = b->duration_ms > 0 ? b->duration_ms : g_cfg.swipe_ms;
        struct timespec now; clock_gettime(CLOCK_MONOTONIC, &now);
        long el = (now.tv_sec - b->swipe_start.tv_sec) * 1000 +
                  (now.tv_nsec - b->swipe_start.tv_nsec) / 1000000;
        double t = dur > 0 ? (double)el / (double)dur : 1.0;
        if (t > 1.0) t = 1.0;
        int sx = b->x + (int)((b->x2 - b->x) * t);
        int sy = b->y + (int)((b->y2 - b->y) * t);
        int tx, ty; map_point(sx, sy, &tx, &ty);
        if (tx != b->curx || ty != b->cury) { touch_move(b->slot, tx, ty); b->curx = tx; b->cury = ty; }
        if (t >= 1.0 && !b->hold) { touch_up(b->slot); b->slot = -1; b->swiping = 0; }
    }
}

static void update_pending(void) {
    long now = ms_now();
    int w = 0;
    for (int i = 0; i < g_pending_n; i++) {
        PendingUp *p = &g_pending[i];
        if (p->until <= now) {
            if (p->slot >= 0) {
                if (g_touch_fd >= 0) touch_up(p->slot);
            } else if (p->slot <= -2000) {
                int btn = -2000 - p->slot;
                if (g_pad_fd >= 0) { ev(g_pad_fd, EV_KEY, btn, 0); syn(g_pad_fd); }
            } else if (p->slot <= -1000) {
                int k = -1000 - p->slot;
                if (g_key_fd >= 0) { ev(g_key_fd, EV_KEY, k, 0); syn(g_key_fd); }
            }
        } else {
            g_pending[w++] = *p;
        }
    }
    g_pending_n = w;
}

static void update_repeats(void) {
    long now = ms_now();
    for (int i = 0; i < g_nbinds; i++) {
        Bind *b = &g_binds[i];
        if (!b->pressed || b->repeat_ms <= 0 || !g_enabled) continue;
        if (b->repeat_last_ms && now - b->repeat_last_ms >= b->repeat_ms) {
            do_action_press(b);
            b->repeat_last_ms = now;
        }
    }
}

static void release_all(void) {
    for (int i = 0; i < g_nbinds; i++) {
        Bind *b = &g_binds[i];
        if (b->slot >= 0 || b->swiping) do_action_release(b);
        b->pressed = 0;
        b->repeat_last_ms = 0;
    }
    /* kuyruktaki gecikmis birakmalar ve dolu slotlar temizlenir */
    for (int i = 0; i < g_pending_n; i++) {
        if (g_pending[i].slot >= 0 && g_touch_fd >= 0) touch_up(g_pending[i].slot);
    }
    g_pending_n = 0;
    if (g_touch_fd >= 0) {
        for (int i = 0; i < MAX_SLOTS; i++) if (g_slot_busy[i]) touch_up(i);
        ev(g_touch_fd, EV_KEY, BTN_TOUCH, 0);
        ev(g_touch_fd, EV_KEY, BTN_TOOL_FINGER, 0);
        syn(g_touch_fd);
    }
}

/* ------------------------------------------------------------------ */
/* baglama gore sanal gamepad (Dead Cells vb. kontrolor destekli oyunlar) */
/* ------------------------------------------------------------------ */

/* Aktif baglam (F10 profili veya on plandaki uygulama) gamepad eslemesi
 * iceriyor mu? bind_for_key ile ayni filtreleme mantigi kullanilir. */
static int context_needs_pad(void) {
    for (int i = 0; i < g_nbinds; i++) {
        Bind *b = &g_binds[i];
        if (b->action != ACT_GP_BUTTON && b->action != ACT_GP_STICK) continue;
        if (g_active_profile[0]) {
            if (!b->src[0] || strcasecmp(b->src, g_active_profile) == 0) return 1;
        } else {
            if (b->manual) continue;   /* elle secilen profil otomatik modda devre disi */
            if (b->app[0]) {
                if (g_current_app[0] && strcasecmp(b->app, g_current_app) == 0) return 1;
            } else {
                return 1;   /* config.ini'de genel gp eslemesi: pad hep gerekir */
            }
        }
    }
    return 0;
}

/* Sanal gamepad'i ihtiyaca gore olustur/kaldir:
 *  - gamepad = 1 (global) : pad acilista olusur, burada dokunulmaz
 *  - gamepad = 0 (vars.)  : gp eslemeli oyun one gelince pad OLUSTURULUR
 *    (Dead Cells "kontrolor baglandi" diye otomatik kol moduna gecer),
 *    oyun/profil degisince pad KALDIRILIR (dokunmatik oyunlar kol gormesin). */
static void ensure_pad_for_context(void) {
    int need = context_needs_pad();
    if (need) {
        if (g_pad_fd >= 0 || g_pad_auto_failed) return;
        if (pad_create() == 0) {
            g_pad_auto = 1;
            logmsg("sanal gamepad OLUSTURULDU (X-Box 360 pad) — oyun artik kol görmeli");
        } else {
            g_pad_auto_failed = 1;
            logmsg("UYARI: gamepad profili aktif ama sanal pad olusturulamadi (uinput?)");
        }
    } else if (g_pad_auto && g_pad_fd >= 0 && !g_cfg.gamepad_enable) {
        for (int i = 0; i < g_nbinds; i++) {   /* basili gp tus/eksen birak */
            Bind *b = &g_binds[i];
            if ((b->action == ACT_GP_BUTTON || b->action == ACT_GP_STICK) && b->pressed)
                do_action_release(b);
            b->pressed = 0;
        }
        ioctl(g_pad_fd, UI_DEV_DESTROY);
        close(g_pad_fd);
        g_pad_fd = -1;
        g_pad_auto = 0;
        logmsg("sanal gamepad kaldirildi (gamepad oyunu/profili kapandi)");
    }
}

/* ------------------------------------------------------------------ */
/* kalibrasyon                                                         */
/* ------------------------------------------------------------------ */
static void calibrate(void) {
    if (g_touch_fd < 0) { logmsg("kalibrasyon: dokunmatik cihaz yok"); return; }
    int pts[4][2] = { {100, 100}, {g_cfg.screen_w - 100, 100},
                      {g_cfg.screen_w - 100, g_cfg.screen_h - 100}, {100, g_cfg.screen_h - 100} };
    const char *names[4] = { "SOL-UST", "SAG-UST", "SAG-ALT", "SOL-ALT" };
    logmsg("=== KALIBRASYON: 4 koseye 1,5 sn arayla dokunulacak (rotation=%d, swap_xy=%d, inv=%d/%d)",
           g_cfg.rotation, g_cfg.swap_xy, g_cfg.invert_x, g_cfg.invert_y);
    for (int i = 0; i < 4; i++) {
        int slot = 0;
        int tx, ty; map_point(pts[i][0], pts[i][1], &tx, &ty);
        touch_down(slot, tx, ty);
        logmsg("  %s: ekran(%d,%d) -> touch(%d,%d)", names[i], pts[i][0], pts[i][1], tx, ty);
        usleep(400000);
        touch_up(slot);
        usleep(1100000);
    }
    logmsg("=== KALIBRASYON BITTI. Dokunuslar kose degilse: rotation degerini 90 artir (0->90->180->270) ve tekrar dene.");
}

/* ------------------------------------------------------------------ */
/* sinyal / temizlik                                                   */
/* ------------------------------------------------------------------ */
static volatile sig_atomic_t g_stop = 0;
static void on_signal(int s) { (void)s; g_stop = 1; }

static void cleanup(int kbd_fd) {
    release_all();
    if (kbd_fd >= 0 && g_grabbed) { int z = 0; ioctl(kbd_fd, EVIOCGRAB, &z); g_grabbed = 0; }
    if (g_touch_fd >= 0) { ioctl(g_touch_fd, UI_DEV_DESTROY); close(g_touch_fd); g_touch_fd = -1; }
    if (g_mouse_fd >= 0) { int zero = 0; ioctl(g_mouse_fd, EVIOCGRAB, &zero); close(g_mouse_fd); g_mouse_fd = -1; }
    if (g_pad_fd >= 0) { ioctl(g_pad_fd, UI_DEV_DESTROY); close(g_pad_fd); g_pad_fd = -1; }
    if (g_key_fd >= 0) { ioctl(g_key_fd, UI_DEV_DESTROY); close(g_key_fd); g_key_fd = -1; }
    if (kbd_fd >= 0) close(kbd_fd);
    unlink(g_statuspath);
    logmsg("kapatildi.");
    if (g_log) fclose(g_log);
}

static void write_status(const char *fmt, ...) {
    char buf[512]; va_list ap; va_start(ap, fmt); vsnprintf(buf, sizeof buf, fmt, ap); va_end(ap);
    FILE *f = fopen(g_statuspath, "w");
    if (f) { fputs(buf, f); fputc('\n', f); fclose(f); }
}

static void usage(const char *argv0) {
    printf(
"kbd2touch v%s — fiziksel klavye -> dokunus / gamepad (root)\n"
"\n"
"Kullanim:\n"
"  %s                          config'i oku ve calis (varsayilan)\n"
"  %s -c <config.ini>          baska config dosyasi\n"
"  %s -p <profil.ini>          ek profil dosyasi yukle\n"
"  %s --list-devices           /dev/input cihazlarini listele ve cik\n"
"  %s --test <X> <Y>           tek dokunus testi yap ve cik\n"
"  %s --calibrate              4 kose kalibrasyon testi ve cik\n"
"  %s --rotation <0|90|180|270> --calibrate\n"
"  %s --version\n"
"\n"
"Kisayollar (config ile degistirilebilir):\n"
"  F9  = eslemeyi ac/kapa   F10 = profil degistir\n"
"  F11 = koseleri kalibre et\n",
VERSION, argv0, argv0, argv0, argv0, argv0, argv0, argv0, argv0);
}

int main(int argc, char **argv) {
    signal(SIGINT, on_signal);
    signal(SIGTERM, on_signal);
    signal(SIGPIPE, SIG_IGN);
    srand((unsigned)time(NULL));

    const char *test_x = NULL; int do_test = 0, do_calib = 0, do_list = 0;
    const char *fg_parse_arg = NULL;
    int do_map = 0, do_check = 0; const char *map_arg = NULL;
    int arg_rotation = -1;
    const char *extra_profile = NULL;

    for (int i = 1; i < argc; i++) {
        if (!strcmp(argv[i], "-c") && i + 1 < argc) g_cfgpath = argv[++i];
        else if (!strcmp(argv[i], "-p") && i + 1 < argc) extra_profile = argv[++i];
        else if (!strcmp(argv[i], "--list-devices")) do_list = 1;
        else if (!strcmp(argv[i], "--calibrate")) do_calib = 1;
        else if (!strcmp(argv[i], "--check")) do_check = 1;
        else if (!strcmp(argv[i], "--map") && i + 2 < argc) {
            do_map = 1;
            static char mb[32]; snprintf(mb, sizeof mb, "%s %s", argv[i+1], argv[i+2]);
            map_arg = mb; i += 2;
        }
        else if (!strcmp(argv[i], "--verbose") || !strcmp(argv[i], "-v")) g_verbose = 1;
        else if (!strcmp(argv[i], "--log") && i + 1 < argc)
            snprintf(g_logpath, sizeof g_logpath, "%s", argv[++i]);
        else if (!strcmp(argv[i], "--home") && i + 1 < argc) {
            snprintf(g_home, sizeof g_home, "%s", argv[++i]);
        }
        else if (!strcmp(argv[i], "--fg-parse") && i + 1 < argc) fg_parse_arg = argv[++i];
        else if (!strcmp(argv[i], "--version")) { printf("kbd2touch %s\n", VERSION); return 0; }
        else if (!strcmp(argv[i], "--rotation") && i + 1 < argc) arg_rotation = atoi(argv[++i]);
        else if (!strcmp(argv[i], "--test") && i + 2 < argc) {
            do_test = 1;
            static char xb[32]; snprintf(xb, sizeof xb, "%s %s", argv[i+1], argv[i+2]);
            test_x = xb; i += 2;
        }
        else if (!strcmp(argv[i], "-h") || !strcmp(argv[i], "--help")) { usage(argv[0]); return 0; }
    }

    if (fg_parse_arg) {
        char out[256];
        if (parse_focus_pkg(fg_parse_arg, out, sizeof out) == 0) printf("%s\n", out);
        else printf("(parse edilemedi)\n");
        return 0;
    }

    {
        const char *env = getenv("KBD2TOUCH_HOME");
        if (env && *env) snprintf(g_home, sizeof g_home, "%s", env);
        if (strcmp(g_home, "/data/adb/kbd2touch") != 0) {
            snprintf(g_logpath, sizeof g_logpath, "%s/log.txt", g_home);
            snprintf(g_statuspath, sizeof g_statuspath, "%s/status.txt", g_home);
            snprintf(g_profiledir, sizeof g_profiledir, "%s/profiles", g_home);
        }
    }
    mkdir(g_home, 0755);
    g_log = fopen(g_logpath, "a");

    if (do_list) { list_devices(); return 0; }

    reload_all();
    if (extra_profile) load_config(extra_profile, 1);
    if (arg_rotation >= 0) g_cfg.rotation = arg_rotation;

    if (do_map && map_arg) {
        int x = 0, y = 0; sscanf(map_arg, "%d %d", &x, &y);
        int tx, ty; map_point(x, y, &tx, &ty);
        printf("ekran(%d,%d) --rotation=%d swap=%d inv=%d/%d--> touch(%d,%d)\n",
               x, y, g_cfg.rotation, g_cfg.swap_xy, g_cfg.invert_x, g_cfg.invert_y, tx, ty);
        return 0;
    }

    if (do_check) {
        printf("Kbd2Touch config kontrolu: %s\n", g_cfgpath);
        printf("  ekran        : %dx%d (touch ekseni %dx%d)\n",
               g_cfg.screen_w, g_cfg.screen_h, g_cfg.touch_max_x + 1, g_cfg.touch_max_y + 1);
        printf("  rotation     : %d  swap_xy=%d invert=%d/%d\n",
               g_cfg.rotation, g_cfg.swap_xy, g_cfg.invert_x, g_cfg.invert_y);
        printf("  grab_keyboard: %d (mod=%d: 0=asla 1=eslenen uygulamada 2=her zaman)\n",
               g_cfg.grab_keyboard, g_cfg.grab_mode);
        printf("  slots: %d   gamepad: %d (0=OTOMATIK: gp profili acilinca olusur)   sanal_klavye: %d\n",
               g_cfg.slot_count, g_cfg.gamepad_enable, g_cfg.virtual_keyboard);
        if (g_mouse_fd < 0) g_mouse_fd = find_mouse();
        printf("  fare       : %s (grab_mouse=%d: eslenen oyun acikken fare de yakalanir)\n",
               g_mouse_fd >= 0 ? g_mouse_path : "yok (BT fare baglaninca otomatik)", g_cfg.grab_mouse);
        printf("  kisayollar   : ac/kapa=%s profil=%s kalibrasyon=%s\n",
               keyname(g_cfg.toggle_key), keyname(g_cfg.profile_key), keyname(g_cfg.calib_key));
        printf("  profiller    : %d\n", g_profile_count);
        for (int i = 0; i < g_profile_count; i++) printf("     - %s\n", g_profile_files[i]);
        printf("  eslesmeler   : %d\n", g_nbinds);
        printf("  %-10s %-10s %-9s %-28s %s\n", "TUS", "UYGULAMA", "EYLEM", "KORDINAT/HEDEF", "ETIKET");
        for (int i = 0; i < g_nbinds; i++) {
            Bind *b = &g_binds[i];
            char det[96] = "";
            if (b->action == ACT_TAP)
                snprintf(det, sizeof det, "(%d,%d)%s", b->x, b->y, b->hold ? " BASILI" : "");
            else if (b->action == ACT_SWIPE)
                snprintf(det, sizeof det, "(%d,%d)->(%d,%d) %dms%s", b->x, b->y, b->x2, b->y2,
                         b->duration_ms > 0 ? b->duration_ms : g_cfg.swipe_ms, b->hold ? " BASILI" : "");
            else if (b->action == ACT_KEY)
                snprintf(det, sizeof det, "tus=%s", keyname(b->out_key > 0 ? b->out_key : b->keycode));
            else if (b->action == ACT_GP_BUTTON) {
                const char *bn = b->gp_button == -100 ? "LT" : b->gp_button == -101 ? "RT" :
                                 b->gp_button == BTN_SOUTH ? "A" : b->gp_button == BTN_EAST ? "B" :
                                 b->gp_button == BTN_WEST ? "X" : b->gp_button == BTN_NORTH ? "Y" :
                                 b->gp_button == BTN_TL ? "LB" : b->gp_button == BTN_TR ? "RB" :
                                 b->gp_button == BTN_START ? "START" : b->gp_button == BTN_SELECT ? "SELECT" :
                                 b->gp_button == BTN_MODE ? "GUIDE" : b->gp_button == BTN_THUMBL ? "THUMBL" :
                                 b->gp_button == BTN_THUMBR ? "THUMBR" : "?";
                snprintf(det, sizeof det, "buton=%s%s", bn, b->hold ? " BASILI" : "");
            } else if (b->action == ACT_GP_STICK)
                snprintf(det, sizeof det, "eksen=%s (%d,%d)",
                         b->gp_axis == 0 ? "LX" : b->gp_axis == 1 ? "LY" :
                         b->gp_axis == 2 ? "RX" : "RY", b->gp_x, b->gp_y);
            printf("  %-10s %-10.10s %-9s %-28s %s%s\n",
                   keyname(b->keycode), b->app[0] ? b->app : "(genel)", actname(b->action), det,
                   b->label, b->manual ? "  [sadece F10]" : "");
            if (b->repeat_ms > 0) printf("  %-10s   (tekrar: her %d ms)\n", "", b->repeat_ms);
        }
        return 0;
    }

    if (do_test && test_x) {
        int x = 0, y = 0; sscanf(test_x, "%d %d", &x, &y);
        if (touch_create() != 0) return 1;
        usleep(400000);
        int tx, ty; map_point(x, y, &tx, &ty);
        touch_down(0, tx, ty);
        printf("dokunus: ekran(%d,%d) -> touch(%d,%d)\n", x, y, tx, ty);
        usleep(300000);
        touch_up(0);
        sleep(1);
        ioctl(g_touch_fd, UI_DEV_DESTROY);
        return 0;
    }

    if (do_calib) {
        if (touch_create() != 0) return 1;
        usleep(500000);
        calibrate();
        sleep(1);
        ioctl(g_touch_fd, UI_DEV_DESTROY);
        return 0;
    }

    if (geteuid() != 0)
        logmsg("UYARI: root degilsin (uid=%d). /dev/uinput ve evdev erisimi icin root gerekir.", geteuid());

    if (touch_create() != 0) return 1;
    if (g_cfg.virtual_keyboard) keydev_create();
    else logmsg("virtual_keyboard = 0 -> sanal klavye cihazi olusturulmadi "
                "(action=key eslesmeleri calismaz)");
    if (g_cfg.gamepad_enable) pad_create();

    g_kbd_fd = find_keyboard();
    int kbd = g_kbd_fd;
    g_mouse_fd = find_mouse();
    if (g_mouse_fd >= 0) logmsg("fare izlemeye alindi: %s", g_mouse_path);
    else logv("fare yok: BT fare oyun sirasinda baglanirsa otomatik yakalanir");
    if (kbd < 0) {
        logmsg("klavye henuz bulunamadi — daemon BEKLEMEDE (2 sn'de bir tekrar arar; "
               "klavyeyi takinca kendiliginden baslar, reboot gerekmez)");
        write_status("klavye bekleniyor");
        for (int tries = 0; !g_stop; tries++) {
            sleep(2);
            g_kbd_fd = find_keyboard();
            if (g_kbd_fd >= 0) { kbd = g_kbd_fd; logmsg("klavye bulundu, bekleyis bitti"); break; }
            if (tries % 30 == 29)
                logmsg("klavye hala yok (%d sn), bekleniyor... ipucu: --list-devices", (tries + 1) * 2);
        }
        if (g_stop || kbd < 0) { cleanup(-1); return 0; }
    }
    g_enabled = g_cfg.enable_on_boot ? 1 : 0;
    logmsg("kbd2touch v%s basladi. enabled=%d auto_profile=%d", VERSION, g_enabled, g_cfg.auto_profile);
    write_status("basladi (enabled=%d)", g_enabled);

    long last_app_check = 0;
    char last_app[128] = "";

    while (!g_stop) {
        struct pollfd pfds[2];
        int nf = 0;
        pfds[nf].fd = kbd; pfds[nf].events = POLLIN; pfds[nf].revents = 0; nf++;
        if (g_mouse_fd >= 0) { pfds[nf].fd = g_mouse_fd; pfds[nf].events = POLLIN; pfds[nf].revents = 0; nf++; }
        poll(pfds, nf, 20);
        long now = ms_now();

        for (int fi = 0; fi < nf; fi++) {
          if (!(pfds[fi].revents & POLLIN)) continue;
          int fd = pfds[fi].fd;
          int is_mouse = (g_mouse_fd >= 0 && fd == g_mouse_fd);
            struct input_event evs[32];
            ssize_t n = read(fd, evs, sizeof evs);
            if (n <= 0) {
                if (is_mouse) {
                    logmsg("fare baglantisi koptu");
                    close(fd); g_mouse_fd = -1; g_mouse_path[0] = 0;
                    continue;
                }
                if (errno == ENODEV || errno == EIO) {
                    logmsg("klavye cikarildi, 2 sn icinde yeniden aranacak");
                    release_all();
                    close(kbd);
                    for (int tries = 0; tries < 600 && !g_stop; tries++) {
                        sleep(2);
                        g_kbd_fd = find_keyboard();
                        if (g_kbd_fd >= 0) {
                            kbd = g_kbd_fd;
                            g_grabbed = 0;
                            set_grab(should_grab());
                            logmsg("klavye yeniden baglandi");
                            break;
                        }
                    }
                    if (kbd < 0) { g_stop = 1; break; }
                    continue;
                }
            } else {
                int cnt = (int)(n / (ssize_t)sizeof(struct input_event));
                for (int i = 0; i < cnt; i++) {
                    struct input_event *e = &evs[i];
                    if (is_mouse) {
                        if (e->type == EV_REL && e->code == REL_WHEEL && e->value != 0) {
                            int wc = e->value > 0 ? KEY_WHEEL_UP : KEY_WHEEL_DOWN;
                            Bind *wb = bind_for_key(wc, g_current_app);
                            if (wb && g_enabled) {
                                wb->pressed = 1; do_action_press(wb);
                                wb->pressed = 0; do_action_release(wb);
                            }
                            continue;
                        }
                        if (e->type != EV_KEY) continue;   /* REL_X/Y hareketi dokunulmaz */
                    } else if (e->type != EV_KEY) continue;
                    int code = e->code;

                    /* ozel kisayollar */
                    if (g_cfg.toggle_key > 0 && code == g_cfg.toggle_key) {
                        if (e->value == 1) {
                            g_enabled = !g_enabled;
                            if (!g_enabled) release_all();
                            logmsg("esleme %s", g_enabled ? "ACIK" : "KAPALI");
                            set_grab(should_grab());
                            write_status("enabled=%d grab=%d", g_enabled, g_grabbed);
                        }
                        continue;
                    }
                    if (g_cfg.profile_key > 0 && code == g_cfg.profile_key && e->value == 1) {
                        if (g_profile_count > 0) {
                            /* dongu: otomatik -> profil1 -> profil2 -> ... -> otomatik */
                            g_profile_idx++;
                            if (g_profile_idx > g_profile_count) g_profile_idx = 0;
                            release_all();
                            if (g_profile_idx == 0) {
                                g_active_profile[0] = 0;
                                logmsg("profil: OTOMATIK (on plandaki uygulamaya gore)");
                                ensure_pad_for_context();
                                set_grab(should_grab());
                                write_status("profil=auto enabled=%d grab=%d", g_enabled, g_grabbed);
                            } else {
                                const char *f = g_profile_files[g_profile_idx - 1];
                                const char *b = strrchr(f, '/'); b = b ? b + 1 : f;
                                char nm[64]; snprintf(nm, sizeof nm, "%s", b);
                                char *dot = strrchr(nm, '.'); if (dot) *dot = 0;
                                snprintf(g_active_profile, sizeof g_active_profile, "%s", nm);
                                logmsg("profil: %s (elle secildi)", nm);
                                ensure_pad_for_context();
                                set_grab(should_grab());
                                write_status("profil=%s enabled=%d grab=%d", nm, g_enabled, g_grabbed);
                            }
                        } else {
                            logmsg("profil yok: %s bos", g_profiledir);
                        }
                        continue;
                    }
                    if (g_cfg.calib_key > 0 && code == g_cfg.calib_key && e->value == 1) {
                        calibrate();
                        continue;
                    }
                    if (g_cfg.quit_key > 0 && code == g_cfg.quit_key && e->value == 1) {
                        logmsg("quit_key basildi, cikiliyor");
                        g_stop = 1; break;
                    }

                    Bind *b = bind_for_key(code, g_current_app);
                    if (!b) continue;

                    if (e->value == 1 && !b->pressed) {
                        b->pressed = 1;
                        b->repeat_last_ms = now;
                        do_action_press(b);
                    } else if ((e->value == 0 || e->value == 2) && b->pressed) {
                        if (e->value == 2) continue;   /* otomatik tekrar */
                        b->pressed = 0;
                        b->repeat_last_ms = 0;
                        do_action_release(b);
                    }
                }
            }
        }

        update_swipes();
        update_pending();
        update_repeats();

        if (g_cfg.auto_profile && now - last_app_check >= g_cfg.auto_profile_ms) {
            last_app_check = now;
            char app[128];
            if (get_foreground_app(app, sizeof app) == 0 && strcmp(app, last_app) != 0) {
                snprintf(last_app, sizeof last_app, "%s", app);
                snprintf(g_current_app, sizeof g_current_app, "%s", app);
                logmsg("on plandaki uygulama: %s", app);
                if (g_mouse_fd < 0) {
                    g_mouse_fd = find_mouse();
                    if (g_mouse_fd >= 0 && g_grabbed && g_cfg.grab_mouse) {
                        int one = 1;
                        ioctl(g_mouse_fd, EVIOCGRAB, &one);
                    }
                }
                ensure_pad_for_context();
                set_grab(should_grab());
                write_status("enabled=%d grab=%d app=%s", g_enabled, g_grabbed, app);
            }
        }
    }

    cleanup(kbd);
    return 0;
}
