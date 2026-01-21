/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

#include "pxf_bridge.h"
#include "pxf_header.h"

#include "cdb/cdbtm.h"
#include "cdb/cdbvars.h"
#include "access/xact.h"
#include "utils/memutils.h"
#include "utils/guc.h"

typedef struct PxfFdwCancelState
{
	CHURL_HEADERS churl_headers;
	CHURL_HANDLE churl_handle;
	ResourceOwner owner;
	StringInfoData uri;
	char       *pxf_protocol;
	int			pxf_port;		/* port number for the PXF Service */
	char	   *pxf_host;		/* hostname for the PXF Service */
	churl_ssl_options *ssl_options; /* NULL if SSL not configured */
} PxfFdwCancelState;

/* helper function declarations */
static void PxfBridgeCancelCleanup(PxfFdwCancelState *pxfcstate);
static void BuildUriForCancel(PxfFdwCancelState *pxfcstate);
static void BuildUriForRead(PxfFdwScanState *pxfsstate);
static void BuildUriForWrite(PxfFdwModifyState *pxfmstate);
#if PG_VERSION_NUM >= 90600
static size_t FillBuffer(PxfFdwScanState *pxfsstate, char *start, int minlen, int maxlen);
#else
static size_t FillBuffer(PxfFdwScanState *pxfsstate, char *start, size_t size);
#endif

static churl_ssl_options *churl_make_ssl_options(PxfOptions *options)
{
	churl_ssl_options *ssl_options = palloc0(sizeof(churl_ssl_options));

	if (options->pxf_ssl_cacert)
		ssl_options->pxf_ssl_cacert = pstrdup(options->pxf_ssl_cacert);

	if (options->pxf_ssl_cert)
		ssl_options->pxf_ssl_cert = pstrdup(options->pxf_ssl_cert);

	if (options->pxf_ssl_cert_type)
		ssl_options->pxf_ssl_cert_type = pstrdup(options->pxf_ssl_cert_type);

	if (options->pxf_ssl_key)
		ssl_options->pxf_ssl_key = pstrdup(options->pxf_ssl_key);

	if (options->pxf_ssl_keypasswd)
		ssl_options->pxf_ssl_keypasswd = pstrdup(options->pxf_ssl_keypasswd);

	ssl_options->pxf_ssl_verify_peer = options->pxf_ssl_verify_peer;

	return ssl_options;
}

static void free_churl_ssl_options(churl_ssl_options *ssl_options)
{
	if (ssl_options->pxf_ssl_cacert)
		pfree(ssl_options->pxf_ssl_cacert);

	if (ssl_options->pxf_ssl_cert)
		pfree(ssl_options->pxf_ssl_cert);

	if (ssl_options->pxf_ssl_cert_type)
		pfree(ssl_options->pxf_ssl_cert_type);

	if (ssl_options->pxf_ssl_key)
		pfree(ssl_options->pxf_ssl_key);
	
	if (ssl_options->pxf_ssl_keypasswd)
		pfree(ssl_options->pxf_ssl_keypasswd);

	pfree(ssl_options);
}

static bool
IsProtocolHttps(const char *protocol) 
{
	return protocol != NULL && (strcmp("https", protocol) == 0);	
}

static void
PxfBridgeAbortCallback(ResourceReleasePhase phase,
							 bool isCommit,
							 bool isTopLevel,
							 void *arg)
{
	PxfFdwCancelState *pxfcstate = arg;

	if (phase != RESOURCE_RELEASE_AFTER_LOCKS)
		return;

	if (pxfcstate->owner == CurrentResourceOwner)
	{
		if (isCommit)
			elog(LOG, "pxf BridgeAbort reference leak: %p still referenced", arg);

		PxfBridgeCancelCleanup(pxfcstate);
	}
}

static void
PxfBridgeCancel(PxfFdwCancelState *pxfcstate)
{
	int local_port = churl_get_local_port(pxfcstate->churl_handle);
	int savedInterruptHoldoffCount = InterruptHoldoffCount;

	if (local_port == 0)
		return;

	PG_TRY();
	{
		CHURL_HANDLE churl_handle;

		churl_headers_append(pxfcstate->churl_headers, "X-GP-CLIENT-PORT", psprintf("%i", local_port));

		initStringInfo(&pxfcstate->uri);
		BuildUriForCancel(pxfcstate);
		churl_handle = churl_init_upload_timeout_ssl(pxfcstate->uri.data, pxfcstate->churl_headers, pxfcstate->ssl_options, 1L);

		churl_cleanup(churl_handle, false);
	}
	PG_CATCH();
	{
		InterruptHoldoffCount = savedInterruptHoldoffCount;

		if (!elog_dismiss(WARNING))
		{
			FlushErrorState();
			elog(WARNING, "unable to dismiss error");
		}
	}
	PG_END_TRY();
}

