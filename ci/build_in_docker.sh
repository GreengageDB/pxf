#!/bin/bash
# Fot build in docker using developer's Greengage image
# image=greengagedb/ggdb6_ubuntu:6.29.1
# version=dh-6.29.1
# or
# image=ghcr.io/greengagedb/greengage/ggdb6_ubuntu:latest
# version=ghcr-latest

set -eux

export JAVA_TOOL_OPTIONS=${JAVA_TOOL_OPTIONS:-'-Dfile.encoding=UTF8'}
export DEBIAN_FRONTEND=${DEBIAN_FRONTEND:-noninteractive}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
"$SCRIPT_DIR/set_azure_sources_list.sh"

apt-get -y update
apt-get -y install --no-install-recommends openjdk-17-jdk # unzip vim nano ksh locales
apt-get clean

update-locale LANG=en_US.UTF-8

go_version=$(grep -E '^go [0-9]+\.[0-9]+\.[0-9]+' cli/go.mod | cut -d' ' -f2)
curl -L https://go.dev/dl/go$go_version.linux-amd64.tar.gz -o /tmp/go.tar.gz
tar -C /usr/local -xzf /tmp/go.tar.gz
rm /tmp/go.tar.gz

git config --global --add safe.directory "$(pwd)"
localedef -c -i ru_RU -f CP1251 ru_RU.CP1251
mkdir -p "$GPHOME"
tar -xzf "$DEV_HOME/bin_gpdb/bin_gpdb.tar.gz" -C "$GPHOME/"

# shellcheck source=/dev/null
source "$GPHOME/greengage_path.sh"
make all install

rm "$HOME/.cache" -rf
rm "$HOME/.gitconfig" -rf
rm "$HOME/.gradle" -rf

rm -rf /var/lib/apt/lists/*
rm -rf /tmp/*
