/*
 * ph4bench.c — PocketShell Phase 4 (Terminal/PTY excellence) benchmark harness.
 *
 * ISOLATED INVESTIGATION TOOLING (Agent Z, Phase 4 audit). Not part of the
 * app build; compiled on-device inside the Alpine guest:
 *
 *     gcc -O2 -o ph4bench ph4bench.c
 *
 * Subcommands:
 *   exec <N> <argv...>     fork/execvp/waitpid loop; wall + reaped-subtree-CPU
 *                          per exec (min/median/p95/max). RUSAGE_CHILDREN
 *                          covers the whole reaped subtree, so a nested proot
 *                          tracer's CPU is included automatically.
 *   fs <N> <path>          stat() loop (proot path-translation cost).
 *   ptylat <N> [argv...]   PTY round trips: (a) raw byte echo RTT (cat),
 *                          (b) login-shell "echo" round trips, (c) fork→
 *                          first-output of the shell. Default argv: /bin/sh -l
 *   ptytp <MiB> [--rev]    PTY throughput, one dd exec, 1 MiB blocks. Default:
 *                          child writes through the slave, parent drains the
 *                          master. --rev: parent writes, child drains.
 *   session [argv...]      App-style spawn replication (termux.c chain:
 *                          ptmx fork/setsid/dup2/exec) — fork→first-output
 *                          and fork→exit. Default: /bin/sh -l -c 'echo READY'
 *   sigtest [-- argv...]   PTY correctness battery against the given shell
 *                          (default "/bin/sh -li"): Ctrl-C, Ctrl-Z, resize,
 *                          UTF-8, 8-bit raw passthrough, EOF, and — the
 *                          Phase-5-critical one — what survives when the
 *                          direct child is SIGKILLed and the master closed
 *                          (closeSession semantics, including a setsid'd
 *                          "server" grandchild and, when argv is a nested
 *                          proot chain, tracer-death semantics).
 *
 * All timing is CLOCK_MONOTONIC. Everything runs inside whatever environment
 * invokes it — run at proot layer 1 (normal guest session) and again under a
 * nested proot to attribute per-layer cost (see README.md).
 */
#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/resource.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <termios.h>
#include <time.h>
#include <unistd.h>

static double now_ms(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return ts.tv_sec * 1000.0 + ts.tv_nsec / 1e6;
}

static int cmp_double(const void *a, const void *b) {
    double x = *(const double *)a, y = *(const double *)b;
    return (x > y) - (x < y);
}

static int idx95(int n) { int i = (int)(n * 0.95); return i >= n ? n - 1 : i; }

static void stats(const char *label, double *v, int n, double cpu_ms) {
    qsort(v, n, sizeof(double), cmp_double);
    double sum = 0;
    for (int i = 0; i < n; i++) sum += v[i];
    printf("%-36s n=%d mean=%.3f median=%.3f p95=%.3f min=%.3f max=%.3f (ms)",
           label, n, sum / n, v[n / 2], v[idx95(n)], v[0], v[n - 1]);
    if (cpu_ms >= 0) printf("  subtree-cpu/exec=%.3fms", cpu_ms);
    printf("\n");
}

/* ---------------- exec ---------------- */

