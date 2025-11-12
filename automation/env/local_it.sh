#!/bin/bash
# shellcheck disable=SC1087,2155,2004,2207
set -e

# --- Presets ---
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd) ; export SCRIPT_DIR=${SCRIPT_DIR:-.}
CONFIG=${1:-"$SCRIPT_DIR/local_it.ini"} ; export CONFIG

# --- Check config file ---
[ -r "$CONFIG" ] || { echo "Config file '$CONFIG' not readable. Process terminated"; exit 1; }

# --- Helper function to read INI ---
function ini_get() {
  local section=$1 key=$2 file=$3
  awk -F '=' -v section="$section" -v key="$key" '
    $0 ~ "\\[" section "\\]" { in_section=1; next }
    /^\[.*\]/ { in_section=0 }
    in_section && $1 ~ key { gsub(/^[ \t]+|[ \t]+$/,"",$2); print $2; exit }
  ' "$file"
}

# --- Configure ---
export GGDB_IMAGE=$(ini_get general ggdb_image "$CONFIG")
export IT_IMAGE=$(ini_get general it_image "$CONFIG")
export IT_TAG=$(ini_get general it_tag "$CONFIG")
export DEBUG_DIR=$(ini_get general debug_dir "$CONFIG")
export DEBUG=$(ini_get general debug "$CONFIG")

# --- Begin ---
if [ "$BUILD_IMAGES" == "true" ]; then
  echo "------------"
  echo "Force (re)build image $IT_IMAGE:$IT_TAG"
  echo "------------"
  bash "$SCRIPT_DIR"/build-images.sh
fi

if ! docker image inspect "$IT_IMAGE:$IT_TAG" &>/dev/null ; then
  echo "------------"
  echo "Integration tests image $IT_IMAGE:$IT_TAG not found locally. Building"
  echo "------------"
  bash "$SCRIPT_DIR"/build-images.sh
fi

# --- Collect all test sections ---
TEST_SECTIONS=($(grep '^\[tests\.' "$CONFIG" | sed 's/^\[tests\.//;s/\]//'))

echo "----------------"
echo "Tests found: ${#TEST_SECTIONS[@]}"
echo "----------------"

unset was_failed
for section in "${TEST_SECTIONS[@]}"; do
  export GROUP="$section"
  export USE_FDW=$(ini_get "tests.$section" fdw "$CONFIG")
  export USE_SSL=$(ini_get "tests.$section" ssl "$CONFIG")
  export PROFILE=$(ini_get "tests.$section" profile "$CONFIG")

  echo "---------------------------------------------------------------------------------"
  echo "Run test '$GROUP' (FDW=${USE_FDW:-false}, SSL=${USE_SSL:-false}, PROFILE=${PROFILE:-$GROUP})"
  echo "---------------------------------------------------------------------------------"

  pushd "$SCRIPT_DIR" > /dev/null
  if ! bash it.sh ; then  # Collect failed tests
    opts=""
    [ "$USE_FDW" = "true" ] && opts=${opts:+$opts,}FDW
    [ "$USE_SSL" = "true" ] && opts=${opts:+$opts,}SSL
    was_failed=${was_failed:+$was_failed, }$GROUP${opts:+"($opts)"}
  fi
  popd > /dev/null
done

if [ -z "$was_failed" ]; then
  echo "----------------------------"
  echo "Grand TOTAL ${#TEST_SECTIONS[@]} test(s) passed"
  echo "----------------------------"
  exit 0
else
  echo "----------------------------------------------"
  echo "This test(s) failed: $was_failed. Check logs and reports"
  echo "----------------------------------------------"
  exit 1
fi
