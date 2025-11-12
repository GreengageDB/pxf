#!/bin/bash
set -e
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
CONFIG=${1:-"$SCRIPT_DIR/local_it.ini"}

# --- Helper function to read INI ---
function ini_get() {
  local section=$1 key=$2 file=$3
  awk -F '=' -v section="$section" -v key="$key" '
    $0 ~ "\\[" section "\\]" { in_section=1; next }
    /^\[.*\]/ { in_section=0 }
    in_section && $1 ~ key { gsub(/^[ \t]+|[ \t]+$/,"",$2); print $2; exit }
  ' "$file"
}

# --- Read general settings ---
GGDB_IMAGE=$(ini_get general ggdb_image "$CONFIG")
IT_IMAGE=$(ini_get general it_image "$CONFIG")
IT_TAG=$(ini_get general it_tag "$CONFIG")
DEBUG_DIR=$(ini_get general debug_dir "$CONFIG")
DEBUG=$(ini_get general debug "$CONFIG")

# --- Build IT image if missing ---
if ! docker image inspect "$IT_IMAGE:$IT_TAG" &>/dev/null; then
  echo "Building integration test image $IT_IMAGE:$IT_TAG..."
  bash "$SCRIPT_DIR/build-images.sh"
fi

# --- Collect all test sections ---
TEST_SECTIONS=($(grep '^\[tests\.' "$CONFIG" | sed 's/^\[tests\.//;s/\]//'))

echo "Found ${#TEST_SECTIONS[@]} test(s)"

# --- Run tests ---
was_failed=""
for section in "${TEST_SECTIONS[@]}"; do
  GROUP=$(ini_get "tests.$section" profile "$CONFIG")
  USE_FDW=$(ini_get "tests.$section" fdw "$CONFIG")
  USE_SSL=$(ini_get "tests.$section" ssl "$CONFIG")

  echo "------------------------------------------------------"
  echo "Running test '$GROUP' (FDW=$USE_FDW, SSL=$USE_SSL)"
  echo "------------------------------------------------------"

  pushd "$SCRIPT_DIR" > /dev/null
  if ! bash it.sh ; then
    opts=""
    [ "$USE_FDW" = "true" ] && opts=${opts:+$opts,}FDW
    [ "$USE_SSL" = "true" ] && opts=${opts:+$opts,}SSL
    was_failed=${was_failed:+$was_failed, }$GROUP${opts:+"($opts)"}
  fi
  popd > /dev/null
done

if [ -z "$was_failed" ]; then
  echo "----------------------------"
  echo "All ${#TEST_SECTIONS[@]} test(s) passed"
  echo "----------------------------"
  exit 0
else
  echo "----------------------------------------------"
  echo "Failed test(s): $was_failed"
  echo "Check logs and reports in $DEBUG_DIR"
  echo "----------------------------------------------"
  exit 1
fi
