// ─────────────────────────────────────────────────────────────────────────────
//  kitsugi_crash_handler.cpp
//
//  NEDEN VAR?
//  Java `Thread.setDefaultUncaughtExceptionHandler` YALNIZCA yönetilen Java/Kotlin
//  istisnalarında çalışır. Native kütüphanelerde (MPV/FFmpeg/MediaCodec/GPU-Skia/libdovi,
//  TorrServer, ASS renderer...) oluşan çökmeler SIGSEGV/SIGABRT/SIGBUS sinyali üretir;
//  bu sinyal Java katmanına HİÇ ulaşmaz. Sonuç: uygulama "pat diye" kapanır, hiçbir
//  çökme raporu üretilmez — tam olarak kullanıcının yaşadığı durum.
//
//  Bu dosya, o sinyalleri yakalayıp `filesDir/native_crash.txt` dosyasına
//  async-signal-safe biçimde (malloc/lock YOK, sadece open/write/close) ham geri iz yazar.
//  Ardından sinyali yeniden yükseltir → sistem normal davranışına devam eder (tombstone
//  üretilir, süreç doğru sinyalle ölür).
// ─────────────────────────────────────────────────────────────────────────────

#include <jni.h>

#include <android/log.h>
#include <fcntl.h>
#include <signal.h>
#include <string.h>
#include <sys/prctl.h>
#include <sys/syscall.h>
#include <time.h>
#include <unistd.h>
#include <unwind.h>

#define LOG_TAG "KitsugiNativeCrash"
#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace {

constexpr int kMaxFrames = 48;
constexpr size_t kDirMax = 480;

char g_dir[kDirMax + 1] = {0};          // uygulamanın filesDir yolu
char g_process_name[64] = {0};
volatile sig_atomic_t g_installed = 0;
volatile sig_atomic_t g_in_handler = 0;

// Yığın taşması (stack overflow) kaynaklı SIGSEGV'de normal yığın kullanılamaz.
// Bu yüzden yedek (alt) yığın ayırıyoruz — statik, malloc yok.
char g_alt_stack[128 * 1024];
stack_t g_alt_stack_desc;

// ── Async-signal-safe string/rakam yardımcıları (malloc ve std:: yok) ────────

void append_str(char* dst, size_t cap, size_t* pos, const char* s) {
    if (s == nullptr) return;
    while (*s != '\0' && *pos + 1 < cap) {
        dst[(*pos)++] = *s++;
    }
    dst[*pos] = '\0';
}

void append_ulong(char* dst, size_t cap, size_t* pos, unsigned long long value) {
    char tmp[24];
    int n = 0;
    if (value == 0) {
        tmp[n++] = '0';
    } else {
        while (value > 0 && n < (int)sizeof(tmp)) {
            tmp[n++] = static_cast<char>('0' + (value % 10));
            value /= 10;
        }
    }
    while (n > 0 && *pos + 1 < cap) {
        dst[(*pos)++] = tmp[--n];
    }
    dst[*pos] = '\0';
}

void append_hex(char* dst, size_t cap, size_t* pos, unsigned long long value) {
    static const char* digits = "0123456789abcdef";
    char tmp[20];
    int n = 0;
    if (value == 0) {
        tmp[n++] = '0';
    } else {
        while (value > 0 && n < (int)sizeof(tmp)) {
            tmp[n++] = digits[value & 0xF];
            value >>= 4;
        }
    }
    while (n > 0 && *pos + 1 < cap) {
        dst[(*pos)++] = tmp[--n];
    }
    dst[*pos] = '\0';
}

void write_all(int fd, const char* data, size_t len) {
    size_t written = 0;
    while (written < len) {
        ssize_t n = write(fd, data + written, len - written);
        if (n <= 0) return;
        written += static_cast<size_t>(n);
    }
}

const char* signal_name(int sig) {
    switch (sig) {
        case SIGSEGV: return "SIGSEGV";
        case SIGABRT: return "SIGABRT";
        case SIGBUS:  return "SIGBUS";
        case SIGILL:  return "SIGILL";
        case SIGFPE:  return "SIGFPE";
        case SIGTRAP: return "SIGTRAP";
        default:      return "SIGNAL";
    }
}

struct BacktraceState {
    void** current;
    void** end;
};

static _Unwind_Reason_Code UnwindCallback(struct _Unwind_Context* context, void* arg) {
    auto* state = static_cast<BacktraceState*>(arg);
    uintptr_t pc = _Unwind_GetIP(context);
    if (pc) {
        if (state->current == state->end) {
            return _URC_END_OF_STACK;
        }
        *state->current++ = reinterpret_cast<void*>(pc);
    }
    return _URC_NO_REASON;
}

static size_t CaptureBacktrace(void** buffer, size_t max_frames) {
    BacktraceState state = {buffer, buffer + max_frames};
    _Unwind_Backtrace(UnwindCallback, &state);
    return state.current - buffer;
}

// ── Rapor yazımı (async-signal-safe) ────────────────────────────────────────

