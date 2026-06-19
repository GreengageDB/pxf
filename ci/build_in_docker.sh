#!/bin/bash
# ci/build_in_docker.sh
#
# Fot build in docker using stable Greengage images:
# image=greengagedb/ggdb6_ubuntu:6.31.0
# image=greengagedb/ggdb6_ubuntu24:6.31.0
# image=greengagedb/ggdb7_ubuntu:7.4.1
#
# or developer Greengage images:
# image=ghcr.io/greengagedb/greengage/ggdb6_ubuntu:latest
# image=ghcr.io/greengagedb/greengage/ggdb7_ubuntu:latest

# shellcheck disable=SC2086

set -eu

: "${GPHOME:?GPHOME must be set}"

export GREENGAGE_VERSION=${GREENGAGE_VERSION:-6}

export JAVA_TOOL_OPTIONS=${JAVA_TOOL_OPTIONS:-'-Dfile.encoding=UTF8'}
export DEBIAN_FRONTEND=${DEBIAN_FRONTEND:-noninteractive}

export GOPATH=/opt/go
export PATH=$PATH:/usr/local/go/bin:$GOPATH/bin

export GREENGAGE_PACKAGE=${GREENGAGE_PACKAGE:-greengage$GREENGAGE_VERSION}
export GREENGAGE_REPO_URL=${GREENGAGE_REPO_URL:-'https://greengagedb.org'}

# SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# "$SCRIPT_DIR/set_azure_sources_list.sh"

# Configure
git config --global --add safe.directory "$(pwd)"

update-locale LANG=en_US.UTF-8
localedef -c -i ru_RU -f CP1251 ru_RU.CP1251

# Install packages from apt
echo -n "Installing packages via apt... "
{
  # Bugfix: developers image 24.04 contains sources.list for 22.04
  # shellcheck disable=SC1091 # External source
  source /etc/os-release
  echo "deb [signed-by=/etc/apt/keyrings/greengagedb.gpg] \
  ${GREENGAGE_REPO_URL}/repositories/ubuntu/${VERSION_ID}/x86_64 greengagedb main" \
    > /etc/apt/sources.list.d/greengagedb.list
  apt-get -yq update
  apt-get -yq install --no-install-recommends openjdk-17-jdk "$GREENGAGE_PACKAGE"
  apt-get clean
} 1>/dev/null ; echo "Done"

# Install Golang
go_version=$(grep -E '^go [0-9]+\.[0-9]+\.[0-9]+' cli/go.mod | cut -d' ' -f2)
installed_go_version=$(go version 2>/dev/null | grep -Eo 'go[0-9]+\.[0-9]+\.[0-9]+' | tr -d 'go')

echo -n "Golang version $go_version "
if [ "$go_version" != "$installed_go_version" ] ; then
  echo "not found"
  if [ -n "$installed_go_version" ] ; then
    echo "Found Golang $installed_go_version. Removing"
    rm -rf /usr/local/go
  fi
  echo -n "Downloading and installing Golang $go_version... "
  {
    curl -L https://go.dev/dl/go$go_version.linux-amd64.tar.gz -o /tmp/go.tar.gz
    tar  -C /usr/local -xzf /tmp/go.tar.gz
    rm   /tmp/go.tar.gz
  } &>/dev/null ; echo 'Done'
else
  echo "found"
fi

# GreengageDB environment variables
export PYTHONPATH="$GPHOME/lib/python"
export LD_LIBRARY_PATH="$GPHOME/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"

if [ -e "$GPHOME/etc/openssl.cnf" ]; then
	export OPENSSL_CONF="$GPHOME/etc/openssl.cnf"
fi

# Build
make all install pkg-deb
