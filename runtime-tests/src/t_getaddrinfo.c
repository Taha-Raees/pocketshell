/* t_getaddrinfo — glibc NSS DNS path (hosts: files dns → integrated resolver).
 * Mirrors what any glibc network CLI does on first use. */
#include <netdb.h>
#include <stdio.h>
#include <string.h>
#include <sys/socket.h>

int main(void) {
    struct addrinfo hints, *res = NULL;
    memset(&hints, 0, sizeof(hints));
    hints.ai_family = AF_UNSPEC;
    hints.ai_socktype = SOCK_STREAM;
    int rc = getaddrinfo("example.com", NULL, &hints, &res);
    if (rc != 0) { printf("getaddrinfo-FAIL: %s\n", gai_strerror(rc)); return 1; }
    char host[256] = "?";
    if (!getnameinfo(res->ai_addr, res->ai_addrlen, host, sizeof(host), NULL, 0, NI_NUMERICHOST))
        printf("getaddrinfo-ok %s\n", host);
    freeaddrinfo(res);
    return 0;
}
