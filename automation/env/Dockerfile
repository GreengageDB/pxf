# automation/env/Dockerfile
ARG GGDB_IMAGE=greengagedb/ggdb6_ubuntu:6.29.1
FROM ghcr.io/linuxcontainers/alpine AS download

ARG GO_URL=https://go.dev/dl/go1.21.3.linux-amd64.tar.gz
ARG MAVEN_URL=https://archive.apache.org/dist/maven/maven-3/3.9.11/binaries/apache-maven-3.9.11-bin.tar.gz
ARG JAR_ENCR_URL=https://repo1.maven.org/maven2/io/arenadata/security/encryption/1.0.0/encryption-1.0.0-exec.jar
ARG JAR_OJDBC_URL=https://repo1.maven.org/maven2/com/oracle/database/jdbc/ojdbc8/12.2.0.1/ojdbc8-12.2.0.1.jar
ARG JAR_MYSQL_URL=https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.3.0/mysql-connector-j-8.3.0.jar

RUN apk add --no-cache curl tar

# --- ATTENTION! folder .cache must be exists ---
RUN --mount=type=bind,source=.cache,target=.cache,rw \
    (curl -fSL --continue-at - $GO_URL        -o .cache/go.tar.gz           || true) ; \
    (curl -fSL --continue-at - $MAVEN_URL     -o .cache/maven.tar.gz        || true) ; \
    (curl -fSL --continue-at - $JAR_ENCR_URL  -o .cache/encryption.jar      || true) ; \
    (curl -fSL --continue-at - $JAR_OJDBC_URL -o .cache/ojdbc8.jar          || true) ; \
    (curl -fSL --continue-at - $JAR_MYSQL_URL -o .cache/mysql-connector.jar || true) ; \
    tar -xf .cache/maven.tar.gz -C /opt && mv /opt/apache-maven-* /opt/maven/ ; \
    tar -xf .cache/go.tar.gz -C /opt ; \
    mkdir -p /opt/lib && mv .cache/*.jar /opt/lib/

FROM $GGDB_IMAGE

# For local build without cache from root of repo
ARG BUILD_CONTEXT=.

ARG GPHOME=/usr/local/greengage-db-devel

SHELL ["bash", "-c"]

# --- Environment ---
ENV GPHOME=$GPHOME
ENV GOPATH=/root/go
ENV JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF8
ENV PXF_HOME=$GPHOME/pxf
ENV SKIP_FDW_BUILD_REASON=0 
ENV PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/opt/go/bin:/opt/maven/bin:$GOPATH/bin"

WORKDIR /

# --- Go and Maven ---
COPY --from=download /opt /opt

# --- System ---
ENV DEBIAN_FRONTEND=noninteractive
RUN apt -y update && apt install -y unzip vim nano openjdk-17-jdk ksh locales; \
        update-locale LANG=en_US.UTF-8; \
        apt clean && rm -rf /var/lib/apt/lists/*

# --- ginkgo ---
RUN go install github.com/onsi/ginkgo/ginkgo@latest

# --- Setup GPDB directories ---
WORKDIR /home/gpadmin
RUN mkdir -p /data1/master /data1/primary /data1/mirror && chmod -R 755 /data1 && \
    gpdb_src/concourse/scripts/setup_gpadmin_user.bash && \
    source gpdb_src/concourse/scripts/common.bash && \
    chown -R gpadmin:gpadmin /data1 /home/gpadmin && \
    install_gpdb

# --- JAR libraries ---
RUN mkdir -p $PXF_HOME/lib/ && mv /opt/lib $PXF_HOME/

# --- Build PXF ---
COPY $BUILD_CONTEXT/ /home/gpadmin/pxf_src
COPY $BUILD_CONTEXT/automation/env/scripts/compile_pxf_without_test.sh /home/gpadmin/pxf_src/concourse/scripts/compile_pxf_without_test.sh
RUN chmod +x /home/gpadmin/pxf_src/concourse/scripts/compile_pxf_without_test.sh

RUN source gpdb_src/concourse/scripts/common.bash && \
    source $GPHOME/greengage_path.sh && \
    /home/gpadmin/pxf_src/concourse/scripts/compile_pxf_without_test.sh && \
    chown -R gpadmin:gpadmin /home/gpadmin/pxf_src

RUN mkdir -p /home/gpadmin/workspace && ln -s /home/gpadmin/pxf_src /home/gpadmin/workspace/pxf && \
    chown -R gpadmin:gpadmin /home/gpadmin/workspace

# --- PXF configuration ---
RUN localedef -c -i ru_RU -f CP1251 ru_RU.CP1251
RUN cp $PXF_HOME/templates/{hbase-site.xml,hdfs-site.xml,hive-site.xml,core-site.xml,mapred-site.xml,pxf-site.xml,jdbc-site.xml} \
       $PXF_HOME/servers/default/

# Copy pxf-application.properties
COPY ./automation/env/conf/pxf-application.properties $PXF_HOME/conf/pxf-application.properties
COPY ./automation/env/conf/pxf-env.sh $PXF_HOME/conf/pxf-env.sh

RUN mkdir -p $PXF_HOME/servers/oracle $PXF_HOME/servers/mysql $PXF_HOME/servers/parquet_config
COPY $BUILD_CONTEXT/automation/env/conf/oracle/jdbc-site.xml $PXF_HOME/servers/oracle/jdbc-site.xml
COPY $BUILD_CONTEXT/automation/env/conf/mysql/jdbc-site.xml $PXF_HOME/servers/mysql/jdbc-site.xml
COPY $BUILD_CONTEXT/automation/env/conf/parquet/pxf-site.xml $PXF_HOME/servers/parquet_config/pxf-site.xml

# --- SSH key compatibility for Ubuntu automation ---
RUN set -eux; \
    if grep -q ubuntu /etc/os-release; then \
      ssh-keygen -p -P "" -N "" -m pem -f /home/gpadmin/.ssh/id_rsa; \
      ssh-keygen -p -P "" -N "" -m pem -f /root/.ssh/id_rsa; \
      echo "KexAlgorithms +diffie-hellman-group-exchange-sha1" >> /etc/ssh/sshd_config; \
      echo "PubkeyAcceptedAlgorithms +ssh-rsa" >> /etc/ssh/sshd_config; \
      echo "HostKeyAlgorithms +ssh-rsa" >> /etc/ssh/sshd_config; \
    fi

# --- Final setup ---
RUN chmod -R a+x /home/gpadmin/pxf_src/automation/env/scripts && \
    chown -R gpadmin:gpadmin $GPHOME

ENTRYPOINT ["/home/gpadmin/pxf_src/automation/env/scripts/start_cluster.sh"]
