/* t_getpwnam — glibc NSS files path: reads the guest's /etc/passwd. */
#include <pwd.h>
#include <stdio.h>
#include <string.h>

int main(void) {
    struct passwd *pw = getpwnam("root");
    if (!pw) { printf("getpwnam-FAIL\n"); return 1; }
    printf(strcmp(pw->pw_name, "root") == 0 ? "getpwnam-ok\n" : "getpwnam-FAIL\n");
    return 0;
}
