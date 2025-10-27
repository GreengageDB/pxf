# automation/env/Dockerfile
ARG GGDB_IMAGE=ghcr.io/greengagedb/greengage/ggdb6_ubuntu:latest
FROM $GGDB_IMAGE

SHELL ["/bin/bash","-c"]

# --- Environment ---
ARG GPHOME=/usr/local/greengage-db-devel \
    DEV_HOME=/home/gpadmin \
    GOPATH=/opt/go \
    JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF8 \
    SKIP_FDW_BUILD_REASON='test builds'
ARG PXF_HOME=$GPHOME/pxf
ARG PATH="$GPHOME/bin:$PXF_HOME/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/opt/maven/bin:/usr/local/go/bin:$GOPATH/bin"

ENV PATH=$PATH \
    GOPATH=$GOPATH \
    GPHOME=$GPHOME \
    PXF_HOME=$PXF_HOME \
    DEV_HOME=$DEV_HOME \
    JAVA_TOOL_OPTIONS=$JAVA_TOOL_OPTIONS \
    SKIP_FDW_BUILD_REASON=$SKIP_FDW_BUILD_REASON \
    DEBIAN_FRONTEND=noninteractive

WORKDIR $DEV_HOME
COPY . ./pxf

RUN cd pxf && bash ci/build_in_docker.sh

WORKDIR /

# --- System ---
RUN apt-get update  -y \
 && apt-get install -y --no-install-recommends unzip vim nano openjdk-17-jdk ksh locales
#  && apt-get clean && rm -rf /var/lib/apt/lists/*
       
# --- ginkgo ---
RUN go install github.com/onsi/ginkgo/ginkgo@latest

# --- Setup GPDB directories ---
WORKDIR /home/gpadmin
RUN mkdir -p /data1/{master,primary,mirror} && chmod -R 755 /data1 && \
    gpdb_src/concourse/scripts/setup_gpadmin_user.bash && \
    source gpdb_src/concourse/scripts/common.bash && \
    chown -R gpadmin:gpadmin /data1 /home/gpadmin && \
    install_gpdb

# --- Maven and JAR libraries ---

ARG MAVEN_VER=3.9.11
ARG MAVEN_URL=https://archive.apache.org/dist/maven/maven-3/$MAVEN_VER/binaries/apache-maven-$MAVEN_VER-bin.tar.gz
ARG JAR_ENCR_URL=https://repo1.maven.org/maven2/io/arenadata/security/encryption/1.0.0/encryption-1.0.0-exec.jar
ARG JAR_OJDBC_URL=https://repo1.maven.org/maven2/com/oracle/database/jdbc/ojdbc8/12.2.0.1/ojdbc8-12.2.0.1.jar
ARG JAR_MYSQL_URL=https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.3.0/mysql-connector-j-8.3.0.jar

RUN curl -fSL $MAVEN_URL      -o /tmp/maven.tar.gz \
 && curl -fSL $JAR_ENCR_URL   -o /tmp/encryption.jar \
 && curl -fSL $JAR_OJDBC_URL  -o /tmp/ojdbc8.jar \
 && curl -fSL $JAR_MYSQL_URL  -o /tmp/mysql-connector.jar \
 && tar -xf /tmp/maven.tar.gz -C /opt && mv /opt/apache-maven-$MAVEN_VER /opt/maven && rm -f /tmp/*.tar.gz \
 && mkdir -p $PXF_HOME/lib && mv /tmp/*.jar $PXF_HOME/lib/

# --- Build PXF ---
COPY . /home/gpadmin/pxf_src
COPY ./automation/env/scripts/compile_pxf_without_test.sh /home/gpadmin/pxf_src/concourse/scripts/compile_pxf_without_test.sh
RUN chmod +x /home/gpadmin/pxf_src/concourse/scripts/compile_pxf_without_test.sh

RUN source gpdb_src/concourse/scripts/common.bash && \
    source $GPHOME/greengage_path.sh && \
    /home/gpadmin/pxf_src/concourse/scripts/compile_pxf_without_test.sh && \
    chown -R gpadmin:gpadmin /home/gpadmin/pxf_src

RUN mkdir -p /home/gpadmin/workspace && ln -s /home/gpadmin/pxf_src /home/gpadmin/workspace/pxf && \
    chown -R gpadmin:gpadmin /home/gpadmin/workspace

# --- PXF configuration ---
RUN localedef -c -i ru_RU -f CP1251 ru_RU.CP1251 ; \
    cp $PXF_HOME/templates/{hbase-site.xml,hdfs-site.xml,hive-site.xml,core-site.xml,mapred-site.xml,pxf-site.xml,jdbc-site.xml} \
    $PXF_HOME/servers/default/

# Copy pxf-application.properties
COPY ./automation/env/conf/pxf-application.properties $PXF_HOME/conf/pxf-application.properties
COPY ./automation/env/conf/pxf-env.sh $PXF_HOME/conf/pxf-env.sh

# RUN  mkdir -p $PXF_HOME/servers/{oracle,mysql,parquet_config}
# COPY ./automation/env/conf/oracle/jdbc-site.xml $PXF_HOME/servers/oracle/jdbc-site.xml
# COPY ./automation/env/conf/mysql/jdbc-site.xml $PXF_HOME/servers/mysql/jdbc-site.xml
# COPY ./automation/env/conf/parquet/pxf-site.xml $PXF_HOME/servers/parquet_config/pxf-site.xml
RUN  mkdir -p $PXF_HOME/servers/
COPY ./automation/env/conf/* $PXF_HOME/servers/

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