static int bench_exec(int n, char **cmd) {
    double *lat = malloc(sizeof(double) * n);
    for (int w = 0; w < 10; w++) {
        pid_t p = fork();
        if (p == 0) { execvp(cmd[0], cmd); _exit(127); }
        int st; waitpid(p, &st, 0);
        if (!WIFEXITED(st) || WEXITSTATUS(st) != 0) {
            fprintf(stderr, "exec %s: abnormal warmup exit (0x%x)\n", cmd[0], st);
            return 1;
        }
    }
    struct rusage before, after;
    getrusage(RUSAGE_CHILDREN, &before);
    for (int i = 0; i < n; i++) {
        double t0 = now_ms();
        pid_t p = fork();
        if (p == 0) { execvp(cmd[0], cmd); _exit(127); }
        int st; waitpid(p, &st, 0);
        lat[i] = now_ms() - t0;
        if (!WIFEXITED(st) || WEXITSTATUS(st) != 0) {
            fprintf(stderr, "exec %s: abnormal exit (0x%x)\n", cmd[0], st);
            return 1;
        }
    }
    getrusage(RUSAGE_CHILDREN, &after);
    double cpu = ((after.ru_utime.tv_sec - before.ru_utime.tv_sec) * 1000.0 +
                  (after.ru_utime.tv_usec - before.ru_utime.tv_usec) / 1000.0 +
                  (after.ru_stime.tv_sec - before.ru_stime.tv_sec) * 1000.0 +
                  (after.ru_stime.tv_usec - before.ru_stime.tv_usec) / 1000.0);
    stats(cmd[0], lat, n, cpu / n);
    free(lat);
    return 0;
}

/* ---------------- fs ---------------- */

static int bench_fs(int n, const char *path) {
    double *lat = malloc(sizeof(double) * n);
    struct stat sb;
    for (int w = 0; w < 100; w++)
        if (stat(path, &sb) != 0) { perror("stat warmup"); return 1; }
    for (int i = 0; i < n; i++) {
        double t0 = now_ms();
        if (stat(path, &sb) != 0) { perror("stat"); return 1; }
        lat[i] = now_ms() - t0;
    }
    stats(path, lat, n, -1);
    free(lat);
    return 0;
}

/* ------- shared PTY spawn (mirrors termux.c create_subprocess) ------- */

static pid_t pty_spawn(int master, const char *cmd, char *const argv[]) {
    char devname[64];
    if (grantpt(master) || unlockpt(master) || ptsname_r(master, devname, sizeof devname))
        return -1;
    pid_t pid = fork();
    if (pid < 0) return -1;
    if (pid > 0) return pid;
    sigset_t all;
    sigfillset(&all);
    sigprocmask(SIG_UNBLOCK, &all, NULL);
    setsid();
    int slave = open(devname, O_RDWR);
    if (slave < 0) _exit(126);
    dup2(slave, 0); dup2(slave, 1); dup2(slave, 2);
    if (slave > 2) close(slave);
    close(master);
    execvp(cmd, argv);
    perror("execvp");
    _exit(127);
}

static void set_nonblock(int fd) {
    int fl = fcntl(fd, F_GETFL, 0);
    fcntl(fd, F_SETFL, fl | O_NONBLOCK);
}

/* Wait up to timeout_ms for the fd to become readable, then read everything
 * currently available. Returns bytes seen (0 on timeout/EIO). */
static ssize_t wait_first_output(int m, double timeout_ms) {
    set_nonblock(m);
    double deadline = now_ms() + timeout_ms;
    ssize_t total = 0;
    while (now_ms() < deadline) {
        struct pollfd pf = { .fd = m, .events = POLLIN };
        int pr = poll(&pf, 1, 50);
        if (pr <= 0) continue;
        char buf[8192];
        ssize_t r;
        while ((r = read(m, buf, sizeof buf)) > 0) total += r;
        if (total > 0) return total;
        if (r < 0 && errno == EIO) return 0;
    }
    return total;
}

/* One non-blocking sweep of whatever is buffered right now. */
static void drain_now(int m) {
    char buf[8192];
    set_nonblock(m);
    while (read(m, buf, sizeof buf) > 0) {}
}

/* Read everything that arrives within window_ms into buf (NUL-terminated).
 * Returns total bytes captured. Used when content AFTER a token matters. */
static ssize_t capture(int m, char *buf, size_t bufsz, double window_ms) {
    size_t total = 0;
    double deadline = now_ms() + window_ms;
    while (total + 1 < bufsz && now_ms() < deadline) {
        ssize_t r = read(m, buf + total, bufsz - 1 - total);
        if (r > 0) total += (size_t)r;
        else if (r < 0 && errno == EIO) break;
        else poll(NULL, 0, 10);
    }
    buf[total] = 0;
    return (ssize_t)total;
}

