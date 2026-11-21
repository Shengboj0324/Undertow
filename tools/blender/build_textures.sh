#!/usr/bin/env bash
# Regenerate every Worldsfall block/item/particle/gui/environment texture deterministically in Blender (headless),
# then verify them and rebuild the review contact sheets.
#
#   tools/blender/build_textures.sh                 # everything
#   tools/blender/build_textures.sh blocks items    # some groups
#   tools/blender/build_textures.sh block/stillstone item/void_anchor   # single textures (prefix match)
#   tools/blender/build_textures.sh expansion       # only the art/SPEC_EXPANSION.md set (xblocks xitems xparticles
#                                                   #   xgui xenvironment are its single groups)
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
BLENDER="${BLENDER:-/Applications/Blender.app/Contents/MacOS/Blender}"
export WF_ROOT="$ROOT"
export WF_TEX_TMP="${WF_TEX_TMP:-${TMPDIR:-/tmp}/wf_tex_tmp}"
mkdir -p "$WF_TEX_TMP"

"$BLENDER" -b --factory-startup --python-exit-code 1 --python "$HERE/textures/main.py" -- "$@" 2>&1 \
  | grep -E '^\[wf\]|Error|Traceback|File "|^\s+\w+Error' || true

python3 "$HERE/verify_textures.py"
if [ "${WF_SHEETS:-1}" = "1" ]; then
  for f in block item particle gui environment; do
    COLS=6 python3 "$HERE/contact_sheet.py" "$f" >/dev/null
  done
  python3 "$HERE/contact_sheet.py" block "$ROOT/art/previews/textures_block_animated.png" --anim >/dev/null
  python3 "$HERE/contact_sheet.py" compare >/dev/null 2>&1 || true
  python3 "$HERE/contact_sheet.py" expansion >/dev/null
  echo "contact sheets -> $ROOT/art/previews/textures_*.png"
fi
# temp renders are small, but clean them up
rm -f "$WF_TEX_TMP"/render_*.png
