/* t_fork_exec — glibc parent spawns a child process: fork + exec of the guest
 * musl shell + waitpid. Proves glibc↔musl process-tree coexistence. */
#include <stdio.h>
#include <stdlib.h>
#include <sys/wait.h>
#include <unistd.h>

int main(void) {
    pid_t pid = fork();
    if (pid < 0) { printf("fork-FAIL\n"); return 1; }
    if (pid == 0) {
        execl("/bin/sh", "sh", "-c", "echo child-ok", (char *)NULL);
        _exit(127);
    }
    int status = 0;
    if (waitpid(pid, &status, 0) < 0) { printf("waitpid-FAIL\n"); return 1; }
    if (!WIFEXITED(status) || WEXITSTATUS(status) != 0) { printf("child-FAIL\n"); return 1; }
    printf("fork-exec-ok\n");
    return 0;
}