/* read until marker appears; 1 found / 0 timeout; fd must be non-blocking */
static int read_until(int m, const char *marker, double timeout_ms) {
    char buf[8192];
    size_t seen = 0, len = strlen(marker);
    double deadline = now_ms() + timeout_ms;
    while (now_ms() < deadline) {
        ssize_t r = read(m, buf, sizeof buf);
        if (r > 0) {
            for (ssize_t k = 0; k < r; k++) {
                if (buf[k] == marker[seen]) {
                    if (++seen == len) return 1;
                } else {
                    seen = (buf[k] == marker[0]) ? 1 : 0;
                }
            }
        } else if (r == 0 || (r < 0 && errno == EIO)) return 0;
        else if (r < 0 && errno != EAGAIN) return -1;
    }
    return 0;
}

static int reap_deadline(pid_t pid, double timeout_ms) {
    double deadline = now_ms() + timeout_ms;
    int st = 0;
    while (now_ms() < deadline) {
        pid_t w = waitpid(pid, &st, WNOHANG);
        if (w == pid) return WIFEXITED(st) ? WEXITSTATUS(st) : -WTERMSIG(st);
        usleep(5000);
    }
    return 0x7fffffff; /* still alive */
}

/* ---------------- ptylat ---------------- */

static int ptylat(int n, char **shell_argv) {
    double *rtt_raw = malloc(sizeof(double) * n);
    double *rtt_sh = malloc(sizeof(double) * n);
    struct winsize ws = { .ws_row = 40, .ws_col = 120, .ws_xpixel = 0, .ws_ypixel = 0 };

    /* (a) raw byte echo via cat */
    {
        int m = posix_openpt(O_RDWR | O_NOCTTY);
        ioctl(m, TIOCSWINSZ, &ws);
        char *cat_argv[] = { "cat", NULL };
        pid_t p = pty_spawn(m, "cat", cat_argv);
        struct termios t;
        tcgetattr(m, &t);
        cfmakeraw(&t);
        tcsetattr(m, TCSANOW, &t);
        usleep(100 * 1000);
        set_nonblock(m);
        char ob = 'x', ib;
        for (int i = 0; i < n; i++) {
            double t0 = now_ms();
            if (write(m, &ob, 1) != 1) { perror("write"); return 1; }
            double deadline = t0 + 5000;
            while (read(m, &ib, 1) <= 0) {
                if (now_ms() > deadline) { fprintf(stderr, "cat echo timeout\n"); return 1; }
                if (errno == EIO) { fprintf(stderr, "cat died\n"); return 1; }
                if (errno != EAGAIN) { perror("read"); return 1; }
            }
            rtt_raw[i] = now_ms() - t0;
        }
        kill(p, SIGKILL); waitpid(p, NULL, 0);
        close(m);
    }
    /* (b)+(c) login shell */
    {
        int m = posix_openpt(O_RDWR | O_NOCTTY);
        ioctl(m, TIOCSWINSZ, &ws);
        double t0 = now_ms();
        pid_t p = pty_spawn(m, shell_argv[0], shell_argv);
        ssize_t first = wait_first_output(m, 30000);
        double first_out = now_ms() - t0;
        printf("login-shell (%s) fork→first-output: %.3f ms (%zd bytes)\n",
               shell_argv[0], first_out, first);
        if (first == 0) { fprintf(stderr, "no output from shell in 30s\n"); return 1; }
        write(m, "stty -echo\n", 11);
        usleep(300 * 1000);
        drain_now(m);
        for (int i = 0; i < n; i++) {
            char cmd[64], marker[32];
            snprintf(cmd, sizeof cmd, "echo __rt%d_%d\n", (int)getpid(), i);
            snprintf(marker, sizeof marker, "__rt%d_%d", (int)getpid(), i);
            double t1 = now_ms();
            if (write(m, cmd, strlen(cmd)) != (ssize_t)strlen(cmd)) { perror("write"); return 1; }
            if (read_until(m, marker, 10000) != 1) {
                fprintf(stderr, "round trip %d: marker never seen\n", i);
                return 1;
            }
            rtt_sh[i] = now_ms() - t1;
        }
        stats("pty raw byte RTT (cat)", rtt_raw, n, -1);
        stats("login-shell echo RTT", rtt_sh, n, -1);
        /* external-command round trips (fork+exec of /bin/true per trip) */
        double *rtt_ext = malloc(sizeof(double) * n);
        for (int i = 0; i < n; i++) {
            char cmd[80], marker[32];
            snprintf(cmd, sizeof cmd, "/bin/true; echo __ex%d_%d\n", (int)getpid(), i);
            snprintf(marker, sizeof marker, "__ex%d_%d", (int)getpid(), i);
            double t1 = now_ms();
            if (write(m, cmd, strlen(cmd)) != (ssize_t)strlen(cmd)) { perror("write"); return 1; }
            if (read_until(m, marker, 10000) != 1) {
                fprintf(stderr, "ext round trip %d: marker never seen\n", i);
                return 1;
            }
            rtt_ext[i] = now_ms() - t1;
        }
        stats("login-shell ext-cmd RTT (/bin/true)", rtt_ext, n, -1);
        free(rtt_ext);
        kill(p, SIGKILL); waitpid(p, NULL, 0);
        close(m);
    }
    free(rtt_raw); free(rtt_sh);
    return 0;
}

