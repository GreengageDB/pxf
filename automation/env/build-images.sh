#!/bin/bash

echo "=============================="
echo "      Clean the project       "
echo "=============================="
pushd ../../server
./gradlew clean
popd

# # Uncomment this section if image is not available in the docker registry
# echo "===================================="
# echo "      Build Hadoop 3.3.6 image      "
# echo "===================================="
# pushd hadoop
# docker build -f Dockerfile -t greengagedb/pxf-hadoop:3.3.6 .
# popd

# #echo "===================================="
# #echo "      Build Vault image      "
# #echo "===================================="
# docker build -f ./vault/Dockerfile -t greengagedb/pxf-vault-test .

echo "=============================="
echo "Build PXF image for automation"
echo "=============================="
# GGDB_IMAGE is from https://github.com/GreengageDB/greengage/tree/main/ci
pushd ../..
docker build -t greengagedb/ggdb6_pxf_automation --build-arg "GGDB_IMAGE=${GGDB_IMAGE:-greengagedb/ggdb6_ubuntu:6.29.1}" -f automation/env/Dockerfile .
popd
