#!/usr/bin/env bash
# Several benchmark worlds, a few at a time (SMH plan 71, step 0.5): each job "<seed>:<switches|->:<label>", run by
# bench-local.sh in slots 1..<parallel>; each world's result line printed as it ends.
#
#   scripts/bench-batch.sh <parallel> <seed:switches:label> ...
#
# Two at a time suits a 4-core, 15 GB machine (each world is a sprinting server and a Gradle daemon).
set -u
here=$(cd "$(dirname "$0")" && pwd)
parallel=${1:?parallel}; shift
declare -A pid_of
for job in "$@"; do
  IFS=: read -r seed switches label <<<"$job"
  slot=""
  while [ -z "$slot" ]; do
    for ((s = 1; s <= parallel; s++)); do
      p=${pid_of[$s]:-}
      if [ -z "$p" ] || ! kill -0 "$p" 2>/dev/null; then slot=$s; break; fi
    done
    [ -z "$slot" ] && wait -n
  done
  "$here/bench-local.sh" "$seed" "$switches" "$label" "$slot" &
  pid_of[$slot]=$!
  sleep 20 # the next world's Gradle start after this one's has taken its locks
done
wait