/* ---------------- ptytp ---------------- */

static int ptytp(int mib, int rev) {
    int m = posix_openpt(O_RDWR | O_NOCTTY);
    struct winsize ws = { .ws_row = 40, .ws_col = 120, .ws_xpixel = 0, .ws_ypixel = 0 };
    ioctl(m, TIOCSWINSZ, &ws);
    struct termios t;
    tcgetattr(m, &t);
    cfmakeraw(&t);
    tcsetattr(m, TCSANOW, &t);
    set_nonblock(m);
    char cnt[32];
    snprintf(cnt, sizeof cnt, "%d", mib);
    setenv("PH4_MIB", cnt, 1);
    pid_t p;
    if (!rev) {
        char *sh[] = { "sh", "-c", "dd if=/dev/zero bs=1048576 count=$PH4_MIB 2>/dev/null", NULL };
        p = pty_spawn(m, sh[0], sh);
    } else {
        char *sh[] = { "sh", "-c", "dd of=/dev/null bs=1048576 count=$PH4_MIB 2>/dev/null", NULL };
        p = pty_spawn(m, sh[0], sh);
    }
    if (p < 0) { perror("pty_spawn"); return 1; }
    double t0 = now_ms();
    size_t moved = 0, total_bytes = (size_t)mib * 1048576;
    char buf[65536];
    memset(buf, 0x41, sizeof buf);
    double deadline = t0 + 120000;
    if (!rev) {
        while (moved < total_bytes && now_ms() < deadline) {
            ssize_t r = read(m, buf, sizeof buf);
            if (r > 0) moved += (size_t)r;
            else if (r < 0 && (errno == EAGAIN || errno == EINTR)) poll(NULL, 0, 1);
            else break; /* EIO: child gone */
        }
    } else {
        while (moved < total_bytes && now_ms() < deadline) {
            size_t want = total_bytes - moved > sizeof buf ? sizeof buf : total_bytes - moved;
            ssize_t w = write(m, buf, want);
            if (w > 0) moved += (size_t)w;
            else if (w < 0 && (errno == EAGAIN || errno == EINTR)) poll(NULL, 0, 1);
            else break;
        }
        int st;
        double dl = now_ms() + 60000;
        while (now_ms() < dl && waitpid(p, &st, WNOHANG) == 0) poll(NULL, 0, 5);
        waitpid(p, &st, WNOHANG);
        p = 0; /* reaped */
    }
    double dt = now_ms() - t0;
    if (p) { kill(p, SIGKILL); waitpid(p, NULL, 0); }
    close(m);
    printf("%s: %.2f MiB moved in %.1f ms = %.2f MiB/s%s\n",
           rev ? "input  (master→tty→guest)" : "output (guest→tty→master)",
           (double)moved / 1048576.0, dt, (double)moved / 1048576.0 / (dt / 1000.0),
           moved == total_bytes ? "" : "  [INCOMPLETE]");
    return moved == total_bytes ? 0 : 1;
}

