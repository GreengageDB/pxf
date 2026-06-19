# Greengage PXF CI

GitHub Actions CI pipeline for building, testing, and releasing
Greengage PXF components.

## Workflows

### `greengage-ci.yml` — Main CI

Triggered on push to `main`, any tag, and pull requests to any branch.
Concurrency control cancels outdated runs for the same branch or PR.

> **Note:** Regression and integration tests run on pull requests only.
> A branch cannot be merged without passing all PR checks, so running
> tests again on push or tag would be redundant.

Defines two top-level env matrices shared across all jobs:

- `MATRIX_BUILDS` — list of Greengage build configurations
- `MATRIX_TESTS`  — list of integration test profiles

Both are passed through the `generate-matrix` job as compressed JSON outputs,
since `env` is not directly accessible in `with` blocks for reusable workflows.

#### Jobs

**`generate-matrix`**
Converts `MATRIX_BUILDS` and `MATRIX_TESTS` env variables into compact JSON
outputs for use in dependent jobs.

**`package`**
Builds Greengage PXF deb packages for each entry in `MATRIX_BUILDS`.
Runs on every push and PR.
Uses: `greengage-reusable-package.yml`

**`regression`** _(PR only)_
Runs PXF regression tests against all entries in `MATRIX_BUILDS`.
Uses: `greengage-reusable-tests-regression.yml`

**`integration`** _(PR only)_
Runs integration tests for each entry in `MATRIX_BUILDS`, passing the full
`MATRIX_TESTS` matrix into each integration workflow instance.
Uses: `greengage-reusable-tests-integration.yml`

---

### `greengage-reusable-package.yml` — Reusable Package Builds

Called by `greengage-ci.yml` via `workflow_call`.

Accepts a `builds` JSON string and iterates over it as a matrix.

For each matrix entry, builds PXF deb packages inside a Greengage Docker
image and uploads them as artifacts named
`deb-packages-pxf{version}-{target_os}{target_os_version}[-devel][-tag]`.

#### Matrix entry fields

| Field               | Required | Description                                        |
|---------------------|----------|----------------------------------------------------|
| `version`           | yes      | GGDB major version (`6` or `7`)                    |
| `target_os`         | yes      | Target OS (`ubuntu`)                               |
| `target_os_version` | yes      | Target OS version (`22.04`, `24.04`)               |
| `release`           | yes      | `prod` (DockerHub) or `devel` (GHCR)               |
| `tag`               | no       | Custom Greengage branch or tag (default: `latest`) |
| `skip_package`      | no       | Set to `"true"` to skip the build for this entry   |

#### `skip_package` flag

Some Greengage versions do not yet have all required dependencies available
in the apt repository (e.g. `greengage7`). For such entries, set
`"skip_package": "true"` to keep the matrix entry active for tests while
skipping the package build step.

#### Custom Greengage branch or tag (`tag`)

By default, the `latest` image tag is used. To test PXF against a specific
Greengage feature branch or release, set `tag` to the branch name or version
tag. Greengage CI automatically publishes a Docker image to GHCR for every
feature branch using the branch name as the tag.

---

### `greengage-reusable-tests-regression.yml` — Reusable Regression Tests

Called by `greengage-ci.yml` via `workflow_call`.

Accepts a `builds` JSON string, iterates over it as a matrix, and runs PXF
regression tests for each entry. Builds a regression Docker image from
`ci/Dockerfile.regression` and runs `ci/test_in_docker.sh` inside it.

Uploads diff artifacts as
`regression-ggdb{version}_{target_os}{target_os_version}-{release}[-tag]`,
retained for 7 days.

---

### `greengage-reusable-tests-integration.yml` — Reusable Integration Tests

Called by `greengage-ci.yml` via `workflow_call`.

Accepts build parameters and a `tests` JSON string. Iterates over the tests
matrix and runs each integration test profile against the specified Greengage
image.

| Input               | Required | Default   | Description                          |
|---------------------|----------|-----------|--------------------------------------|
| `version`           | yes      |           | GGDB major version (`6` or `7`)      |
| `target_os`         | yes      |           | Target OS (`ubuntu`)                 |
| `target_os_version` | yes      |           | Target OS version (`22.04`, `24.04`) |
| `release`           | yes      |           | `prod` or `devel`                    |
| `tests`             | yes      |           | JSON string with test matrix         |
| `tag`               | no       | `latest`  | Custom Greengage branch or tag       |

Builds a PXF automation image from `ci/Dockerfile.integration` and runs
tests via `automation/env/it.sh`.

Uploads artifacts as
`integration-ggdb{version}_{target_os}{target_os_version}-{release}[-tag]-{test}[-fdw][-ssl]`,
retained for 7 days.

---

### `greengage-release.yml` — Release

Triggered on GitHub release publication (`released`).

Uploads pre-built PXF deb packages to the release assets.

---

## Build Matrix Format

```json
[
  { "version": "6", "target_os": "ubuntu", "target_os_version": "22.04", "release": "prod"  },
  { "version": "6", "target_os": "ubuntu", "target_os_version": "22.04", "release": "devel" },
  { "version": "6", "target_os": "ubuntu", "target_os_version": "24.04", "release": "prod"  },
  { "version": "6", "target_os": "ubuntu", "target_os_version": "24.04", "release": "devel" }
]
```

The Docker image name is composed as:

- `prod`:  `greengagedb/ggdb{version}_{target_os}[{target_os_version}]:{tag}`
- `devel`: `ghcr.io/greengagedb/greengage/ggdb{version}_{target_os}[{target_os_version}]:{tag}`

The OS version suffix is omitted for `22.04` (the default), e.g.:

- `greengagedb/ggdb6_ubuntu:latest`
- `greengagedb/ggdb6_ubuntu24.04:latest`

---

## Testing PXF Against a Custom Greengage Branch

PXF and Greengage are sometimes developed in tandem: a PXF feature may depend
on complementary changes in a Greengage feature branch not yet merged upstream.

Greengage CI automatically builds and publishes a Docker image to GHCR for
every feature branch using the branch name as the tag, e.g.
`ghcr.io/greengagedb/greengage/ggdb6_ubuntu:GG-559`.

To test against a specific Greengage branch, set `tag` in the matrix entry:

```yaml
env:
  MATRIX_BUILDS: |
    [
      { "version": "6", "target_os": "ubuntu", "target_os_version": "22.04", "release": "devel", "tag": "GG-559" },
      { "version": "6", "target_os": "ubuntu", "target_os_version": "22.04", "release": "prod"  },
      { "version": "6", "target_os": "ubuntu", "target_os_version": "24.04", "release": "prod"  },
      { "version": "6", "target_os": "ubuntu", "target_os_version": "24.04", "release": "devel" }
    ]
```

Once both branches are merged, remove the `tag` field to revert to `latest`.

---

## Disabling Package Build for Specific Entries

When a Greengage version is not yet available in the apt repository, or when
you only need to run tests without building packages, use `skip_package`:

```yaml
env:
  MATRIX_BUILDS: |
    [
      { "version": "8", "target_os": "ubuntu", "target_os_version": "22.04", "release": "devel", "tag": "GG-399", "skip_package": "true" },
      { "version": "7", "target_os": "ubuntu", "target_os_version": "22.04", "release": "prod",  "skip_package": "true" },
      { "version": "6", "target_os": "ubuntu", "target_os_version": "22.04", "release": "prod"  },
      { "version": "6", "target_os": "ubuntu", "target_os_version": "24.04", "release": "prod"  }
    ]
```

The matrix entry remains active for regression and integration tests —
only the package build and upload steps are skipped.
