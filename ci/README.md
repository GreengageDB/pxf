# Regression tests

## How to build PXF Docker image

From the root pxf folder run:

```bash
docker build --target test -t ggdb6_pxf_regression:latest -f ci/Dockerfile.regression .
```

or `build_local.sh` script building regression tests image `ggdb6_pxf_regression:latest` by default:

```bash
bash build_local.sh
```

For default Docker will use image `greengagedb/ggdb6_ubuntu:6.29.0` (see ARG `GGDB_IMAGE` in the `Dockerfile`). It may be changed by the `--build-arg` param:

```bash
docker build --target test -t ggdb7_pxf_regression:latest --build-arg GGDB_IMAGE="ggdb7_u22:latest" -f ci/Dockerfile.regression .
```

This will build an image called `ggdb6_pxf_regression` with the tag `latest`. This image is based on `ggdb6_pxf_regression:latest`, which additionally contains pxf sources and pxf artifacts tarball in `/tmp/build/pxf_src` and `/tmp/build/pxf_tarball` folders respectively.

## How to test PXF

During the image building phase `compile_pxf.bash` script additionally calls `test` make target, which calls `make -C cli/go/src/pxf-cli test` and `make -C server test` commands.
To additionally test `fdw` and `external-table` parts you may call:

```bash
docker run --rm -it \
  --privileged --sysctl kernel.sem="500 1024000 200 4096" \
  ggdb6_pxf_regression:latest /tmp/build/pxf_src/ci/test_in_docker.sh
```

And the same for adb 7.x:

```bash
docker run --rm -it \
  --privileged --sysctl kernel.sem="500 1024000 200 4096" \
  ggdb7_pxf_regression:latest /tmp/build/pxf_src/ci/test_in_docker.sh
```

## How to test PXF with TLS support

To test PXF with TLS we build PXF with Dockerfile which has PXF set up with SSL support:

For adb 6.x images:

```bash
docker build -t ggdb6_pxf_regression_ssl:latest -f ci/Dockerfile.regression .
```

To additionally test `fdw` and `external-table` parts you may call:

```bash
docker run --rm -it \
  --privileged --sysctl kernel.sem="500 1024000 200 4096" \
  ggdb6_pxf_regression_ssl:latest /tmp/build/pxf_src/ci/test_in_docker.sh
```

For adb 7.x images:

```bash
docker build -t ggdb7_pxf_regression_ssl:latest --build-arg GGDB_IMAGE="ggdb7_u22:latest" -f ci/Dockerfile.regression .
```

To additionally test `fdw` and `external-table` parts you may call:

```bash
docker run --rm -it \
  --privileged --sysctl kernel.sem="500 1024000 200 4096" \
  ggdb7_pxf_regression_ssl:latest /tmp/build/pxf_src/ci/test_in_docker.sh
```