/* ---------------- session spawn replication ---------------- */

static int bench_session(int argc, char **cmd) {
    char *def[] = { "/bin/sh", "-l", "-c", "echo READY", NULL };
    char **chain = argc > 0 ? cmd : def;
    double t_first[7], t_exit[7];
    int runs = 7;
    for (int i = 0; i < runs; i++) {
        int m = posix_openpt(O_RDWR | O_NOCTTY);
        struct winsize ws = { .ws_row = 40, .ws_col = 120, .ws_xpixel = 0, .ws_ypixel = 0 };
        ioctl(m, TIOCSWINSZ, &ws);
        double t0 = now_ms();
        pid_t p = pty_spawn(m, chain[0], chain);
        ssize_t first = wait_first_output(m, 30000);
        t_first[i] = now_ms() - t0;
        if (first == 0) fprintf(stderr, "(warning: no output observed)\n");
        int st;
        waitpid(p, &st, 0);
        t_exit[i] = now_ms() - t0;
        close(m);
    }
    stats("spawn fork→first-output", t_first, runs, -1);
    stats("spawn fork→exit", t_exit, runs, -1);
    return 0;
}

/* fork→first-output only, then SIGKILL the child (for chains that would
 * wait at a prompt — the user-facing "new tab until prompt" cost). */
static int bench_sessionfo(int argc, char **cmd) {
    char *def[] = { "/bin/sh", "-l", "-c", "echo READY", NULL };
    char **chain = argc > 0 ? cmd : def;
    double t_first[7];
    int runs = 7;
    for (int i = 0; i < runs; i++) {
        int m = posix_openpt(O_RDWR | O_NOCTTY);
        struct winsize ws = { .ws_row = 40, .ws_col = 120, .ws_xpixel = 0, .ws_ypixel = 0 };
        ioctl(m, TIOCSWINSZ, &ws);
        double t0 = now_ms();
        pid_t p = pty_spawn(m, chain[0], chain);
        ssize_t first = wait_first_output(m, 60000);
        t_first[i] = now_ms() - t0;
        if (first == 0) fprintf(stderr, "(warning: no output observed)\n");
        kill(p, SIGKILL);
        waitpid(p, NULL, 0);
        close(m);
    }
    stats("spawn fork→first-output (killed)", t_first, runs, -1);
    return 0;
}

static void nap(int ms); /* defined after sigtest — declared here for fresh_shell */

/* ---------------- sigtest ---------------- */

static int proc_state(pid_t pid) {
    char path[64], buf[1024];
    snprintf(path, sizeof path, "/proc/%d/stat", pid);
    int fd = open(path, O_RDONLY);
    if (fd < 0) return '-';
    ssize_t n = read(fd, buf, sizeof buf - 1);
    close(fd);
    if (n <= 0) return '-';
    buf[n] = 0;
    char *rp = strrchr(buf, ')');
    if (!rp) return '-';
    return rp[2];
}

static void report(int *failures, int ok, const char *name, const char *detail) {
    printf("%-4s %-56s %s\n", ok ? "PASS" : "FAIL", name, detail ? detail : "");
    if (!ok) (*failures)++;
}

/* One shell-on-a-pty instance per test — tests never share termios state
 * (busybox ash restores its startup termios when it regains the foreground
 * after a job dies, so cross-test sharing produced order-dependent results). */
struct shctx { int m; pid_t p; };

