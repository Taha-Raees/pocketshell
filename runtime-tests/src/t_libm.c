/* t_libm — glibc + libm (DT_NEEDED libm.so.6 forced like Cline's). */
#include <math.h>
#include <stdio.h>
int main(void) {
    double r = sqrt(144.0) + pow(2.0, 10.0);
    printf(r == 1036.0 ? "libm-ok\n" : "libm-FAIL\n");
    return r == 1036.0 ? 0 : 1;
}
