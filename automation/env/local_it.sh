#!/bin/bash
# shellcheck disable=SC1087,2155,2004,2207
set -e

# --- Presets ---
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd) ; export SCRIPT_DIR=${SCRIPT_DIR:-.}
CONFIG=${1:-"$SCRIPT_DIR/local_it.ini"} ; export CONFIG

# --- Check config file ---
[ -r "$CONFIG" ] || { echo "Config file '$CONFIG' not readable. Process terminated"; exit 1; }

# --- Helper function to read config value ---
# Returns "" if value is empty or literally "false", else returns the value
function get_config_value() {
  local section=$1 key=$2 file=$CONFIG
  local val
  val=$(awk -F '=' -v section="$section" -v key="$key" '
    $0 ~ "\\[" section "\\]" { in_section=1; next }
    /^\[.*\]/ { in_section=0 }
    in_section && $1 ~ key { gsub(/^[ \t]+|[ \t]+$/,"",$2); print $2; exit }
  ' "$file")
  if [[ -z "$val" || "$val" == "false" ]]; then
    echo ""
  else
    echo "$val"
  fi
}

# --- Configure ---
export GGDB_IMAGE=$(get_config_value general GGDB_IMAGE)
export IT_IMAGE=$(get_config_value general IT_IMAGE)
export IT_TAG=$(get_config_value general IT_TAG)
export DEBUG_DIR=$(get_config_value general DEBUG_DIR)
export DEBUG=$(get_config_value general DEBUG)

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

# --- Collect all test sections dynamically ---
TEST_SECTIONS=($(grep '^\[test\.' "$CONFIG" | sed 's/^\[test\.//;s/\]//'))
tests_num=${#TEST_SECTIONS[@]}

echo "----------------"
echo "Tests found: $tests_num"
echo "----------------"

unset was_failed
for section in "${TEST_SECTIONS[@]}"; do
  export GROUP="$section"
  PROFILE=$(get_config_value "test.$section" profile) ; export PROFILE=${PROFILE:-all}

  # USE_FDW and USE_SSL are enabled if key exists and value != false
  export USE_FDW=$(get_config_value "test.$section" fdw)
  export USE_SSL=$(get_config_value "test.$section" ssl)

  echo "---------------------------------------------------------------------------------"
  echo "Run test '$GROUP' (FDW=${USE_FDW:-false}, SSL=${USE_SSL:-false}, PROFILE=${PROFILE:-$GROUP})"
  echo "---------------------------------------------------------------------------------"

  pushd "$SCRIPT_DIR" > /dev/null
  if ! bash "$SCRIPT_DIR"/it.sh ; then
    opts=""
    [ -n "$USE_FDW" ] && opts=${opts:+$opts,}FDW
    [ -n "$USE_SSL" ] && opts=${opts:+$opts,}SSL
    was_failed=${was_failed:+$was_failed, }$GROUP${opts:+"($opts)"}
  fi
  popd > /dev/null
done

if [ -z "$was_failed" ]; then
  echo "----------------------------"
  echo "Grand TOTAL $tests_num test(s) passed"
  echo "----------------------------"
  exit 0
else
  echo "----------------------------------------------"
  echo "This test(s) failed: $was_failed. Check logs and reports"
  echo "----------------------------------------------"
  exit 1
fi
