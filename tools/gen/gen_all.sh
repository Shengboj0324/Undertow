#!/bin/sh
# Regenerates every generated resource. Run from anywhere.
set -e
cd "$(dirname "$0")"
python3 gen_sounds.py
python3 gen_assets.py
python3 gen_data.py
python3 gen_worldgen.py
python3 gen_lang.py
