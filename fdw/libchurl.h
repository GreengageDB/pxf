/*
 * Compatibility header for out-of-tree users of the pxf_fdw API (e.g. tkh_fdw).
 * FDW code lives in external-table/src and is built into pxf.so.
 */
#include "../external-table/src/libchurl.h"
