#!/bin/bash
# shellcheck disable=SC1087,2155,2004,2207
set -e

# --- Presets ---
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd) ; export SCRIPT_DIR=${SCRIPT_DIR:-.}
CONFIG=${1:-"$SCRIPT_DIR/local_it.ini"} ; export CONFIG

# --- Check config file ---
[ -r "$CONFIG" ] || { echo "Config file '$CONFIG' not readable. Process terminated"; exit 1; }

# --- Helper function to apply section variables ---
function apply_section_vars() {
    local section=$1
    awk -F '=' -v section="$section" '
        {
            sub(/[ \t]*[;#].*$/, "", $0)
            gsub(/^[ \t]+|[ \t]+$/, "", $0)
        }
        NF == 0 { next }
        $0 ~ "^[[:space:]]*\\[" section "\\][[:space:]]*$" { in_section=1; next }
        /^[[:space:]]*\\[.*\\][[:space:]]*$/ { in_section=0 }
        in_section && $1 ~ /^[a-zA-Z_][a-zA-Z0-9_]*$/ {
            gsub(/^[ \t]+|[ \t]+$/, "", $1)
            gsub(/^[ \t]+|[ \t]+$/, "", $2)
            print "export " $1 "=\"" $2 "\""
        }
    ' "$CONFIG"
}

# --- Load general section first ---
eval "$(apply_section_vars "general")"

# --- Load default section for reset ---
DEFAULT_VARS=$(apply_section_vars "default")

# --- Collect all test sections dynamically ---
TEST_SECTIONS=($(grep '^\[.*\]' "$CONFIG" | sed 's/^\[//;s/\]//' | grep -v -E "^(general|default)$"))
tests_num=${#TEST_SECTIONS[@]}

echo "----------------"
echo "Tests found: $tests_num"
echo "----------------"

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

unset was_failed
for section in "${TEST_SECTIONS[@]}"; do
  # Reset to default values for each test section
  eval "$DEFAULT_VARS"

  # Apply section-specific variables
  eval "$(apply_section_vars "$section")"

  # GROUP must be defined in each test section
  if [ -z "$GROUP" ]; then
    echo "ERROR: Section '$section' must define GROUP variable"
    exit 1
  fi

  export PROFILE="${PROFILE:-all}"

  echo "----------------------------------------------------------------------------"
  echo "Run test: $section"
  echo "Group: $GROUP, Profile: $PROFILE${USE_FDW:+, with FDW}${USE_SSL:+, with SSL}"
  echo "----------------------------------------------------------------------------"

  pushd "$SCRIPT_DIR" > /dev/null
  if ! bash "$SCRIPT_DIR"/it.sh ; then
    unset opts
    [ -n "$USE_FDW" ] && opts=${opts:+$opts,}FDW
    [ -n "$USE_SSL" ] && opts=${opts:+$opts,}SSL
    was_failed=${was_failed:+$was_failed, }$GROUP${opts:+" (with $opts)"}
  fi
  popd > /dev/null
done

if [ -z "$was_failed" ]; then
  echo "----------------------------"
  echo "Grand TOTAL $tests_num test(s) started at $STARTED_AT passed"
  echo "----------------------------"
  exit 0
else
  echo "----------------------------------------------"
  echo "This test(s) started at $STARTED_AT was failed: $was_failed. Check logs and reports"
  echo "----------------------------------------------"
  exit 1
fi
