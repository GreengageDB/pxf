#!/bin/bash
# GGDB_IMAGE is from https://github.com/GreengageDB/greengage/tree/main/ci
export GGDB_IMAGE=${GGDB_IMAGE:-greengagedb/ggdb6_ubuntu:6.29.1}
export IT_IMAGE=${IT_IMAGE:-greengagedb/ggdb6_pxf_automation}
export IT_TAG=${IT_TAG:-it}

export SCRIPT_DIR=${SCRIPT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)}

# Uncomment this section if image is not available in the docker registry
# echo "===================================="
# echo "      Build Hadoop 3.3.6 image      "
# echo "===================================="
#pushd $SCRIPT_DIR/hadoop
#docker build -f Dockerfile -t pxf-hadoop:3.3.6 .
#popd

# #echo "===================================="
# #echo "      Build Vault image      "
# #echo "===================================="
# docker build -f $SCRIPT_DIR/vault/Dockerfile -t greengagedb/pxf-vault-test .

echo "=============================="
echo "Build PXF image for automation"
echo "=============================="
pushd $SCRIPT_DIR/../..
mkdir -p .cache
docker build --no-cache -t "$IT_IMAGE:$IT_TAG" --build-arg GGDB_IMAGE -f ci/Dockerfile.integration . ; exit_code=$?
popd
exit $exit_code
