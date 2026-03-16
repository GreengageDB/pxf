## PXF Grafana Dashboards

This directory contains Grafana dashboards for monitoring the PXF service in Greengage DB.

Import `gg_pxf_dashboard.json` into Grafana to get a single view of PXF health, executor behavior, data transfer, and JVM/system metrics.

### Template variables

- **Datasource (`DS_PROMETHEUS`)**: Prometheus datasource to query.
- **PXF Instance (`instance`)**: One or more PXF instances to display (defaults to *All*).

---

### Metrics overview

| **Metric / Panel name**              | **Description**                                                                                                   |
|--------------------------------------|-------------------------------------------------------------------------------------------------------------------|
| PXF Instances Status                 | Shows if PXF instance is up or not.                                                                               |
| CPU Usage by Instance                | Fraction of CPU used by the PXF process per instance.                                                             |
| JVM Heap Usage by Instance          | Heap utilization percentage per instance.                                                                         |
| PXF Executor Active Threads          | Number of currently active executor threads.                                                                      |
| PXF Executor Queued Tasks            | Number of tasks waiting in the executor queue.                                                                    |
| Pool Size                            | Current size of the executor thread pool per instance.                                                            |
| Core Threads                         | Configured core thread count for the executor pool.                                                               |
| Max Threads                          | Maximum allowed executor threads per instance. Compare with Active Threads and Queued Tasks to evaluate capacity. |
| HTTP READ Request Rate (/pxf/read)   | Read requests per second per instance. Reflects incoming query/read load on PXF.                                  |
| HTTP READ Data Rate (Bytes)         | Outgoing data rate (bytes per second) for JDBC read profile. Indicates throughput of data sent back to clients.   |
| HTTP WRITE Request Rate (/pxf/write) | Write requests per second per instance. Reflects ingest/write traffic via PXF.                                    |
| HTTP WRITE Data Rate (Bytes)        | Data rate (bytes per second) for JDBC write profile.                                                              |
| Bytes Received (All Profiles)        | Incoming byte rate per instance from all profiles.                                                                |
| Records Received (All Profiles)      | Number of records per second received by PXF.                                                                     |
| Bytes Sent (All Profiles)            | Outgoing byte rate per instance across all profiles.                                                              |
| Records Sent (All Profiles)          | Number of records per second sent by PXF.                                                                         |
| JVM Live Threads                     | Live JVM thread count per instance.                                                                               |
| JVM Loaded Classes                   | Number of currently loaded classes.                                                                               |
| Process Uptime                       | Seconds since the PXF process started.                                                                            |
| System CPU Usage                     | Overall system CPU utilization. Correlate with process CPU usage to see PXF’s share.                              |
| System CPU count                     | Number of CPU cores available to the host/instance.                                                               |
| JVM Memory Committed                 | Amount of memory guaranteed to be available to the JVM (committed) for each memory region.                        |
| JVM Memory Max                       | Maximum memory that can be used by each JVM memory region (may be -1 for unbounded).                              |
| JVM Memory Used                      | Actual memory currently used in each region (heap and non-heap).                                                  |
| PXF UPTIME (Table)                   | Tabular view of process uptime per host/instance with colored background.                                         |

