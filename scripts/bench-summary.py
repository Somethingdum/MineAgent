#!/usr/bin/env python3
"""The benchmark worlds' results as one table (SMH plan 71, §7.12): per world, whether the minimum held at the end,
the day it was first met, stalls, deaths and tick cost; then per agent and seed, the held count and the median
first-met day the gate compares. Reads <label>.report.json from the bench logs folder.

    python3 -I scripts/bench-summary.py [logs folder] [label prefix ...]
"""
import json, os, statistics, sys

logs = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'mineagent-bench', 'logs')
prefixes = sys.argv[2:]
rows = []
for name in sorted(os.listdir(logs)):
    if not name.endswith('.report.json'):
        continue
    label = name[:-len('.report.json')]
    if prefixes and not any(label.startswith(p) for p in prefixes):
        continue
    r = json.load(open(os.path.join(logs, name)))
    end = r.get('end', {})
    t = r.get('tickCost', {})
    rows.append({
        'label': label, 'switches': r.get('switchesOn') or 'none', 'seed': r.get('seed'),
        'held': bool(r.get('heldAtEnd', r.get('passed'))), 'firstMet': r.get('minimumFirstMetDay', -1),
        'days': r.get('days'), 'stalls': r.get('stalls', 0), 'endedStalled': r.get('endedEarlyStalled', False),
        'deaths': end.get('deaths'), 'cMean': t.get('companionMeanMs'), 'cP99': t.get('companionP99Ms'),
        'sMean': t.get('serverMeanMs'), 'sP99': t.get('serverP99Ms'),
    })
print('| world | switches | held | first met | days | stalls | deaths | companion ms mean / p99 | server ms mean / p99 |')
print('|---|---|---|---|---|---|---|---|---|')
for x in rows:
    fm = 'never' if x['firstMet'] is None or x['firstMet'] < 0 else '%.2f' % x['firstMet']
    st = str(x['stalls']) + (' (ended early)' if x['endedStalled'] else '')
    cost = lambda a, b: '-' if a is None else '%.2f / %.2f' % (a, b)
    print('| %s | %s | %s | %s | %s | %s | %s | %s | %s |' % (x['label'], x['switches'], 'yes' if x['held'] else 'no', fm,
          '%.2f' % x['days'] if x['days'] else '-', st, x['deaths'], cost(x['cMean'], x['cP99']), cost(x['sMean'], x['sP99'])))
groups = {}
for x in rows:
    groups.setdefault((x['switches'], x['seed']), []).append(x)
print()
print('| switches | seed | held | median first met (never counts as 4) |')
print('|---|---|---|---|')
for (sw, seed), xs in sorted(groups.items(), key=lambda kv: (kv[0][0], str(kv[0][1]))):
    days = [x['firstMet'] if x['firstMet'] is not None and x['firstMet'] >= 0 else 4.0 for x in xs]
    print('| %s | %s | %d of %d | %.2f |' % (sw, seed, sum(x['held'] for x in xs), len(xs), statistics.median(days)))