void write_native_report(int sig, siginfo_t* info) {
    if (g_dir[0] == '\0') return;

    char path[kDirMax + 24];
    size_t p = 0;
    append_str(path, sizeof(path), &p, g_dir);
    append_str(path, sizeof(path), &p, "/native_crash.txt");

    int fd = open(path, O_WRONLY | O_CREAT | O_APPEND, 0600);
    if (fd < 0) return;

    char buf[1536];
    size_t b = 0;

    append_str(buf, sizeof(buf), &b, "\n=== KITSUGI NATIVE CRASH ===\nepoch=");
    append_ulong(buf, sizeof(buf), &b, static_cast<unsigned long long>(time(nullptr)));
    append_str(buf, sizeof(buf), &b, "\nsignal=");
    append_str(buf, sizeof(buf), &b, signal_name(sig));
    append_str(buf, sizeof(buf), &b, " (");
    append_ulong(buf, sizeof(buf), &b, static_cast<unsigned long long>(sig));
    append_str(buf, sizeof(buf), &b, ")\nsi_code=");
    if (info != nullptr) {
        append_ulong(buf, sizeof(buf), &b, static_cast<unsigned long long>(info->si_code));
        append_str(buf, sizeof(buf), &b, "\nfault_addr=0x");
        append_hex(buf, sizeof(buf), &b, reinterpret_cast<unsigned long long>(info->si_addr));
    } else {
        append_str(buf, sizeof(buf), &b, "?");
    }
    append_str(buf, sizeof(buf), &b, "\npid=");
    append_ulong(buf, sizeof(buf), &b, static_cast<unsigned long long>(getpid()));
    append_str(buf, sizeof(buf), &b, "\ntid=");
    append_ulong(buf, sizeof(buf), &b, static_cast<unsigned long long>(syscall(__NR_gettid)));
    append_str(buf, sizeof(buf), &b, "\nprocess=");
    append_str(buf, sizeof(buf), &b, g_process_name[0] != '\0' ? g_process_name : "?");

    char thread_name[32];
    thread_name[0] = '\0';
    if (prctl(PR_GET_NAME, thread_name, 0, 0, 0) == 0) {
        append_str(buf, sizeof(buf), &b, "\nthread=");
        append_str(buf, sizeof(buf), &b, thread_name);
    }
    append_str(buf, sizeof(buf), &b, "\nframes:\n");
    write_all(fd, buf, b);

    // Ham geri iz (adresler). Sembol çözümü logcat/tombstone tarafında yapılır —
    // burada sembolleştirme (symbolization) YAPILMAZ: malloc/lock riski vardır.
    void* frames[kMaxFrames];
    int count = static_cast<int>(CaptureBacktrace(frames, kMaxFrames));

    char line[64];
    for (int i = 0; i < count; ++i) {
        size_t l = 0;
        append_str(line, sizeof(line), &l, "  #");
        append_ulong(line, sizeof(line), &l, static_cast<unsigned long long>(i));
        append_str(line, sizeof(line), &l, "  0x");
        append_hex(line, sizeof(line), &l, reinterpret_cast<unsigned long long>(frames[i]));
        append_str(line, sizeof(line), &l, "\n");
        write_all(fd, line, l);
    }

    const char* footer = "=== END NATIVE CRASH ===\n";
    write_all(fd, footer, strlen(footer));

    fsync(fd);
    close(fd);
}

void crash_signal_handler(int sig, siginfo_t* info, void* /*ucontext*/) {
    // İç içe çökme koruması: handler içinde tekrar sinyal gelirse rapor yazma.
    if (g_in_handler) {
        signal(sig, SIG_DFL);
        kill(getpid(), sig);
        return;
    }
    g_in_handler = 1;

    write_native_report(sig, info);

    g_in_handler = 0;

    // SA_RESETHAND sayesinde bu sinyalin davranışı artık SIG_DFL'dir.
    // Sinyali yeniden yükselt: süreç doğru sinyalle ölsün, tombstone üretilsin.
    signal(sig, SIG_DFL);
    kill(getpid(), sig);
}

void install_handlers() {
    if (g_installed) return;
    g_installed = 1;

    if (prctl(PR_GET_NAME, g_process_name, 0, 0, 0) != 0) {
        g_process_name[0] = '\0';
    }

    g_alt_stack_desc.ss_sp = g_alt_stack;
    g_alt_stack_desc.ss_size = sizeof(g_alt_stack);
    g_alt_stack_desc.ss_flags = 0;
    sigaltstack(&g_alt_stack_desc, nullptr);

    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sa.sa_sigaction = crash_signal_handler;
    sa.sa_flags = SA_SIGINFO | SA_ONSTACK | SA_RESETHAND;
    sigemptyset(&sa.sa_mask);

    const int signals[] = {SIGSEGV, SIGABRT, SIGBUS, SIGILL, SIGFPE, SIGTRAP};
    for (int sig : signals) {
        sigaction(sig, &sa, nullptr);
    }

    ALOGI("Native crash handler installed.");
}

}  // namespace

extern "C" JNIEXPORT void JNICALL
Java_com_kitsugi_animelist_core_diagnostics_NativeCrashBridge_nativeInstall(
    JNIEnv* env, jclass /* clazz */, jstring filesDir) {
    if (filesDir != nullptr) {
        const char* dir = env->GetStringUTFChars(filesDir, nullptr);
        if (dir != nullptr) {
            strncpy(g_dir, dir, kDirMax);
            g_dir[kDirMax] = '\0';
            env->ReleaseStringUTFChars(filesDir, dir);
        }
    }
    install_handlers();
}
