# Resolves Stonecutter's version comments (https://stonecutter.kikugie.dev) for one game version, reading the sources
# as text only: each chain "//? cond {" ... "//?} elif cond {" ... "//?} else {" ... "//?}" (and the inline
# "/*? cond {*/" form) is replaced by the body of its first branch whose condition holds for the target, uncommented
# ("/*" and "*/" round an inactive body removed); the other branches and the markers are dropped.
# Usage: python3 -I stonecut.py <target version> <source dir> <output dir>
import os, re, sys

TARGET = tuple(int(x) for x in sys.argv[1].split('.'))
SRC, OUT = sys.argv[2], sys.argv[3]

MARK = re.compile(r'//\?[^\n]*|/\*\?.*?\*/', re.S)

def ver(s):
    t = tuple(int(x) for x in s.strip().split('.'))
    return t + (0,) * (3 - len(t))

def holds(cond):
    cond = cond.strip()
    for c in cond.split('&&'):
        c = c.strip()
        m = re.fullmatch(r'(>=|<=|>|<|=|==)?\s*([0-9.]+)', c)
        if not m:
            raise ValueError('condition I cannot read: %r' % cond)
        op, v = m.group(1) or '=', ver(m.group(2))
        t = TARGET + (0,) * (3 - len(TARGET))
        ok = {'>=': t >= v, '<=': t <= v, '>': t > v, '<': t < v, '=': t == v, '==': t == v}[op]
        if not ok:
            return False
    return True

def parse(marker):
    """('open', cond) | ('elif', cond) | ('else', None) | ('close', None)"""
    body = marker[3:] if marker.startswith('//?') else marker[3:-2]
    body = body.strip()
    if body.startswith('}'):
        rest = body[1:].strip()
        if not rest:
            return ('close', None)
        if rest.startswith('else'):
            rest = rest[4:].strip()
            if rest.startswith('{'):
                return ('else', None)
            raise ValueError('odd else: %r' % marker)
        if rest.startswith('elif'):
            rest = rest[4:].strip()
            assert rest.endswith('{'), marker
            return ('elif', rest[:-1])
        raise ValueError('odd marker: %r' % marker)
    if body.startswith('if '):
        body = body[3:]
    assert body.endswith('{'), marker
    return ('open', body[:-1])

def uncomment(seg):
    """An inactive body is "/*" ... "*/" (round its text, whitespace aside): its text, uncommented; else as is."""
    stripped = seg.strip()
    if stripped.startswith('/*') and stripped.endswith('*/'):
        lead = seg[:len(seg) - len(seg.lstrip())]
        trail = seg[len(seg.rstrip()):]
        inner = stripped[2:-2]
        # Inside a commented-out branch Stonecutter writes the comments it holds as "/^" and "^/": back to "/*" and "*/",
        # and the version chains among them resolved in turn.
        inner = inner.replace('/^', '/*').replace('^/', '*/')
        return lead + resolve(inner) + trail
    return seg

def resolve(text):
    out, pos, stack = [], 0, []
    # stack entries: dict(chosen, active_now, buf_start)
    for m in MARK.finditer(text):
        kind, cond = parse(m.group(0))
        seg = text[pos:m.start()]
        pos = m.end()
        if not stack:
            if kind != 'open':
                raise ValueError('unopened %s at %d' % (kind, m.start()))
            out.append(seg)
            stack.append({'done': False, 'take': holds(cond), 'body': []})
            continue
        top = stack[-1]
        if kind == 'open':
            raise ValueError('nested chain at %d (not handled)' % m.start())
        # the segment just ended is the body of the branch now closing
        if top['take'] and not top['done']:
            out.append(uncomment(seg))
            top['done'] = True
        if kind == 'close':
            stack.pop()
            continue
        top['take'] = (kind == 'else') or holds(cond)
    if stack:
        raise ValueError('unclosed chain')
    out.append(text[pos:])
    return ''.join(out)

changed = 0
for root, _, files in os.walk(SRC):
    for f in files:
        src = os.path.join(root, f)
        dst = os.path.join(OUT, os.path.relpath(src, SRC))
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        data = open(src, encoding='utf-8').read() if f.endswith(('.java', '.json')) else None
        if data is None:
            with open(src, 'rb') as a, open(dst, 'wb') as b:
                b.write(a.read())
            continue
        try:
            res = resolve(data) if ('//?' in data or '/*?' in data) else data
        except Exception as e:
            print('FAILED', src, e)
            res = data
        if res != data:
            changed += 1
        open(dst, 'w', encoding='utf-8').write(res)
print('resolved', changed, 'files for', sys.argv[1])