static void
PxfBridgeCancelCleanup(PxfFdwCancelState *pxfcstate)
{
	if (pxfcstate == NULL)
		return;

	UnregisterResourceReleaseCallback(PxfBridgeAbortCallback, pxfcstate);

	if (IsAbortInProgress())
		PxfBridgeCancel(pxfcstate);

	if (pxfcstate->pxf_protocol)
		pfree(pxfcstate->pxf_protocol);

	if (pxfcstate->pxf_host)
		pfree(pxfcstate->pxf_host);

	if (pxfcstate->ssl_options) {
		free_churl_ssl_options(pxfcstate->ssl_options);
	}

	pfree(pxfcstate);
}

/*
 * Clean up churl related data structures from the PXF FDW scan state.
 */
void
PxfBridgeImportCleanup(PxfFdwScanState *pxfsstate)
{
	if (pxfsstate == NULL)
		return;

	PxfBridgeCancelCleanup(pxfsstate->pxfcstate);
	pxfsstate->pxfcstate = NULL;

	churl_cleanup(pxfsstate->churl_handle, false);
	pxfsstate->churl_handle = NULL;

	churl_headers_cleanup(pxfsstate->churl_headers);
	pxfsstate->churl_headers = NULL;

	if (pxfsstate->uri.data)
	{
		pfree(pxfsstate->uri.data);
	}

	if (pxfsstate->options)
	{
		pfree(pxfsstate->options);
	}
}

/*
 * Clean up churl related data structures from the PXF FDW modify state.
 */
void
PxfBridgeCleanup(PxfFdwModifyState *pxfmstate)
{
	if (pxfmstate == NULL)
		return;

	churl_cleanup(pxfmstate->churl_handle, false);
	pxfmstate->churl_handle = NULL;

	churl_headers_cleanup(pxfmstate->churl_headers);
	pxfmstate->churl_headers = NULL;

	if (pxfmstate->uri.data)
	{
		pfree(pxfmstate->uri.data);
	}

	if (pxfmstate->options)
	{
		pfree(pxfmstate->options);
	}
}

/*
 * Sets up data before starting import
 */
void
PxfBridgeImportStart(PxfFdwScanState *pxfsstate)
{
	MemoryContext oldcontext;
	PxfFdwCancelState *pxfcstate;
	churl_ssl_options *ssl_options = NULL;

	pxfsstate->churl_headers = churl_headers_init();

	BuildUriForRead(pxfsstate);
	BuildHttpHeaders(pxfsstate->churl_headers,
					 pxfsstate->options,
					 pxfsstate->relation,
					 pxfsstate->filter_str,
					 pxfsstate->retrieved_attrs,
					 pxfsstate->projectionInfo);

	if (IsProtocolHttps(pxfsstate->options->pxf_protocol)) {
		ssl_options = churl_make_ssl_options(pxfsstate->options);
	}

	pxfsstate->churl_handle = churl_init_download_ssl(pxfsstate->uri.data, pxfsstate->churl_headers, ssl_options);
	if (ssl_options != NULL) {
		free_churl_ssl_options(ssl_options);
	}

	oldcontext = MemoryContextSwitchTo(CurTransactionContext);
	pxfcstate = palloc0(sizeof(PxfFdwCancelState));
	pxfcstate->pxf_protocol = pstrdup(pxfsstate->options->pxf_protocol);
	pxfcstate->pxf_host = pstrdup(pxfsstate->options->pxf_host);
	if (ssl_options != NULL) {
		/* make another copy for transaction context */
		pxfcstate->ssl_options = churl_make_ssl_options(pxfsstate->options);
	}
	MemoryContextSwitchTo(oldcontext);
	pxfsstate->pxfcstate = pxfcstate;
	pxfcstate->churl_headers = pxfsstate->churl_headers;
	pxfcstate->churl_handle = pxfsstate->churl_handle;
	pxfcstate->owner = CurTransactionResourceOwner;
	pxfcstate->pxf_port = pxfsstate->options->pxf_port;
	RegisterResourceReleaseCallback(PxfBridgeAbortCallback, pxfcstate);

	/* read some bytes to make sure the connection is established */
	churl_read_check_connectivity(pxfsstate->churl_handle);
}

/*
 * Sets up data before starting export
 */
void
PxfBridgeExportStart(PxfFdwModifyState *pxfmstate)
{
	churl_ssl_options *ssl_options = NULL; /* NULL if SSL not used */
	BuildUriForWrite(pxfmstate);
	pxfmstate->churl_headers = churl_headers_init();
	BuildHttpHeaders(pxfmstate->churl_headers,
					 pxfmstate->options,
					 pxfmstate->relation,
					 NULL,
					 NULL,
					 NULL);

	if (IsProtocolHttps(pxfmstate->options->pxf_protocol)) {
		ssl_options = churl_make_ssl_options(pxfmstate->options);
	}

	pxfmstate->churl_handle = churl_init_upload_ssl(pxfmstate->uri.data, pxfmstate->churl_headers, ssl_options);

	if (ssl_options != NULL) {
		free_churl_ssl_options(ssl_options);
	}
}

