#!/usr/bin/env bash

# Main
export IFS=","

dbid=2 # dbid=1 is a master segment
segid=0

is_mirrored=$DOCKER_GG_WITH_MIRROR
primary_segments_per_host=$DOCKER_GG_PRIMARY_SEGMENTS_PER_HOST

# Wait Vault service
if [[ "$PXF_VAULT_ENABLED" = true ]]; then
    echo "--------------------------"
    echo "Wait Vault service for PXF"
    echo "--------------------------"
    role_id_file="/vault/env/role_id"
    secret_id_file="/vault/env/secret_id"
    while [ ! -f "$role_id_file" ] || [ ! -f "$secret_id_file" ]; do
      echo "Waiting for vault init approle envs"
      sleep 1
    done
    # Read the role_id and secret_id from the shared volume
    export PXF_VAULT_ROLE_ID=$(cat "$role_id_file")
    export PXF_VAULT_SECRET_ID=$(cat "$secret_id_file")
    echo "Vault environment were initialized successfully"
fi

# Base config
CONFIG="ARRAY_NAME='Demo Greengage Cluster'
TRUSTED_SHELL=ssh
CHECK_POINT_SEGMENTS=8
ENCODING=UNICODE
SEG_PREFIX=seg
HEAP_CHECKSUM=on
HBA_HOSTNAMES=0
QD_PRIMARY_ARRAY=$DOCKER_GG_MASTER_SERVER~$DOCKER_GG_MASTER_SERVER~5432~/data1/master/gpseg-1~1~-1
declare PRIMARY_ARRAY=("

# Add primary segments to the config
for server in $DOCKER_GG_SEGMENT_SERVERS
do
  primary_port=6001
  for (( i=1 ; i<=$primary_segments_per_host ; i++ ));
  do
      echo -e -n "primary: host=$server; dbid=$dbid; segid=$segid, port=$primary_port\n"
      CONFIG+="$server~$server~$primary_port~/data1/primary/gpseg$segid~$dbid~$segid "
      ((dbid++))
      ((segid++))
      ((primary_port++))
  done
done
CONFIG+=")"

# Add mirror segments to the config
if [ -z "$is_mirrored" ] || [ "$is_mirrored" == "true" ]; then
  ((segid--))
  CONFIG+=" declare MIRROR_ARRAY=("
  for server in $DOCKER_GG_SEGMENT_SERVERS
  do
    mirror_port=7001
    for (( i=1 ; i<=$primary_segments_per_host ; i++ ));
    do
        echo -e -n "mirror : host=$server; dbid=$dbid; segid=$segid, port=$mirror_port\n"
        CONFIG+="$server~$server~$mirror_port~/data1/mirror/gpseg$segid~$dbid~$segid "
        ((dbid++))
        ((mirror_port++))
        ((segid--))
    done
  done
  CONFIG+=")"
  echo "------------------------------------"
  echo "Run cluster with mirrors. Config is:"
  echo "------------------------------------"
  echo -e "$CONFIG"
else
  echo "---------------------------------------"
  echo "Run cluster without mirrors. Config is:"
  echo "---------------------------------------"
  echo -e "$CONFIG"
fi

BASH_PROFILE="export PGPORT=5432
  export MASTER_DATA_DIRECTORY=/data1/master/gpseg-1
  source /usr/local/greengage-db-devel/greengage_path.sh
  export GPHOME=/usr/local/greengage-db-devel
  export JAVA_HOME=\$(readlink -f /usr/bin/java | sed 's:bin/java::')
  export PXF_HOME=/usr/local/greengage-db-devel/pxf
  export GPHD_ROOT=/home/gpadmin/workspace/singlecluster
  export TEST_HOME=/home/gpadmin/workspace/pxf/automation
  # Maven variables
  export M2_HOME=/opt/maven
  export MAVEN_HOME=/opt/maven
  PATH=/usr/lib/gpdb/bin/:/usr/local/greengage-db-devel/bin/:/usr/local/greengage-db-devel/pxf/bin:/opt/maven/bin:\$PATH
  export PATH"

# Run sshd
bash -c "/usr/sbin/sshd"

# Change the owner
chown -R gpadmin:gpadmin /home/gpadmin/.m2/

# Get ssh public keys of hosts
echo "----------------------------"
echo "Get ssh public keys of hosts"
echo "----------------------------"
keys=()
max_iterations=10
wait_seconds=3
iterations=0

servers=()
for server in $DOCKER_GG_CLUSTER_HOSTS; do
  servers+=("$server")
done

while true
do
  ((iterations++))
  echo "Get public key. Attempt $iterations"
  status=0
  toremove=()
  for server in "${servers[@]}"
  do
      echo "Get public key for $server"
      key=$(ssh-keyscan -t ssh-rsa $server)
      if [ $? -ne 0 ] ||  [[ $key != *"ssh-rsa"* ]]; then
        echo "Server $server doesn't have the public key yet. We will try again..."
        status=1
        break
      else
        echo "Add key for server $server"
        keys+=("$key")
        toremove+=("$server")
      fi
  done
  if [ $status -eq 0 ]; then
    echo "All servers have public key"
    break
  elif [ "$iterations" -ge "$max_iterations" ]; then
    echo "Error to get public key for some server after $max_iterations tries. Exit from script!"
    exit 1
  else
    for server in "${toremove[@]}"; do
      for i in "${!servers[@]}"; do
        if [[ ${servers[i]} = "$server" ]]; then
          unset 'servers[i]'
        fi
      done
    done
    echo "The following servers don't have the key yet: ${servers[*]}"
    echo "Wait $wait_seconds seconds and try again to get public key"
    sleep $wait_seconds
  fi
done

# Create config files
echo "----------------------------------------------"
echo "Copy keys, set bash profile and create configs"
echo "----------------------------------------------"
for key in "${keys[@]}"
do
  bash -c "echo $key >> /home/gpadmin/.ssh/known_hosts"
done
bash -c "cat /root/.ssh/id_rsa > /home/gpadmin/.ssh/id_rsa && cat /root/.ssh/id_rsa.pub > /home/gpadmin/.ssh/id_rsa.pub && cat /root/.ssh/authorized_keys > /home/gpadmin/.ssh/authorized_keys"
bash -c "echo \"$CONFIG\" > /home/gpadmin/gpdb_src/gpAux/gpdemo/create_cluster.conf"
bash -c "echo \"$BASH_PROFILE\" > /home/gpadmin/.bash_profile && echo \"$BASH_PROFILE\" > /home/gpadmin/.bashrc &&
 chown gpadmin:gpadmin /home/gpadmin/.bash_profile && chown gpadmin:gpadmin /home/gpadmin/.bashrc"

# Create linux cgroup for GP
echo "--------------------------"
echo "Create linux cgroup for GP"
echo "--------------------------"
bash -c "mkdir -p /sys/fs/cgroup/{memory,cpu,cpuset,cpuacct}/gpdb"
bash -c "chmod -R 777 /sys/fs/cgroup/{memory,cpu,cpuset,cpuacct}/gpdb"
bash -c "chown -R gpadmin:gpadmin /sys/fs/cgroup/{memory,cpu,cpuset,cpuacct}/gpdb"

echo "-----------------------------------"
echo "Remove sudo permission from gpadmin"
echo "-----------------------------------"
bash -c "sed -i 's/gpadmin ALL = NOPASSWD : ALL/# gpadmin ALL = NOPASSWD : ALL/g' /etc/sudoers"

# Install cluster
if [ "$HOSTNAME" == "$DOCKER_GG_MASTER_SERVER" ]; then
  if ! [ -d "/data1/master/gpseg-1" ]; then
    echo "----------------------------------"
    echo "Run Greengage cluster installation"
    echo "----------------------------------"

    echo "---------------------------------"
    echo "Check ssh connection to the hosts"
    echo "---------------------------------"
    max_iterations=20
    wait_seconds=5
    iterations=0
    while true
    do
      ((iterations++))
      echo "Check SSH connection. Attempt $iterations"
      status=0
      for server in $DOCKER_GG_CLUSTER_HOSTS
      do
          echo "Check SSH connection to the $server"
          sudo -H -u gpadmin bash -c -l "ssh -o PasswordAuthentication=no $server 'exit'"
          if ! [ $? -eq 0 ]; then
            echo "Server $server is not available for ssh connection. We will try again..."
            status=1
            break
          fi
      done
      if [ $status -eq 0 ]; then
        echo "All Greengage servers are available for SSH connection"
        break
      elif [ "$iterations" -ge "$max_iterations" ]; then
        echo "Error to connect to some Greengage server via SSH after $max_iterations tries. Exit from script!"
        exit 1
      else
        echo "Wait $wait_seconds seconds and try again to connect to the servers"
        sleep $wait_seconds
      fi
    done

    echo "-------------------------"
    echo "Install Greengage cluster"
    echo "-------------------------"
    sudo -H -u gpadmin bash -c -l "/usr/local/greengage-db-devel/bin/gpinitsystem -a -I /home/gpadmin/gpdb_src/gpAux/gpdemo/create_cluster.conf -l /home/gpadmin/gpAdminLogs"

    echo "-------------------------------------"
    echo "Check connection to Greengage cluster"
    echo "-------------------------------------"
    result="$( sudo -H -u gpadmin bash -c -l "psql -d postgres -Atc 'SELECT 1;'" )"
    if [ "${result}" == "1" ]; then
      echo "--------------------------------------------"
      echo "Fantastic!!! Greengage cluster is available!"
      echo "--------------------------------------------"
      if ! [[ -z "$DOCKER_GG_STANDBY_SERVER" ]]; then
            # Activate standby master server
            echo "------------------------------"
            echo "Activate standby master server"
            echo "------------------------------"
            sudo -H -u gpadmin bash -c -l "/usr/local/greengage-db-devel/bin/gpinitstandby -a -s $DOCKER_GG_STANDBY_SERVER"
      fi
    else
      echo "-------------------------------------"
      echo "Error to connect to Greengage cluster"
      echo "-------------------------------------"
      exit 1;
    fi

   echo "-------------------"
   echo "Configure Greengage"
   echo "-------------------"
   sudo -H -u gpadmin bash -c -l "psql -d postgres -Atc 'CREATE EXTENSION IF NOT EXISTS pxf;' &&
       psql -d postgres -Atc 'CREATE EXTENSION IF NOT EXISTS pxf_fdw;' &&
       echo 'local all testuser trust' >> /data1/master/gpseg-1/pg_hba.conf &&
       echo 'host all gpadmin 0.0.0.0/0 trust' >> /data1/master/gpseg-1/pg_hba.conf &&
       echo 'host all all 0.0.0.0/0 md5' >> /data1/master/gpseg-1/pg_hba.conf"
   sudo -H -u gpadmin bash -c -l "gpconfig -c gp_resource_manager -v group"
   sudo -H -u gpadmin bash -c -l "gpstop -aM fast && gpstart -a"
  else
    echo "-------------------------"
    echo "Starting Greengage server"
    echo "-------------------------"
    sudo -H -u gpadmin bash -c -l "gpstart -a"
    result="$( sudo -H -u gpadmin bash -c -l "psql -d postgres -Atc 'SELECT 1;'" )"
    if [ "${result}" == "1" ]; then
      echo "--------------------------------------------"
      echo "Fantastic!!! Greengage cluster is available!"
      echo "--------------------------------------------"
    else
      echo "-------------------------------------"
      echo "Error to connect to Greengage cluster"
      echo "-------------------------------------"
      exit 1;
    fi
  fi
fi

echo "------------------------------------"
echo "Prepare configs for automation tests"
echo "------------------------------------"
sudo -H -u gpadmin bash -c -l "HIVE_SERVER_HOST=$HIVE_SERVER_HOST JDBC_HOST=$DOCKER_GG_MASTER_SERVER make -C ~/workspace/pxf/automation  sync_jdbc_config"
sudo -H -u gpadmin bash -c -l "PROTOCOL=s3 MINIO=true ACCESS_KEY_ID=$MINIO_ACCESS_KEY SECRET_ACCESS_KEY=$MINIO_SECRET_KEY make -C ~/workspace/pxf/automation  sync_cloud_configs"

# Start PXF
for server in $DOCKER_GG_CLUSTER_HOSTS
do
  echo "---------"
  echo "Start PXF"
  echo "---------"
  # Init Vault environment
  if [[ "$PXF_VAULT_ENABLED" = true ]]; then
      echo "----------------------------------------"
      echo "Init Vault env variables for PXF service"
      echo "----------------------------------------"
      ksh -c env | grep -E 'PXF_VAULT' | sed 's/^/export /' >> /home/gpadmin/.bash_profile
      ksh -c env | grep -E 'PXF_VAULT' | sed 's/^/export /' >> /home/gpadmin/.bashrc
  fi
  # Init SSL environment
  if [[ "$PXF_PROTOCOL" = "https" ]]; then
    echo "--------------------------------------"
    echo "Init SSL env variables for PXF service"
    echo "--------------------------------------"
    ksh -c env | grep -E 'PXF_SSL|PXF_HOST|PXF_PROTOCOL' | sed 's/^/export /' >> /home/gpadmin/.bash_profile
    ksh -c env | grep -E 'PXF_SSL|PXF_HOST|PXF_PROTOCOL' | sed 's/^/export /' >> /home/gpadmin/.bashrc
  fi

  if [ "$HOSTNAME" == "$DOCKER_GG_MASTER_SERVER" ]; then
    if [ -d "/data1/master/gpseg-1/pg_log" ]; then
      log_dir="pg_log"
    else
      log_dir="log"
    fi
    sudo -H -u gpadmin bash -c -l "pxf start && tail -f /data1/master/gpseg-1/${log_dir}/gpdb-*.csv"
  else
    sudo -H -u gpadmin bash -c -l "pxf start && tail -f /usr/local/greengage-db-devel/pxf/logs/pxf-service.log"
  fi
done
