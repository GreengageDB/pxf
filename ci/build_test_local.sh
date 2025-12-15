#!/bin/bash
# Local build Docker image for Regression (default) or Intregration tests

type=${1:-regression}

export GOPATH=/opt/go
export DEV_HOME=/home/gpadmin
export GPHOME=/usr/local/greengage-db-devel
export PXF_HOME=$GPHOME/pxf
export GGDB_IMAGE=ghcr.io/greengagedb/greengage/ggdb6_ubuntu:latest
export PATH=$GPHOME/bin:$PXF_HOME/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/local/go/bin:$GOPATH/bin

docker build \
  --build-arg GGDB_IMAGE \
  --build-arg DEV_HOME \
  --build-arg PXF_HOME \
  --build-arg GPHOME \
  --build-arg GOPATH \
  --build-arg PATH \
  -f "ci/Dockerfile.$type" \
  -t "ggdb6_pxf_$type:latest" \
  .
