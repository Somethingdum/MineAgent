#!/usr/bin/env bash
# The architecture checks (SMH plan 71, §6.8 invariant I6, §10): one implementation per behaviour, each grep enforced
# from the phase it belongs to: .github/phase holds the last phase whose gate has passed, so a check "from phase 1"
# is enforced once phase 1's gate has passed (the gate is where it must first hold). The switch count is
# in .github/switch-count: a new switch is a deliberate change to that file, with its line in plan 71's record.
# Run from the repository root; exits non-zero on the first broken rule, after printing every one.
set -u
phase=$(tr -dc '0-9' < .github/phase)
src=engine/src/main/java/com/mineagent
bad=0
check() { # <from phase> <what> <count> <allowed>
  local from=$1 what=$2 count=$3 allowed=$4
  if [ "$phase" -ge "$from" ]; then
    if [ "$count" -gt "$allowed" ]; then echo "BROKEN (from phase $from): $what: $count, allowed $allowed"; bad=1;
    else echo "ok (from phase $from): $what: $count"; fi
  else echo "not yet (phase $from): $what: $count"; fi
}
count() { grep -rlE "$1" $2 2>/dev/null | wc -l; }
check 1 "files naming com.mineagent.engine.baritone" "$(count 'com\.mineagent\.engine\.baritone' "$src fabric/src neoforge/src")" 0
check 1 "a static AltoClef current" "$(grep -rE 'static AltoClef current[[:space:]]*[;=]' $src 2>/dev/null | wc -l)" 0
check 2 "files naming com.mineagent.sbe.altoclef" "$(count 'com\.mineagent\.sbe\.altoclef' "$src fabric/src neoforge/src")" 0
# sbe/mixins/MixinUtil.java copies Minecraft's own executor-shutdown routine, whose decompiled local name (var3) came
# with it; it is SecondBrainEngine's source, not decompiled (.github/port-sbe/AUTOMATONE-LEDGER.md).
check 2 "decompiled files (catch (X varN)) in the engine" "$(grep -rlE 'catch \([A-Za-z]* var[0-9]+\)' $src | grep -v 'sbe/mixins/MixinUtil.java' | wc -l)" 0
check 2 "getBlockState( in engine/alto outside blockAt" "$(grep -rn 'getBlockState(' $src/engine/alto 2>/dev/null | grep -v blockAt | wc -l)" 0
check 5 "lines in AgentLoop.java (upstream's 3,979)" "$(wc -l < $src/engine/loop/AgentLoop.java)" 3999
switches=$(grep -cE '^\s*public static boolean [a-zA-Z0-9]+\(\)' $src/engine/settings/Switches.java)
expected=$(tr -dc '0-9' < .github/switch-count)
if [ "$switches" -ne "$expected" ]; then echo "BROKEN: $switches switches, .github/switch-count says $expected"; bad=1;
else echo "ok: $switches switches"; fi
exit $bad
