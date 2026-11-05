#!/usr/bin/env bash
# One three-day benchmark world, run locally (SMH plan 71, step 0.5): a fresh world for the seed, the switches given,
# a companion on its own; its log, report and any stall snapshot kept under the bench folder.
#
#   scripts/bench-local.sh <seed> <switches|-> <label> [slot]
#
# It runs the commit checked out now (HEAD), never uncommitted work, from a git worktree of its own per slot
# ($BENCH_ROOT/slot-<n>, default ../mineagent-bench), so two slots can run at once without sharing a Gradle project.
# BENCH_TICKS (default 72000: three days) and BENCH_BOX_IN=true (a forced stall, to check the stall snapshot) pass on.
# A download Maven Central refuses for the rate (HTTP 429) is waited out and retried; it is not a finding.
set -u
repo=$(cd "$(dirname "$0")/.." && pwd)
seed=${1:?seed}; switches=${2:--}; label=${3:?label}; slot=${4:-1}
[ "$switches" = "-" ] && switches=""
ticks=${BENCH_TICKS:-72000}
root=${BENCH_ROOT:-$(cd "$repo/.." && pwd)/mineagent-bench}
logs="$root/logs"; mkdir -p "$logs"
commit=$(git -C "$repo" rev-parse HEAD)
wt="$root/slot-$slot"
if [ -e "$wt/.git" ]; then
  git -C "$wt" checkout -q --force --detach "$commit"
else
  git -C "$repo" worktree add -q --detach "$wt" "$commit"
fi
run="$wt/fabric/build/benchmark"
mkdir -p "$run"
( cd "$run" && rm -rf world config logs stalls report.json usercache.json banned-ips.json banned-players.json ops.json whitelist.json )
echo "eula=true" > "$run/eula.txt"
cat > "$run/server.properties" <<PROPS
level-seed=$seed
gamemode=survival
difficulty=normal
spawn-protection=0
online-mode=false
view-distance=10
simulation-distance=10
max-tick-time=-1
spawn-monsters=true
spawn-animals=true
generate-structures=true
PROPS
log="$logs/$label.log"
echo "[bench-local] $label: seed $seed, switches '${switches:-none}', $ticks ticks, commit ${commit:0:7}, slot $slot" > "$log"
status=1
for attempt in 1 2 3 4; do
  ( cd "$wt" && LITTLE_CODER_NO_UPDATE_CHECK=1 ./gradlew --console=plain :fabric:runBenchmark \
      "-PbenchmarkTicks=$ticks" "-PbenchmarkSwitches=$switches" "-PbenchmarkBoxIn=${BENCH_BOX_IN:-false}" ) >> "$log" 2>&1
  status=$?
  if [ $status -ne 0 ] && grep -q "status code 429" "$log"; then sleep $((attempt * 30)); continue; fi
  break
done
echo "[bench-local] exit $status" >> "$log"
[ -f "$run/report.json" ] && cp "$run/report.json" "$logs/$label.report.json"
[ -d "$run/stalls" ] && rm -rf "$logs/$label-stalls" && cp -r "$run/stalls" "$logs/$label-stalls"
line=$(grep -E "\[BENCH\] done after|\[BENCH\] hung: no server" "$log" | tail -1 | sed 's/.*\[STDOUT\]: //')
echo "$label: ${line:-no result (exit $status); see $log}"
