#!/bin/bash
# Compiles third_party/moto/rewritten/*.c with the options that reproduce
# the flash.  Usage: tools/matchdecomp/build.sh OUTDIR
set -euo pipefail

SRC="$(cd "$(dirname "$0")/../../third_party/moto/rewritten" && pwd)"
readonly SRC
readonly CFLAGS=(-p cpu32 -OT -c)

main() {
  if [[ $# -ne 1 ]]; then
    echo "usage: $0 OUTDIR" >&2
    exit 2
  fi
  mkdir -p "$1"
  cd "$1"
  local f
  for f in "${SRC}"/*.c; do
    cc68k "${CFLAGS[@]}" -I "${SRC}" "${f}"
  done
}

main "$@"
