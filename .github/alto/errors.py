# The compile errors of a Gradle log, one line each, grouped by file: the end of the job log is what is read back.
import re, sys, collections
log = open(sys.argv[1], errors='replace').read().split('\n')
errs = []
i = 0
while i < len(log):
    m = re.match(r'^(/\S+\.java):(\d+): error: (.*)$', log[i])
    if m:
        path, line, msg = m.group(1), int(m.group(2)), m.group(3)
        extra = []
        j = i + 1
        while j < len(log) and not re.match(r'^(/\S+\.java):\d+: (error|warning)', log[j]) and j < i + 8:
            t = log[j].strip()
            if t.startswith(('symbol:', 'location:', 'required:', 'found:', 'reason:')):
                extra.append(t)
            j += 1
        rel = re.sub(r'^.*/src/main/java/com/mineagent/engine/', '', path)
        rel = re.sub(r'^.*/src/main/java/', '', rel)
        errs.append((rel, line, msg, ' | '.join(extra)))
        i = j
        continue
    i += 1
print('=' * 30, 'ALTO COMPILE ERRORS:', len(errs))
by = collections.Counter(e[0] for e in errs)
for f, n in by.most_common():
    print('%5d %s' % (n, f))
print('=' * 30, 'ERRORS')
for rel, line, msg, extra in sorted(errs):
    print('%s:%d: %s%s' % (rel, line, msg[:200], (' [' + extra[:240] + ']') if extra else ''))
tail = [l for l in log if 'BUILD SUCCESSFUL' in l or 'BUILD FAILED' in l or 'What went wrong' in l]
print('=' * 30, *tail)
