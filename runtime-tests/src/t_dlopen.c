/* t_dlopen — glibc dlopen/dlsym across libraries (libm, libz) + dlclose. */
#include <dlfcn.h>
#include <math.h>
#include <stdio.h>

typedef double (*dfunc)(double);

int main(void) {
    int fails = 0;
    void *m = dlopen("libm.so.6", RTLD_NOW);
    if (!m) { printf("dlopen-libm-FAIL: %s\n", dlerror()); fails++; }
    else {
        dfunc sq = (dfunc)dlsym(m, "sqrt");
        if (!sq || fabs(sq(4.0) - 2.0) > 1e-9) { printf("dlsym-sqrt-FAIL\n"); fails++; }
        else printf("dlopen-libm-ok\n");
    }
    void *z = dlopen("libz.so.1", RTLD_NOW);
    if (!z) { printf("dlopen-libz-FAIL: %s\n", dlerror()); fails++; }
    else { printf("dlopen-libz-ok\n"); }
    if (m) dlclose(m);
    if (z) dlclose(z);
    return fails ? 1 : 0;
}
