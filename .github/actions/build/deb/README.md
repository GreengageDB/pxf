# build/deb

Composite action. Builds a Greengage DEB package inside Docker and tests its installation.

## Inputs

| Input              | Required | Default            | Description                                                     |
|--------------------|----------|--------------------|-----------------------------------------------------------------|
| `version`          | yes      | —                  | GGDB major version (`6` or `7`)                                 |
| `tag`              | yes      | —                  | Docker image tag and release version (e.g. `6.30.1`)            |
| `image`            | no       | `greengagedb/ggdb` | Docker image prefix (e.g. `ghcr.io/greengagedb/greengage/ggdb`) |
| `target_os`        | no       | `ubuntu`           | Target OS for installation test (e.g. `ubuntu`)                 |
| `target_os_version`| no       | `22.04`            | Target OS version (e.g. `22.04`, `24.04`)                       |

The base Greengage image is composed as `{image}{version}_ubuntu:{tag}`,
e.g. `greengagedb/ggdb6_ubuntu:6.30.1`.

The `greengage` DEB package is downloaded from GitHub Releases as
`https://github.com/GreengageDB/greengage/releases/download/{tag}/greengage{version}.deb`.

## Usage

```yaml
- uses: ./.github/actions/build/deb
  with:
    version: '6'
    tag:     '6.30.1'
    image:   'ghcr.io/greengagedb/greengage/ggdb'
```

## Output

Uploads built packages as `deb-packages-{target_os}{target_os_version}-{tag}` and saves them to cache.