static int fresh_shell(struct shctx *s, char **sh) {
    s->m = posix_openpt(O_RDWR | O_NOCTTY);
    if (s->m < 0) return -1;
    struct winsize ws = { .ws_row = 40, .ws_col = 120, .ws_xpixel = 0, .ws_ypixel = 0 };
    ioctl(s->m, TIOCSWINSZ, &ws);
    struct termios t;
    tcgetattr(s->m, &t);
    t.c_iflag |= IUTF8;               /* match the app's termios (termux.c) */
    t.c_iflag &= ~(IXON | IXOFF);
    tcsetattr(s->m, TCSANOW, &t);
    s->p = pty_spawn(s->m, sh[0], sh);
    if (s->p < 0) { close(s->m); s->m = -1; return -1; }
    wait_first_output(s->m, 30000);
    write(s->m, "stty -echo 2>/dev/null\n", 23);
    nap(300);
    drain_now(s->m);
    return 0;
}

static void kill_shell(struct shctx *s) {
    if (s->p > 0) { kill(s->p, SIGKILL); waitpid(s->p, NULL, 0); }
    if (s->m >= 0) close(s->m);
    s->p = 0; s->m = -1;
}

static int sigtest(int argc, char **shell) {
    int failures = 0;
    char *def[] = { "/bin/sh", "-li", NULL };
    char **sh = argc > 0 ? shell : def;
    struct shctx s;
    char cap[1024];

    /* 1. Ctrl-C: INTR char interrupts the foreground job; shell survives.
       (An interactive shell may discard the rest of the interrupted line, so
       the evidence is the sleep process actually dying + a live shell.)
       HARNESS CONSTRAINT: after writing the INTR byte, the parent must keep
       issuing syscalls continuously (tight read loop) — sleeping across the
       INTR window desyncs the outer proot tracer and the harness dies with
       SIGTRAP into the resident loader breakpoint (loader+0x1bb4, SM-F711B;
       documented in the Phase 4 report). Sleeping BEFORE the INTR and AFTER
       the foreground job died is safe (observed over repeated runs). */
    if (fresh_shell(&s, sh) == 0) {
        write(s.m, "sleep 30; echo INTDONE\n", 23);
        nap(500); /* let sleep become the fg job */
        double t0 = now_ms();
        unsigned char intr = 0x03;
        write(s.m, &intr, 1);
        /* ash aborts the remainder of an interrupted line, so the latency
           signal is a FRESH command accepted at the prompt afterwards. Keep
           the read loop tight (no sleeps) across the whole INTR window. */
        read_until(s.m, "INTDONE", 2500);
        write(s.m, "echo CTRLC_OK\n", 14);
        read_until(s.m, "CTRLC_OK", 5000);
        double dt = now_ms() - t0;
        nap(600);
        write(s.m, "echo ALIVE$((13*7))_SLEEP=$(pgrep -c -f 'sleep 30' 2>/dev/null || echo 0)\n", 75);
        memset(cap, 0, sizeof cap);
        capture(s.m, cap, sizeof cap - 1, 1200);
        int shell_ok = strstr(cap, "ALIVE91") != NULL;
        int sleep_gone = strstr(cap, "_SLEEP=0") != NULL;
        char det[96];
        snprintf(det, sizeof det, "sleep gone=%c shell alive=%c (responsive in %.0f ms)",
                 sleep_gone ? 'y' : 'n', shell_ok ? 'y' : 'n', dt);
        report(&failures, shell_ok && sleep_gone, "Ctrl-C (ISIG) kills fg job, shell survives", det);
        kill_shell(&s);
    }

    /* 2. Ctrl-Z: SUSP char stops the foreground job */
    if (fresh_shell(&s, sh) == 0) {
        write(s.m, "sleep 30\n", 9);
        nap(500);
        unsigned char susp = 0x1A;
        write(s.m, &susp, 1);
        nap(800);
        write(s.m, "jobs\n", 5);
        memset(cap, 0, sizeof cap);
        capture(s.m, cap, sizeof cap - 1, 1500);
        int stopped = strstr(cap, "Stopped") != NULL;
        char det[256];
        snprintf(det, sizeof det, "%s", stopped ? "jobs(1) shows Stopped" : cap);
        report(&failures, stopped, "Ctrl-Z (SIGTSTP) stops foreground job", det);
        write(s.m, "kill %1 2>/dev/null\n", 20);
        nap(200);
        kill_shell(&s);
    }

    /* 3. resize: TIOCSWINSZ must reach the guest */
    if (fresh_shell(&s, sh) == 0) {
        struct winsize ws2 = { .ws_row = 60, .ws_col = 80, .ws_xpixel = 0, .ws_ypixel = 0 };
        ioctl(s.m, TIOCSWINSZ, &ws2);
        nap(200);
        write(s.m, "stty size\n", 10);
        int got = read_until(s.m, "60 80", 5000);
        report(&failures, got, "TIOCSWINSZ reaches guest (stty size)",
               got ? "60 80 observed" : "size not observed");
        kill_shell(&s);
    }

    /* 4. UTF-8 through the shell (cooked mode) */
    if (fresh_shell(&s, sh) == 0) {
        char cmd[64];
        int L = snprintf(cmd, sizeof cmd, "printf '\xc3\xa9\xc3\xbc\\n'\n");
        write(s.m, cmd, L);
        int got = read_until(s.m, "\xc3\xa9\xc3\xbc", 5000);
        report(&failures, got, "UTF-8 (é ü) survives shell round trip",
               got ? "bytes intact" : "corrupted/missing");
        kill_shell(&s);
    }

    /* 5. 8-bit-clean raw passthrough (fresh pty, cat echoes) */
    {
        int m2 = posix_openpt(O_RDWR | O_NOCTTY);
        struct winsize ws = { .ws_row = 40, .ws_col = 120, .ws_xpixel = 0, .ws_ypixel = 0 };
        ioctl(m2, TIOCSWINSZ, &ws);
        char *cat[] = { "cat", NULL };
        pid_t p2 = pty_spawn(m2, "cat", cat);
        struct termios t2;
        tcgetattr(m2, &t2);
        cfmakeraw(&t2);
        tcsetattr(m2, TCSANOW, &t2);
        nap(100);
        unsigned char payload[6] = { 0xC3, 0xA9, 0x01, 0xFF, 0x00, 0x07 };
        write(m2, payload, 6);
        char rb[64];
        size_t gotb = 0;
        double dl = now_ms() + 5000;
        set_nonblock(m2);
        while (gotb < 6 && now_ms() < dl) {
            ssize_t r = read(m2, rb + gotb, sizeof rb - gotb);
            if (r > 0) gotb += (size_t)r;
            else poll(NULL, 0, 2);
        }
        int ok = (gotb == 6) && memcmp(rb, payload, 6) == 0;
        char det[48];
        snprintf(det, sizeof det, "%zu/6 bytes intact", gotb);
        report(&failures, ok, "8-bit clean incl NUL/0xFF (raw cat echo)", det);
        kill(p2, SIGKILL); waitpid(p2, NULL, 0);
        close(m2);
    }

    /* 6. EOF: exit + EOT ends the shell with status 0 */
    if (fresh_shell(&s, sh) == 0) {
        write(s.m, "exit\n", 5);
        nap(100);
        unsigned char eot = 0x04;
        write(s.m, &eot, 1);
        int rc = reap_deadline(s.p, 8000);
        int ok = (rc == 0);
        report(&failures, ok, "shell exits cleanly on exit+EOT (EOF)",
               ok ? "exit status 0" : rc == 0x7fffffff ? "did not exit in 8s" : "abnormal status");
        if (rc == 0x7fffffff) { kill(s.p, SIGKILL); waitpid(s.p, NULL, 0); }
        close(s.m);
    }

    /* 7. closeSession semantics: SIGKILL direct child + master close, then
          check whether a setsid'd grandchild ("server") survives. Survival is
          the EXPECTED result — reported as an orphan-cleanup hazard. */
    if (fresh_shell(&s, sh) == 0) {
        write(s.m, "setsid sleep 60 & echo ORPHAN_READY\n", 36);
        read_until(s.m, "ORPHAN_READY", 5000);
        nap(300);
        drain_now(s.m);
        write(s.m, "echo PID:$(pgrep -f 'sleep 60' | head -1)\n", 42);
        memset(cap, 0, sizeof cap);
        capture(s.m, cap, sizeof cap - 1, 900);
        char *pidstr = strstr(cap, "PID:");
        pid_t orphan = pidstr ? (pid_t)strtol(pidstr + 4, NULL, 10) : 0;
        if (orphan > 0) {
            kill(s.p, SIGKILL);          /* = TerminalSession.finishIfRunning() */
            int rc = reap_deadline(s.p, 5000);
            if (rc == 0x7fffffff) { kill(s.p, SIGKILL); waitpid(s.p, NULL, 0); }
            close(s.m);                  /* = cleanupResources JNI.close(master) */
            s.m = -1;
            nap(800);
            int alive = kill(orphan, 0) == 0;
            char det[112];
            snprintf(det, sizeof det, "grandchild %d alive=%c state=%c",
                     orphan, alive ? 'y' : 'n', proc_state(orphan));
            report(&failures, alive,
                   "orphan hazard: setsid'd grandchild survives close", det);
            kill(orphan, SIGKILL);
        } else {
            report(&failures, 0, "orphan experiment", "could not resolve grandchild pid");
            kill_shell(&s);
        }
    }

    printf("sigtest: %d failure(s)\n", failures);
    return failures;
}

