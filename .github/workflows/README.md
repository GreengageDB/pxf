# Greengage CI Pipeline

This repository includes a GitHub Actions–based continuous integration (CI) workflow for building, testing, and validating Greengage PXF components.

## Overview

The workflow (`.github/workflows/greengage-ci.yml`) defines three stages:

1. **Build** — compiles and packages PXF into Debian (`.deb`) packages.  
2. **Integration** — runs integration tests under multiple configurations.  
3. **Regression** — performs regression tests on multiple Docker images.

The workflow is automatically triggered on:
- Push events to the `main` branch.
- Pull requests for all branches.

Concurrency control is configured to cancel outdated runs for the same branch or pull request.

## Build Stage

**Job:** `build`

This stage produces PXF Debian packages using multiple container images.

- Images used:
  - `greengagedb/ggdb6_ubuntu:6.29.1` - a public, tagged stable image of GreengageDB that includes development tools and source code.
  - `ghcr.io/greengagedb/greengage/ggdb6_ubuntu` - the latest development image, automatically built from the most recent pull request.
- Build steps:
  1. Execute `ci/build_in_docker.sh` within the container.
  2. Run `make deb` to build the package.
  3. Cache generated `.deb` files for reuse.
  4. Upload resulting artifacts to GitHub Actions for later stages.

Artifacts are named according to the image version, for example:
```

pxf-deb-dh-6.29.1
pxf-deb-ghcr-latest

```

## Integration Stage

**Job:** `integration`

This stage runs automated integration tests across several configurations.

Test profiles include:
- `smoke`
- `gpdb`
- `jdbc`
- Variants with FDW (Foreign Data Wrapper)
- Variants with SSL enabled

Each configuration:
- Builds its own test Docker image (`greengagedb/ggdb6_pxf_automation`).
- Executes test scripts located in `automation/env/it.sh`.
- Collects and uploads logs and test artifacts.

Artifacts are uploaded as:
```

artifacts-integration-<test>[-fdw][-ssl]

```

Artifacts are retained for 7 days.

## Regression Stage

**Job:** `regression`

This stage runs regression tests against multiple base images to verify compatibility and detect regressions.

Images tested:
- `greengagedb/ggdb6_ubuntu:6.29.1`
- `ghcr.io/greengagedb/greengage/ggdb6_ubuntu:latest`

Procedure:
1. Build the regression image (`greengagedb/ggdb6_pxf_regression:<version>`).
2. Execute the regression suite inside the container using `ci/test_in_docker.sh`.
3. Collect and upload `.diffs` and log files.

Artifacts are uploaded under:
```

logs_regression-<version>

```

Retention period: 7 days.

## Environment Variables

| Variable | Description |
|-----------|-------------|
| `DEV_HOME` | Developer home directory inside the container |
| `PXF_HOME` | PXF installation directory |
| `GPHOME` | Greenplum/Greengage installation directory |
| `GOPATH` | Go workspace path |
| `PROFILE`, `GROUP` | Integration test identifiers |
| `USE_FDW`, `USE_SSL` | Flags to enable FDW and SSL testing |
| `GGDB_IMAGE`, `IT_IMAGE`, `IT_TAG` | Docker image parameters for integration testing |
| `JAVA_TOOL_OPTIONS` | JVM encoding settings |
| `DEBIAN_FRONTEND` | Noninteractive mode for package installation |

## Execution Environment

- All jobs run on GitHub-hosted `ubuntu-latest` shared runners.
- The workflow relocates Docker storage to maximize available disk space.
- Each test and regression job executes in a containerized environment to ensure reproducibility.

## Artifacts and Retention

All stages upload build and test artifacts using `actions/upload-artifact@v4`.  
Artifacts are stored for seven days unless otherwise specified.  
Failed jobs also upload artifacts for debugging purposes.

## Maintenance Notes

- The workflow uses concurrency grouping to prevent redundant runs.
