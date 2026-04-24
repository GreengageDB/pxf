#!/bin/bash
# Fot build in docker using developer's Greengage image
# image=greengagedb/ggdb6_ubuntu:6.29.1
# version=dh-6.29.1
# or
# image=ghcr.io/greengagedb/greengage/ggdb6_ubuntu:latest
# version=ghcr-latest

# shellcheck disable=SC2086

set -eux

: "${GPHOME:?GPHOME must be set}"

export JAVA_TOOL_OPTIONS=${JAVA_TOOL_OPTIONS:-'-Dfile.encoding=UTF8'}
export DEBIAN_FRONTEND=${DEBIAN_FRONTEND:-noninteractive}

export GOPATH=/opt/go
export PATH=$PATH:/usr/local/go/bin:$GOPATH/bin

export GREENGAGE_DEB=${GREENGAGE_DEB:-/tmp/greengage.deb}
export GREENGAGE_DEB_URL=${GREENGAGE_DEB_URL:-https://github.com/GreengageDB/greengage/releases/download/main/greengage.deb}

# SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# "$SCRIPT_DIR/set_azure_sources_list.sh"

pkgs='openjdk-17-jdk debhelper devscripts dh-python file' # unzip vim nano ksh locales
echo -n "Installing $pkgs via apt... "
{
  apt-get -yq update
  apt-get -yq install --no-install-recommends $pkgs
  apt-get clean
} &>/dev/null ; echo "Done"

update-locale LANG=en_US.UTF-8

mime_type='application/vnd.debian.binary-package'

if [ ! -r "$GREENGAGE_DEB" ] ; then
  echo -n "GreengageDB deb-package file '$GREENGAGE_DEB' "
  echo -n "not exists or not readable. Try to download from $GREENGAGE_DEB_URL "
  mkdir -p "$(dirname $GREENGAGE_DEB)"
  curl -L "$GREENGAGE_DEB_URL" -o "$GREENGAGE_DEB" &>/dev/null
    if [ ! -r "$GREENGAGE_DEB" ] ; then
      echo "failed. Exiting"
      exit 1
    else
      echo "Done"
    fi
fi
if [ "$(file -Lb --mime-type "$GREENGAGE_DEB")" != "$mime_type" ] ; then
  echo "File $GREENGAGE_DEB not a $mime_type. Use 'GREENGAGE_DEB=<ggdb_deb_file> $0' for proper file location"
  exit 1
fi

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

git config --global --add safe.directory "$(pwd)"
localedef -c -i ru_RU -f CP1251 ru_RU.CP1251

# Install Greengage package
apt-get install -yf "$(realpath "$GREENGAGE_DEB")"

# GreengageDB environment variables
PATH="$GPHOME/bin:$PATH"
PYTHONPATH="$GPHOME/lib/python"
LD_LIBRARY_PATH="$GPHOME/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"

if [ -e "$GPHOME/etc/openssl.cnf" ]; then
	OPENSSL_CONF="$GPHOME/etc/openssl.cnf"
fi

export PATH
export PYTHONPATH
export LD_LIBRARY_PATH
export OPENSSL_CONF

make all install pkg-deb