/* poll-based sleep. Used instead of usleep(3) in sigtest: sleeping across an
 * INTR window desyncs the outer proot tracer (SIGTRAP at loader+0x1bb4). */
static void nap(int ms) { poll(NULL, 0, ms); }

/* ---------------- main ---------------- */

static void usage(void) {
    fprintf(stderr,
        "usage: ph4bench <cmd>\n"
        "  exec <N> <argv...>     fork/exec/waitpid latency + subtree CPU\n"
        "  fs <N> <path>          stat() latency\n"
        "  ptylat <N> [argv...]   PTY round-trip latency\n"
        "  ptytp <MiB> [--rev]    PTY throughput\n"
        "  session [argv...]      app-style spawn cost\n"
        "  sessionfo [argv...]    app-style spawn, fork→first-output only\n"
        "  sigtest [-- argv...]   PTY signal/correctness battery\n");
}

int main(int argc, char **argv) {
    if (argc < 2) { usage(); return 2; }
    signal(SIGPIPE, SIG_IGN);
    if (!strcmp(argv[1], "exec")) {
        if (argc < 4) { usage(); return 2; }
        return bench_exec(atoi(argv[2]), argv + 3);
    }
    if (!strcmp(argv[1], "fs")) {
        if (argc < 4) { usage(); return 2; }
        return bench_fs(atoi(argv[2]), argv[3]);
    }
    if (!strcmp(argv[1], "ptylat")) {
        int n = argc > 2 ? atoi(argv[2]) : 100;
        char *def[] = { "/bin/sh", "-l", NULL };
        char **shell = argc > 3 ? argv + 3 : def;
        return ptylat(n, shell);
    }
    if (!strcmp(argv[1], "ptytp")) {
        int mib = argc > 2 ? atoi(argv[2]) : 16;
        int rev = (argc > 3 && !strcmp(argv[3], "--rev"));
        return ptytp(mib, rev);
    }
    if (!strcmp(argv[1], "session")) {
        return bench_session(argc - 2, argv + 2);
    }
    if (!strcmp(argv[1], "sessionfo")) {
        return bench_sessionfo(argc - 2, argv + 2);
    }
    if (!strcmp(argv[1], "sigtest")) {
        int off = 2;
        if (argc > 2 && !strcmp(argv[2], "--")) off = 3;
        return sigtest(argc - off, argv + off);
    }
    usage();
    return 2;
}
