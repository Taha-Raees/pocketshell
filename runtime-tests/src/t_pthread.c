/* t_pthread — glibc + pthread (DT_NEEDED libpthread.so.0 forced like Cline's). */
#include <pthread.h>
#include <stdio.h>
#include <string.h>

static void *worker(void *arg) {
    (void)arg;
    static const char *msg = "pthread-ok";
    return (void *)msg;
}

int main(void) {
    pthread_t t;
    void *ret = NULL;
    if (pthread_create(&t, NULL, worker, NULL) != 0) {
        printf("pthread-create-FAIL\n");
        return 1;
    }
    pthread_join(t, &ret);
    printf("%s\n", ret != NULL && strcmp((char *)ret, "pthread-ok") == 0 ? "pthread-ok" : "pthread-FAIL");
    return 0;
}