/*
 * Reads data from the PXF server into the given buffer of a given size
 */
int
#if PG_VERSION_NUM >= 90600
PxfBridgeRead(void *outbuf, int minlen, int maxlen, void *extra)
#else
PxfBridgeRead(void *outbuf, int datasize, void *extra)
#endif
{
	size_t		n = 0;
	PxfFdwScanState *pxfsstate = (PxfFdwScanState *) extra;

#if PG_VERSION_NUM >= 90600
	n = FillBuffer(pxfsstate, outbuf, minlen, maxlen);
#else
	n = FillBuffer(pxfsstate, outbuf, datasize);
#endif

	if (n == 0)
	{
		/* check if the connection terminated with an error */
		churl_read_check_connectivity(pxfsstate->churl_handle);
	}

	elog(DEBUG5, "pxf PxfBridgeRead: segment %d read %zu bytes from %s",
		 PXF_SEGMENT_ID, n, pxfsstate->options->resource);

	return (int) n;
}

/*
 * Writes data from the given buffer of a given size to the PXF server
 */
int
PxfBridgeWrite(PxfFdwModifyState *pxfmstate, char *databuf, int datalen)
{
	size_t		n = 0;

	if (datalen > 0)
	{
		n = churl_write(pxfmstate->churl_handle, databuf, datalen);
		elog(DEBUG5, "pxf PxfBridgeWrite: segment %d wrote %zu bytes to %s", PXF_SEGMENT_ID, n, pxfmstate->options->resource);
	}

	return (int) n;
}

/*
 * Format the URI for cancel by adding PXF service endpoint details
 */
static void
BuildUriForCancel(PxfFdwCancelState *pxfcstate)
{
	const char *protocol = IsProtocolHttps(pxfcstate->pxf_protocol) ? "https" : "http";

	resetStringInfo(&pxfcstate->uri);
	appendStringInfo(&pxfcstate->uri, "%s://%s:%d/%s/cancel", 
		protocol, pxfcstate->pxf_host, pxfcstate->pxf_port, PXF_SERVICE_PREFIX);
	elog(DEBUG2, "pxf_fdw: uri %s for cancel", pxfcstate->uri.data);
}

/*
 * Format the URI for reading by adding PXF service endpoint details
 */
static void
BuildUriForRead(PxfFdwScanState *pxfsstate)
{
	PxfOptions *options = pxfsstate->options;
	const char *protocol = IsProtocolHttps(options->pxf_protocol) ? "https" : "http";

	resetStringInfo(&pxfsstate->uri);
	appendStringInfo(&pxfsstate->uri, "%s://%s:%d/%s/read", 
		protocol, options->pxf_host, options->pxf_port, PXF_SERVICE_PREFIX);

	if ((LOG >= log_min_messages) || (LOG >= client_min_messages))
	{
		appendStringInfo(&pxfsstate->uri, "?trace=true");
	}

	elog(DEBUG2, "pxf_fdw: uri %s for read", pxfsstate->uri.data);
}

/*
 * Format the URI for writing by adding PXF service endpoint details
 */
static void
BuildUriForWrite(PxfFdwModifyState *pxfmstate)
{
	PxfOptions *options = pxfmstate->options;
	const char *protocol = IsProtocolHttps(options->pxf_protocol) ? "https" : "http";

	resetStringInfo(&pxfmstate->uri);
	appendStringInfo(&pxfmstate->uri, "%s://%s:%d/%s/write", 
		protocol, options->pxf_host, options->pxf_port, PXF_SERVICE_PREFIX);

	if ((LOG >= log_min_messages) || (LOG >= client_min_messages))
	{
		appendStringInfo(&pxfmstate->uri, "?trace=true");
	}

	elog(DEBUG2, "pxf_fdw: uri %s with file name for write: %s", pxfmstate->uri.data, options->resource);
}

/*
 * Read data from churl until the buffer is full or there is no more data to be read
 */
static size_t
#if PG_VERSION_NUM >= 90600
FillBuffer(PxfFdwScanState *pxfsstate, char *start, int minlen, int maxlen)
#else
FillBuffer(PxfFdwScanState *pxfsstate, char *start, size_t size)
#endif
{
	size_t		n = 0;
	char	   *ptr = start;
#if PG_VERSION_NUM >= 90600
	char	   *minend = ptr + minlen;
	char	   *maxend = ptr + maxlen;

	while (ptr < minend)
	{
		n = churl_read(pxfsstate->churl_handle, ptr, maxend - ptr);
#else
	char	   *end = ptr + size;

	while (ptr < end)
	{
		n = churl_read(pxfsstate->churl_handle, ptr, end - ptr);
#endif
		if (n == 0)
			break;

		ptr += n;
	}

	return ptr - start;
}
