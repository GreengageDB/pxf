FROM openjdk:8

RUN apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
      net-tools curl wget netcat procps gnupg libsnappy-dev && rm -rf /var/lib/apt/lists/*

ENV HADOOP_VERSION=${HADOOP_VERSION:-3.3.6}
ENV HIVE_VERSION=${HIVE_VERSION:-4.0.0}
ENV HBASE_VERSION=${HBASE_VERSION:-2.5.10}
ENV TEZ_VERSION=${TEZ_VERSION:-0.10.3}
ENV ZOOKEEPER_VERSION=${ZOOKEEPER_VERSION:-3.9.4}

ENV HADOOP_URL  https://archive.apache.org/dist/hadoop/core/hadoop-$HADOOP_VERSION/hadoop-$HADOOP_VERSION.tar.gz
ENV HIVE_URL https://archive.apache.org/dist/hive/hive-$HIVE_VERSION/apache-hive-$HIVE_VERSION-bin.tar.gz
ENV HBASE_URL https://archive.apache.org/dist/hbase/$HBASE_VERSION/hbase-$HBASE_VERSION-hadoop3-bin.tar.gz
ENV TEZ_URL https://archive.apache.org/dist/tez/$TEZ_VERSION/apache-tez-$TEZ_VERSION-bin.tar.gz
ENV ZOOKEEPER_URL https://archive.apache.org/dist/zookeeper/zookeeper-$ZOOKEEPER_VERSION/apache-zookeeper-$ZOOKEEPER_VERSION-bin.tar.gz

# Download Hadoop
RUN set -x \
    && curl -fSL "$HADOOP_URL" -o /tmp/hadoop.tar.gz \
    && tar -xvf /tmp/hadoop.tar.gz -C /opt/ \
    && rm /tmp/hadoop.tar.gz*

# Download Hive
RUN set -x \
    && curl -fSL $HIVE_URL -o /tmp/hive.tar.gz \
    && tar -xvf /tmp/hive.tar.gz -C /opt/ \
    && mv /opt/apache-hive-"$HIVE_VERSION-bin" /opt/hive \
    && curl -fSL https://jdbc.postgresql.org/download/postgresql-9.4.1212.jar -o /opt/hive/lib/postgresql-jdbc.jar \
    && rm /tmp/hive.tar.gz*

# Download HBase
RUN set -x \
    && curl -fSL "$HBASE_URL" -o /tmp/hbase.tar.gz \
    && tar -xvf /tmp/hbase.tar.gz -C /opt/ \
    && mv /opt/hbase-$HBASE_VERSION-hadoop3 /opt/hbase \
    && rm /tmp/hbase.tar.gz*

# Download Tez
RUN set -x \
    && curl -fSL "$TEZ_URL" -o /tmp/apache-tez-"$TEZ_VERSION"-bin.tar.gz \
    && tar -xvf /tmp/apache-tez-"$TEZ_VERSION"-bin.tar.gz -C /opt/ \
    && mv /opt/apache-tez-"$TEZ_VERSION-bin" /opt/tez

# Download Zookeeper
RUN mkdir -p /usr/zookeeper \
    && curl -fSL $ZOOKEEPER_URL -o /tmp/apache-zookeeper.tar.gz && tar -xvf /tmp/apache-zookeeper.tar.gz -C /opt/ \
    && mv /opt/apache-zookeeper-$ZOOKEEPER_VERSION-bin /opt/zookeeper


# Configure HDFS
RUN ln -s /opt/hadoop-$HADOOP_VERSION/etc/hadoop /etc/hadoop
RUN mkdir /opt/hadoop-$HADOOP_VERSION/logs
RUN mkdir /hadoop-data

ENV HADOOP_HOME=/opt/hadoop-$HADOOP_VERSION
ENV HADOOP_CONF_DIR=/etc/hadoop
ENV MULTIHOMED_NETWORK=1
ENV USER=root
ENV PATH $HADOOP_HOME/bin/:$PATH

# Configure namenode
ENV HDFS_CONF_dfs_namenode_name_dir=file:///hadoop/dfs/name
RUN mkdir -p /hadoop/dfs/name

# Configure datenode
ENV HDFS_CONF_dfs_datanode_data_dir=file:///hadoop/dfs/data
RUN mkdir -p /hadoop/dfs/data

# Configure Hive
ENV HIVE_HOME /opt/hive
ENV PATH $HIVE_HOME/bin:$PATH
ADD hive-site.xml ${HIVE_HOME}/conf/hive-site.xml

# Configure Zookeeper
ENV ZOOKEEPER_HOME=/opt/zookeeper
ADD zoo.cfg $ZOOKEEPER_HOME/conf/zoo.cfg

# Configure HBase
ENV HBASE_HOME_DIR=/opt/hbase
ENV PATH=$HBASE_HOME_DIR/bin/:$PATH
RUN mkdir ${HBASE_HOME_DIR}/logs

# Configure Tez
ENV TEZ_HOME=/opt/tez
ADD tez-site.xml ${TEZ_HOME}/conf/tez-site.xml

ADD healthcheck_hadoop.sh /healthcheck_hadoop.sh
RUN chmod a+x /healthcheck_hadoop.sh
ADD start_hadoop.sh /start_hadoop.sh
RUN chmod a+x /start_hadoop.sh
ENTRYPOINT ["/start_hadoop.sh"]