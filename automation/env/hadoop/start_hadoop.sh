#!/bin/bash

# Set some sensible defaults
export CORE_CONF_fs_defaultFS=${CORE_CONF_fs_defaultFS:-hdfs://`hostname -f`:8020}

function wait_for_it()
{
    local serviceport=$1
    local service=${serviceport%%:*}
    local port=${serviceport#*:}
    local retry_seconds=5
    local max_try=100
    let i=1

    nc -z $service $port
    result=$?

    until [ $result -eq 0 ]; do
      echo "[$i/$max_try] check for ${service}:${port}..."
      echo "[$i/$max_try] ${service}:${port} is not available yet"
      if (( $i == $max_try )); then
        echo "[$i/$max_try] ${service}:${port} is still not available; giving up after ${max_try} tries. :/"
        exit 1
      fi

      echo "[$i/$max_try] try in ${retry_seconds}s once again ..."
      let "i++"
      sleep $retry_seconds

      nc -z $service $port
      result=$?
    done
    echo "[$i/$max_try] $service:${port} is available."
}

function addProperty() {
  local path=$1
  local name=$2
  local value=$3

  local entry="<property><name>$name</name><value>${value}</value></property>"
  local escapedEntry=$(echo $entry | sed 's/\//\\\//g')
  sed -i "/<\/configuration>/ s/.*/${escapedEntry}\n&/" $path
}

function configure() {
    local path=$1
    local module=$2
    local envPrefix=$3

    local var
    local value

    echo "Configuring $module"
    for c in `printenv | perl -sne 'print "$1 " if m/^${envPrefix}_(.+?)=.*/' -- -envPrefix=$envPrefix`; do
        name=`echo ${c} | perl -pe 's/___/-/g; s/__/@/g; s/_/./g; s/@/_/g;'`
        var="${envPrefix}_${c}"
        value=${!var}
        echo " - Setting $name=$value"
        addProperty $path $name "$value"
    done
}

configure $HADOOP_CONF_DIR/core-site.xml core CORE_CONF
configure $HADOOP_CONF_DIR/hdfs-site.xml hdfs HDFS_CONF
configure $HADOOP_CONF_DIR/yarn-site.xml yarn YARN_CONF
configure $HADOOP_CONF_DIR/httpfs-site.xml httpfs HTTPFS_CONF
configure $HADOOP_CONF_DIR/kms-site.xml kms KMS_CONF
configure $HADOOP_CONF_DIR/mapred-site.xml mapred MAPRED_CONF
configure $HIVE_HOME/conf/hive-site.xml hive HIVE_SITE_CONF
configure $TEZ_HOME/conf/tez-site.xml tez TEZ_CONF
configure $HADOOP_CONF_DIR/capacity-scheduler.xml capacity-scheduler CAPACITY_CONF

if [ "$MULTIHOMED_NETWORK" = "1" ]; then
    echo "Configuring for multihomed network"

    # HDFS
    addProperty $HADOOP_CONF_DIR/hdfs-site.xml dfs.namenode.rpc-bind-host 0.0.0.0
    addProperty $HADOOP_CONF_DIR/hdfs-site.xml dfs.namenode.servicerpc-bind-host 0.0.0.0
    addProperty $HADOOP_CONF_DIR/hdfs-site.xml dfs.namenode.http-bind-host 0.0.0.0
    addProperty $HADOOP_CONF_DIR/hdfs-site.xml dfs.namenode.https-bind-host 0.0.0.0
    addProperty $HADOOP_CONF_DIR/hdfs-site.xml dfs.client.use.datanode.hostname true
    addProperty $HADOOP_CONF_DIR/hdfs-site.xml dfs.datanode.use.datanode.hostname true

    # YARN
    addProperty $HADOOP_CONF_DIR/yarn-site.xml yarn.resourcemanager.bind-host 0.0.0.0
    addProperty $HADOOP_CONF_DIR/yarn-site.xml yarn.nodemanager.bind-host 0.0.0.0
    addProperty $HADOOP_CONF_DIR/yarn-site.xml yarn.timeline-service.bind-host 0.0.0.0

    # MAPRED
    addProperty $HADOOP_CONF_DIR/mapred-site.xml yarn.nodemanager.bind-host 0.0.0.0
fi

echo "------------------------------"
echo "Add permission to gpadmin user"
echo "------------------------------"
useradd gpadmin
usermod -a -G root gpadmin

echo "--------------"
echo "Start namenode"
echo "--------------"
namedir=`echo $HDFS_CONF_dfs_namenode_name_dir | perl -pe 's#file://##'`
if [ ! -d $namedir ]; then
  echo "Namenode name directory not found: $namedir"
  exit 2
fi

if [ -z "$CLUSTER_NAME" ]; then
  echo "Cluster name not specified"
  exit 2
fi

echo "remove lost+found from $namedir"
rm -r $namedir/lost+found

if [ "`ls -A $namedir`" == "" ]; then
  echo "Formatting namenode name directory: $namedir"
  $HADOOP_HOME/bin/hdfs --config $HADOOP_CONF_DIR namenode -format $CLUSTER_NAME
fi
$HADOOP_HOME/bin/hdfs --config $HADOOP_CONF_DIR namenode &
wait_for_it hadoop:9870

echo "---------------"
echo "Start datenode"
echo "---------------"
datadir=`echo $HDFS_CONF_dfs_datanode_data_dir | perl -pe 's#file://##'`
if [ ! -d $datadir ]; then
  echo "Datanode data directory not found: $datadir"
  exit 2
fi
$HADOOP_HOME/bin/hdfs --config $HADOOP_CONF_DIR datanode &
wait_for_it hadoop:9864

echo "----------------------"
echo "Start resource manager"
echo "----------------------"
$HADOOP_HOME/bin/yarn --config $HADOOP_CONF_DIR resourcemanager &
wait_for_it hadoop:8088

echo "------------------"
echo "Start node manager"
echo "------------------"
$HADOOP_HOME/bin/yarn --config $HADOOP_CONF_DIR nodemanager &
wait_for_it hadoop:8042

# Add env for Hive
export HIVE_CONF_DIR=$HIVE_HOME/conf
export HADOOP_CLASSPATH=$TEZ_HOME/*:$TEZ_HOME/lib/*:$TEZ_HOME/conf/

echo "------------------------------"
echo "Check Hive metastore migration"
echo "------------------------------"
if $HIVE_HOME/bin/schematool -dbType postgres -validate | grep 'Done with metastore validation' | grep '[SUCCESS]'; then
  echo 'Database OK'
  return 0
else
  echo "------------------------------"
  echo "Start Hive metastore migration"
  echo "------------------------------"
  $HIVE_HOME/bin/schematool --verbose -dbType postgres -initSchema
fi

echo "-------------------------------"
echo "Put Tez library tarball on HDFS"
echo "-------------------------------"
hdfs dfs -mkdir /tez && hdfs dfs -put $TEZ_HOME/share/tez.tar.gz /tez/tez-0.10.3.tar.gz

echo "----------------------------"
echo "Start Hive metastore service"
echo "----------------------------"
$HIVE_HOME/bin/hive --service metastore &
wait_for_it hadoop:9083

echo "----------------------------"
echo "         Start Hive"
echo "----------------------------"
hdfs dfs -mkdir       /tmp
hdfs dfs -mkdir -p    /user/hive/warehouse
hdfs dfs -chmod g+w   /tmp
hdfs dfs -chmod g+w   /user/hive/warehouse

export HADOOP_CLIENT_OPTS="$HADOOP_CLIENT_OPTS -Dhive.log.dir=/tmp/hive -Dhive.log.file=hiveserver2.log"
$HIVE_HOME/bin/hiveserver2 --hiveconf hive.server2.enable.doAs=false &
wait_for_it hadoop:10000

echo "------------------------------"
echo " Start zookeeper"
echo "------------------------------"
$ZOOKEEPER_HOME/bin/zkServer.sh start &
wait_for_it hadoop:2181

echo "-----------------------------"
echo "Put PXF HBase library on HDFS"
echo "-----------------------------"
cp /share/pxf-hbase-lib-*.jar $HBASE_HOME_DIR/lib/

echo "----------------------------"
echo "        Start HBase"
echo "----------------------------"
echo 'export HBASE_DISABLE_HADOOP_CLASSPATH_LOOKUP=true' >> $HBASE_HOME_DIR/conf/hbase-env.sh
echo 'export HBASE_MANAGES_ZK=false' >> $HBASE_HOME_DIR/conf/hbase-env.sh
$HBASE_HOME_DIR/bin/start-hbase.sh &
wait_for_it hadoop:16000

tail -f $HBASE_HOME_DIR/logs/*