#!/bin/bash
# shellcheck disable=SC2046,2086
locations=${1:-'/opt /usr/lib'}
f=''
for location in $locations; do
  f=$(find "$location" -type f -path "*/bin/pg_config" 2>/dev/null | head -n1)
  if [ -n "$f" ]; then
    dirname $(dirname $f)
    break
  fi
done
[ -z "$f" ] && exit 1 || exit 0
