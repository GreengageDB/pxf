#ifndef _PXFUTILS_H_
#define _PXFUTILS_H_

#include "postgres.h"

/* convert input string to upper case and prepend "X-GP-OPTIONS-" prefix */
char	   *normalize_key_name(const char *key);

/* get the name of the type, given the OID */
char	   *TypeOidGetTypename(Oid typid);

/* Get protocol for the PXF server URL */
const char *get_pxf_protocol(void);

/* Get authority (host:port) for the PXF server URL */
char	   *get_authority(void);

/* Returns the PXF Host defined in the PXF_HOST
 * environment variable or the default when undefined
 */
const char *get_pxf_host(void);

/* Returns the PXF Port defined in the PXF_PORT
 * environment variable or the default when undefined
 */
const int  get_pxf_port(void);

const char *get_pxf_ssl_keypasswd(void);
const char *get_pxf_ssl_cacert(void);
const char *get_pxf_ssl_cert(void);
const char *get_pxf_ssl_key(void);
const char *get_pxf_ssl_certtype(void);
long get_pxf_ssl_verifypeer(void);

/* Build palloc'ed SSL options from the environment, returns NULL if the protocol is not https */
struct churl_ssl_options;
struct churl_ssl_options *get_pxf_ssl_options_from_env(void);

/* Returns the namespace (schema) name for a given namespace oid */
char	   *GetNamespaceName(Oid nsp_oid);

#define PXF_PROFILE                 "PROFILE"
#define FRAGMENTER                  "FRAGMENTER"
#define ACCESSOR                    "ACCESSOR"
#define RESOLVER                    "RESOLVER"
#define ANALYZER                    "ANALYZER"
#define ENV_PXF_HOST                "PXF_HOST"
#define ENV_PXF_PORT                "PXF_PORT"
#define ENV_PXF_PROTOCOL            "PXF_PROTOCOL"
#define ENV_PXF_SSL_CACERT          "PXF_SSL_CACERT"
#define ENV_PXF_SSL_CERT            "PXF_SSL_CERT"
#define ENV_PXF_SSL_CERT_TYPE       "PXF_SSL_CERT_TYPE"
#define ENV_PXF_SSL_KEY             "PXF_SSL_KEY"
#define ENV_PXF_SSL_KEYPASSWD       "PXF_SSL_KEYPASSWD"
#define ENV_PXF_SSL_VERIFY_PEER     "PXF_SSL_VERIFY_PEER"
#define PXF_DEFAULT_HOST            "localhost"
#define PXF_DEFAULT_PORT            5888
#define PXF_DEFAULT_PROTOCOL        "http"
#define PXF_DEFAULT_SSL_CACERT      "/home/gpadmin/arenadata_configs/cacert.pem"
#define PXF_DEFAULT_SSL_CERT        "client.pem"
#define PXF_DEFAULT_SSL_CERT_TYPE   "pem"
#define PXF_DEFAULT_SSL_KEY         ""
#define PXF_DEFAULT_SSL_KEYPASSWD   ""
#define PXF_DEFAULT_SSL_VERIFY_PEER 1

#endif							/* _PXFUTILS_H_ */
