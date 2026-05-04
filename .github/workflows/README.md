# Greengage PXF CI

GitHub Actions CI pipeline for building, testing, and releasing
Greengage PXF components.

## Workflows

### `greengage-ci.yml` — Main CI

Triggered on push to `main`, any tag, and pull requests to any branch.
Concurrency control cancels outdated runs for the same branch or PR.

Defines two top-level env matrices shared across all jobs:

- `IMAGES` — list of Greengage Docker images to test against
- `TESTS`  — list of integration test profiles

Both are passed through the `generate-matrix` job as outputs,
since `env` is not accessible in `with` for reusable workflows.

#### Jobs

**`generate-matrix`**
Converts `IMAGES` and `TESTS` env variables into compressed JSON outputs
for use in dependent jobs.

**`build`**
Builds a Greengage DEB package using the first entry in `IMAGES`.
Uses: `.github/actions/build/deb`

**`regression`**
Runs PXF regression tests against all entries in `IMAGES`.
Uses: `.github/actions/tests/regression`

**`integration-0..3`**
Runs integration tests for each entry in `IMAGES`.
Uses: `greengage-reusable-tests-integration.yml`

---

### `greengage-reusable-tests-integration.yml` — Reusable Integration Tests

Called by `greengage-ci.yml` via `workflow_call`.

Accepts:

| Input     | Type   | Required | Description                     |
|-----------|--------|----------|---------------------------------|
| `image`   | string | yes      | Docker image prefix             |
| `version` | string | yes      | GGDB major version (`6` or `7`) |
| `tag`     | string | yes      | Docker image tag                |
| `tests`   | string | yes      | JSON test matrix                |

Builds a PXF automation image `{image}{version}_ubuntu_pxf_automation:{tag}`
from `ci/Dockerfile.integration` and runs each test profile from the `tests`
matrix via `automation/env/it.sh`.

Uploads artifacts as `integration-ggdb{version}-{tag}-{test}[-fdw][-ssl]`,
retained for 7 days.

---

### `greengage-release.yml` — Release

Triggered on GitHub release publication (`released`).

Uploads pre-built DEB packages to the release assets for each of:

- `6-stable`, `7-stable`, `6-devel`, `7-devel`

Uses: `greengagedb/greengage-ci/.github/actions/upload-pkgs-to-release@v10`

---

## Actions

### `build/deb`

See [`actions/build/deb/README.md`](.github/actions/build/deb/README.md).

### `tests/regression`

See [`actions/tests/regression/README.md`](.github/actions/tests/regression/README.md).

### `apt/sources`

See [`actions/apt/sources/README.md`](.github/actions/apt/sources/README.md).

---

## Image Matrix Format

```json
[
  { "version": "6", "image": "greengagedb/ggdb",                    "tag": "6.30.1"  },
  { "version": "7", "image": "greengagedb/ggdb",                    "tag": "7.4.0"   },
  { "version": "6", "image": "ghcr.io/greengagedb/greengage/ggdb",  "tag": "GG-246"  },
  { "version": "7", "image": "ghcr.io/greengagedb/greengage/ggdb",  "tag": "latest"  }
]
```

Full image name is composed as `{image}{version}_ubuntu:{tag}`, e.g.

- `greengagedb/ggdb6_ubuntu:6.30.1`
- `ghcr.io/greengagedb/greengage/ggdb7_ubuntu:latest`

---

## Testing PXF Against a Custom Greengage Branch

PXF and Greengage are sometimes developed in tandem: a PXF feature may depend
on complementary changes in a Greengage feature branch that are not yet merged
to the upstream branches `6.x` / `7.x`.

Greengage CI automatically builds and publishes a Docker image to GHCR for
every feature branch, using the branch name as the tag, e.g.
`ghcr.io/greengagedb/greengage/ggdb6_ubuntu:GG-246`.

The default `latest` images in GHCR are built from the upstream branches
`6.x` and `7.x` respectively.

To test a PXF feature branch against a specific Greengage feature branch,
replace the `tag` for the corresponding GHCR entry in the `IMAGES` matrix
in `greengage-ci.yml`:

```yaml
env:
  IMAGES: |
    [
      { "version": "6", "image": "greengagedb/ggdb",                    "tag": "6.30.1"  },
      { "version": "7", "image": "greengagedb/ggdb",                    "tag": "7.4.0"   },
      { "version": "6", "image": "ghcr.io/greengagedb/greengage/ggdb",  "tag": "GG-246"  },
      { "version": "7", "image": "ghcr.io/greengagedb/greengage/ggdb",  "tag": "latest"  }
    ]
```

`tag: "GG-246"` selects the image built from the Greengage feature branch
`GG-246` instead of the default `latest` built from `6.x`. All regression
and integration jobs for that matrix entry will use this image.

Once both branches are merged, revert the tag back to `latest`.
