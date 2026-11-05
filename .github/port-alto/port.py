# SMH plan 71, step 1.0: the AltoClef port (engine/src/main/java/com/mineagent/engine/alto) made from its source
# (third_party/altoclef, MiranCZ/altoclef 1f8e038 in Mojang's names) by a script, so every difference from AltoClef is
# a recorded patch with a reason (LEDGER.md), never a hand edit nobody can see.
#
#   python3 -I .github/port-alto/port.py            regenerate the ported files from the source and the patches
#   python3 -I .github/port-alto/port.py --check    regenerate into a scratch folder and fail on any difference (CI)
#   python3 -I .github/port-alto/port.py --record   write each ported file's difference from its source as its patch
#
# A ported file is AltoClef's file with its package renamed (adris.altoclef -> com.mineagent.engine.alto, and the old
# port's Baritone imports -> com.mineagent.engine.baritone), a two-line source header, then its patch, if it has one
# (patches/<path>.patch: a unified diff over the file's lines, applied only where every context line matches). The
# files written here, not ported, are listed in written.txt and left alone. Run from the repository root.
import difflib, os, re, shutil, sys, tempfile

SRC = 'third_party/altoclef/src/main/java/adris/altoclef'
DST = 'engine/src/main/java/com/mineagent/engine/alto'
HERE = os.path.dirname(os.path.abspath(__file__))
PATCHES = os.path.join(HERE, 'patches')
COMMIT = '1f8e038'


def lines_of(path):
    return open(path, encoding='utf-8', newline='').read().split('\n')


def manifest(name):
    return [l.strip() for l in open(os.path.join(HERE, name)) if l.strip() and not l.startswith('#')]


def transformed(rel):
    text = open(os.path.join(SRC, rel), encoding='utf-8', newline='').read()
    text = text.replace('adris.altoclef', 'com.mineagent.engine.alto').replace('import baritone.', 'import com.mineagent.engine.baritone.')
    header = ('// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/'
              + rel + ' at commit\n// ' + COMMIT + ", in Mojang's names (third_party/altoclef). Changes for the companion's"
              + " server-side body are marked SMH.\n")
    return (header + text).split('\n')


def diff(a, b):
    out = []
    for group in difflib.SequenceMatcher(None, a, b, autojunk=False).get_grouped_opcodes(3):
        i1, i2, j1, j2 = group[0][1], group[-1][2], group[0][3], group[-1][4]
        out.append('@@ -%d,%d +%d,%d @@' % (i1 + 1, i2 - i1, j1 + 1, j2 - j1))
        for op, a1, a2, b1, b2 in group:
            if op == 'equal':
                out += [' ' + l for l in a[a1:a2]]
            else:
                out += ['-' + l for l in a[a1:a2]] + ['+' + l for l in b[b1:b2]]
    return out


def apply(rel, a, patch):
    out, pos, i = [], 0, 0
    while i < len(patch):
        m = re.match(r'^@@ -(\d+),(\d+) \+(\d+),(\d+) @@$', patch[i])
        if not m:
            raise SystemExit('%s: bad hunk header at patch line %d' % (rel, i + 1))
        start = int(m.group(1)) - 1
        i += 1
        old, new = [], []
        while i < len(patch) and not patch[i].startswith('@@ '):
            tag, line = patch[i][:1], patch[i][1:]
            if tag in (' ', '-'): old.append(line)
            if tag in (' ', '+'): new.append(line)
            i += 1
        if a[start:start + len(old)] != old:
            raise SystemExit('%s: a hunk at source line %d no longer matches AltoClef (re-read the source, then the patch)' % (rel, start + 1))
        out += a[pos:start] + new
        pos = start + len(old)
    return out + a[pos:]


def build(rel):
    a = transformed(rel)
    pf = os.path.join(PATCHES, rel + '.patch')
    if os.path.exists(pf):
        a = apply(rel, a, open(pf, encoding='utf-8', newline='').read().split('\n')[:-1])
    return '\n'.join(a)


def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else ''
    ported, written = manifest('files.txt'), manifest('written.txt')
    if mode == '--record':
        n = 0
        for rel in ported:
            d = diff(transformed(rel), lines_of(os.path.join(DST, rel)))
            pf = os.path.join(PATCHES, rel + '.patch')
            if d:
                os.makedirs(os.path.dirname(pf), exist_ok=True)
                open(pf, 'w', encoding='utf-8', newline='').write('\n'.join(d) + '\n')
                n += 1
            elif os.path.exists(pf):
                os.remove(pf)
        print('recorded %d patches for %d ported files' % (n, len(ported)))
        return
    out = tempfile.mkdtemp() if mode == '--check' else DST
    bad = []
    for rel in ported:
        text = build(rel)
        if mode == '--check':
            if open(os.path.join(DST, rel), encoding='utf-8', newline='').read() != text: bad.append(rel)
        else:
            os.makedirs(os.path.dirname(os.path.join(out, rel)), exist_ok=True)
            open(os.path.join(out, rel), 'w', encoding='utf-8', newline='').write(text)
    on_disk = sorted(os.path.relpath(os.path.join(d, f), DST) for d, _, fs in os.walk(DST) for f in fs if f.endswith('.java'))
    unlisted = [f for f in on_disk if f not in ported and f not in written]
    if mode == '--check':
        shutil.rmtree(out)
        for rel in bad: print('differs from what the script makes:', rel)
        for rel in unlisted: print('neither ported nor listed as written:', rel)
        print('%d ported files checked, %d differ, %d unlisted' % (len(ported), len(bad), len(unlisted)))
        sys.exit(1 if bad or unlisted else 0)
    print('ported %d files into %s' % (len(ported), DST))


main()
