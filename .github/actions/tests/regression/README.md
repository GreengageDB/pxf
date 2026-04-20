# tests/regression

Composite action. Builds a PXF regression Docker image and runs regression tests inside it.

## Inputs

| Input     | Required | Default            | Description                                                     |
|-----------|----------|--------------------|-----------------------------------------------------------------|
| `version` | yes      | —                  | GGDB major version (`6` or `7`)                                 |
| `tag`     | no       | `latest`           | Docker image tag (e.g. `6.30.1` or `latest`)                    |
| `image`   | no       | `greengagedb/ggdb` | Docker image prefix (e.g. `ghcr.io/greengagedb/greengage/ggdb`) |

The base Greengage image is composed as `{image}{version}_ubuntu:{tag}`,
e.g. `ghcr.io/greengagedb/greengage/ggdb6_ubuntu:6.30.1`.

## Usage

```yaml
- uses: ./.github/actions/tests/regression
  with:
    version: '6'
    tag:     '6.30.1'
    image:   'ghcr.io/greengagedb/greengage/ggdb'
```

## Output

Uploads regression diff artifacts as `regression-ggdb{version}-{tag}`, retained for 7 days.
