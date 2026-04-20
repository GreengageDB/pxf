## PXF Grafana Dashboards

This directory contains Grafana dashboards for monitoring the PXF service in Greengage DB.

Import `gg_pxf_dashboard.json` into Grafana to get a single view of PXF health, executor behavior, data transfer, and JVM/system metrics.

### Template variables

- **Datasource (`DS_PROMETHEUS`)**: Prometheus datasource to query.
- **Application (`application`)**: Prometheus datasource to query.
- **Profile (`profile`)**: Prometheus datasource to query.
- **PXF Instance (`instance`)**: One or more PXF instances to display (defaults to *All*).

---
### Metrics tags

Apart from labels described in template variables (application, profile, instance ) there are few other labels presented in all PXF metrics:
- **user**: GG user who run the query
- **segment**: PXF segment id where query runs
- **server**: Identifier of server configs that are used in query, relevant mostly for external tables
- **datasource**: External storage's resource that is used in query execution 


### Metrics overview

| **Metric / Panel name**       | **Description**                                                                                                                   |
|-------------------------------|-----------------------------------------------------------------------------------------------------------------------------------|
| PXF Instances Status          | Shows if PXF instance is up or not.                                                                                               |
| CPU Usage by Instance         | Fraction of CPU used by the PXF process per instance.                                                                             |
| JVM Heap Usage by Instance    | Heap utilization percentage per instance.                                                                                         |
| Active Read Operations        | The number of read operations currently in progress.                                                                              |
| Active Write Operations       | The number of write operations currently in progress.                                                                             |
| Active Operations Count       | Full number of active operations during selected time period.                                                                     |
| Active Operations Duration    | Duration of active operations; if there are few of them and they have the same label values, then the maximum value is displayed. |
| PXF Executor Active Threads   | Number of currently active executor threads.                                                                                      |
| PXF Executor Queued Tasks     | Number of tasks waiting in the executor queue.                                                                                    |
| Fragment Processing Count     | Number of fragments currently in read.                                                                                            |
| Fragment Processing Duration  | Maximum value of time spent reading current fragments.                                                                            |
| Fragmenter & Bridge Call Rate | Frequency of calling fragmenter and bridge begin logic.                                                                           |
| Bridge Begin Duration         | Time spent on calling bridge begin ( usually contains creation and establishment of connection to the target storage).            |
| Fragmenter Call Duration      | Time spent on calling fragmeneter before spreading read activities between PXF instances.                                         |
| Records Read Rate             | Number of records per second received by PXF.                                                                                     |
| Bytes Read Rate               | Incoming byte rate per instance from all profiles.                                                                                |
| Records Written Rate          | Number of records per second sent by PXF.                                                                                         |
| Bytes Written Rate            | Outgoing byte rate per instance across all profiles.                                                                              |
| HTTP Request Rate             | Frequency of http requests to the PXF server.                                                                                     |
| HTTP Error Rate               | Frequency of failed http requests to the PXF server.                                                                              |
| Tomcat Threads                | Number of threads used by Tomcat grouped by thread type.                                                                          |
| Thread Pool Utilization       | Degree of tomcat thread's usage.                                                                                                  |
| Tomcat Connections            | Number of connections to Tomcat.                                                                                                  |
| JVM Heap Memory               | Amount of memory allocated/used by JVM.                                                                                           |
| JVM Non-Heap Memory           | Amount of non-heap memory used by JVM.                                                                                            |                                                                                                                                  |
| GC Pause Rate                 | Frequency of garbage collector execution                                                                                          |
| JVM Threads                   | Number of JVM threads by type                                                                                                     |
| CPU Usage                     | Fraction of CPU used by the PXF process per instance and instance.                                                                |
