#!/bin/bash
export DEV_HOME=/home/gpadmin
export PXF_HOME=/usr/local/pxf
export GPHOME=/usr/local/greengage-db-devel
export GGDB_IMAGE=ghcr.io/greengagedb/greengage/ggdb6_ubuntu:latest
export PATH=$GPHOME/bin:$PXF_HOME/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/local/go/bin

docker build \
  --build-arg GGDB_IMAGE \
  --build-arg DEV_HOME \
  --build-arg PXF_HOME \
  --build-arg GPHOME \
  --build-arg PATH \
  -f ci/Dockerfile.new \
  -t pxf_test:test \
  .
