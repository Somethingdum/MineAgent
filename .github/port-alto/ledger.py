# Writes .github/port-alto/LEDGER.md: one line per patch, why the companion's copy differs from AltoClef's (SMH plan 71,
# §7.1 "Patched"). The reason is the port's own SMH notes in the patch, or, for the mechanical kinds, the kind; a patch
# that is neither stops it (exit 1), so no difference goes unexplained. Run after port.py --record.
import os, re, sys
HERE = os.path.dirname(os.path.abspath(__file__))
P = os.path.join(HERE, 'patches')
PRE = re.compile(r'^\s*//\s*(#if|#else|#elseif|#endif|\$\$)')
CLIENT = re.compile(r'Minecraft\.getInstance\(\)|LocalPlayer|net\.minecraft\.client\.')
rows, unexplained = [], []
for d, _, fs in os.walk(P):
    for f in sorted(fs):
        rel = os.path.relpath(os.path.join(d, f), P)[:-len('.patch')]
        lines = open(os.path.join(d, f), encoding='utf-8').read().split('\n')
        add = [l[1:] for l in lines if l.startswith('+')]
        rem = [l[1:] for l in lines if l.startswith('-')]
        notes = []
        for l in add:
            m = re.search(r'\bSMH\b[:,]?\s*(.*)', l)
            if m and m.group(1).strip():
                notes.append(m.group(1).strip().rstrip('*/ ').strip())
        kinds = set()
        for l in rem:
            if PRE.match(l): kinds.add('multi-version comments resolved to 1.21.1')
            elif re.match(r'^\s*import [\w.]+\.\*;\s*$', l) or 'net.fabricmc.fabric.api.transfer' in l: kinds.add('wildcard or client-library imports the Mojang remap left behind, removed')
            elif CLIENT.search(l): kinds.add("the client's player, world and game mode replaced by the companion's server player, world and game mode (AltoClef.getInstance())")
            elif 'Baritone.settings()' in l: kinds.add("Baritone's static settings read through the companion's Baritone API")
            elif re.search(r'Baritone\.settings\(\)|getSettings\(\)|Optional<Double>|new BlockOptionalMeta\(|getPrimaryBaritone\(\)|createBaritone|baritone\.tick\(\)|isInControl\(\)|\.logger\b|blocksToDisallowBreaking|MovementHelper\.avoidBreaking\(|BaritoneProvider\.as\(|destroyBaritone|setPlayer\(|import com\.mineagent\.engine\.(baritone|rules)', l):
                kinds.add("Automatone's API where it differs from Baritone's (seam A, SMH plan 71 §6.1)")
            elif l.strip() == '': pass
            else: kinds.add('other')
        known = sorted(k for k in kinds if k != 'other')
        if notes:
            why = 'SMH: ' + ' / '.join(n[:160] for n in notes[:3]) + (' / ...' if len(notes) > 3 else '')
            if known: why += '; and ' + '; '.join(known)
        elif kinds and 'other' not in kinds:
            why = '; '.join(known)
        else:
            unexplained.append(rel)
            why = 'UNEXPLAINED'
        rows.append('| `%s` | +%d / -%d | %s |' % (rel, len(add), len(rem), why.replace('|', '/')))
out = ['# AltoClef port: the ledger', '',
       "Every difference between `engine/src/main/java/com/mineagent/engine/alto` and AltoClef's source",
       '(`third_party/altoclef`, MiranCZ/altoclef `1f8e038`), one line per patched file, with why. Made by `ledger.py`',
       'from the patches `port.py --record` writes; the reasons are the port\'s own `SMH` notes, or, where a patch is only',
       'mechanical, its kind. SMH plan 71, §7.1 "Patched" and step 1.0. Files written for the companion, not ported, are',
       'in `written.txt`.', '', '| File | Lines | Why |', '|---|---|---|'] + rows
open(os.path.join(HERE, 'LEDGER.md'), 'w', encoding='utf-8').write('\n'.join(out) + '\n')
print('%d patches in the ledger, %d unexplained' % (len(rows), len(unexplained)))
for r in unexplained: print('  unexplained:', r)
sys.exit(1 if unexplained else 0)
