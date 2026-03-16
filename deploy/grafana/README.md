## PXF Grafana Dashboards

This directory contains Grafana dashboards for monitoring the PXF service in Greengage DB.

Import `gg_pxf_dashboard.json` into Grafana to get a single view of PXF health, executor behavior, data transfer, and JVM/system metrics.

### Template variables

- **Datasource (`DS_PROMETHEUS`)**: Prometheus datasource to query.
- **PXF Instance (`instance`)**: One or more PXF instances to display (defaults to *All*).

---

### Metrics overview

| **Metric / Panel name**                | **Description** |
|----------------------------------------|-----------------|
| PXF Instances Status                   | Shows if each PXF instance is UP (1) or DOWN (0). Good for a quick “is it alive?” check. |
| CPU Usage by Instance                  | Fraction of CPU used by the PXF process per instance (0–1). Use to spot CPU hotspots. |
| ☕ JVM Heap Usage by Instance          | Heap utilization percentage per instance. Useful to detect memory pressure and potential GC issues. |
| PXF Executor Active Threads            | Number of currently active executor threads. High or saturated values may indicate thread pool exhaustion. |
| PXF Executor Queued Tasks              | Number of tasks waiting in the executor queue. Growing queues indicate backlog or contention. |
| Pool Size                              | Current size of the executor thread pool per instance. |
| Core Threads                           | Configured core thread count for the executor pool. |
| Max Threads                            | Maximum allowed executor threads per instance. Compare with Active Threads and Queued Tasks to evaluate capacity. |
| HTTP READ Request Rate (/pxf/read)     | Read requests per second per instance. Reflects incoming query/read load on PXF. |
| HTTP READ Data Rate (Bytes)           | Outgoing data rate (bytes per second) for JDBC read profile. Indicates throughput of data sent back to clients. |
| HTTP WRITE Request Rate (/pxf/write)   | Write requests per second per instance. Reflects ingest/write traffic via PXF. |
| HTTP WRITE Data Rate (Bytes)          | Data rate (bytes per second) for JDBC write profile. |
| Bytes Received (All Profiles)          | Incoming byte rate per instance from all profiles. Helps to understand inbound data volume. |
| Records Received (All Profiles)        | Number of records per second received by PXF. |
| Bytes Sent (All Profiles)              | Outgoing byte rate per instance across all profiles. |
| Records Sent (All Profiles)            | Number of records per second sent by PXF. |
| JVM Live Threads                       | Live JVM thread count per instance. Sudden spikes may indicate thread leaks or high concurrency. |
| JVM Loaded Classes                     | Number of currently loaded classes. Useful to diagnose classloader-related issues. |
| Process Uptime                         | Seconds since the PXF process started. Use to detect restarts and deployment events. |
| System CPU Usage                       | Overall system CPU utilization where PXF runs (0–1). Correlate with process CPU usage to see PXF’s share. |
| System CPU count                       | Number of CPU cores available to the host/instance. Helps interpret CPU utilization in absolute terms. |
| JVM Memory Committed                   | Amount of memory guaranteed to be available to the JVM (committed) for each memory region. |
| JVM Memory Max                         | Maximum memory that can be used by each JVM memory region (may be -1 for unbounded). |
| JVM Memory Used                        | Actual memory currently used in each region (heap and non-heap). Use together with Max and Committed to understand utilization. |
| PXF UPTIME (Table)                     | Tabular view of process uptime per host/instance with colored background. Handy overview of which instances are freshly restarted vs long-running. |

