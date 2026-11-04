# The benchmark's progress, posted each minute to a check run on the commit ("Benchmark progress"), so it can be
# followed while the job runs (a job's own log can be read only once it has ended): the latest status line as the
# title; the last status lines and what the companion said lately as the summary. Closed when the run says it is
# done, or when the job ends it.
import json
import os
import signal
import sys
import time
import urllib.request

LOG = sys.argv[1]
REPO = os.environ["GITHUB_REPOSITORY"]
SHA = os.environ["GITHUB_SHA"]
TOKEN = os.environ["GITHUB_TOKEN"]
# One check run a world: "Benchmark progress (smh-benchmark-2)".
NAME = "Benchmark progress" + (" (%s)" % os.environ["SEED"] if os.environ.get("SEED") else "")
API = "https://api.github.com/repos/%s/check-runs" % REPO


def call(method, url, body):
    req = urllib.request.Request(url, data=json.dumps(body).encode(), method=method,
                                 headers={"Authorization": "Bearer " + TOKEN, "Accept": "application/vnd.github+json"})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)


def read():
    try:
        with open(LOG, errors="replace") as f:
            lines = f.read().splitlines()
    except OSError:
        return "Starting", "The server is starting.", False
    bench = [l.split("[STDOUT]: ", 1)[-1] for l in lines if "[BENCH]" in l]
    said = [l.split("[STDOUT]: ", 1)[-1] for l in lines
            if "[MineAgent][" in l and "Lesson failed" not in l and "LLM call" not in l]
    # Its work traced (what it is stuck on and why), the noise left out.
    noise = ("Lesson failed", "LLM call", "saved companion", "Saved ", "Loaded ", "transport failure", "[MineAgent][",
             "paused for survival chain", "LLM retries", "Stale request", "Trimmed history")
    traced = []
    for l in lines:
        if "[MineAgent] " not in l or any(n in l for n in noise):
            continue
        t = l.split("[STDOUT]: ", 1)[-1].split("[STDERR]: ", 1)[-1]
        # A line said over and over (an error every tick) once, at its last place, so it can't push the rest out.
        if t in traced:
            traced.remove(t)
        traced.append(t)
    finished = [l for l in bench if "[BENCH] done" in l]
    done = bool(finished)
    shown = [l for l in bench if l.strip() != "[BENCH] report"]
    title = (finished[-1] if finished else shown[-1] if shown else "[BENCH] Starting").replace("[BENCH] ", "", 1)
    summary = "\n".join(["Status:"] + shown[-12:] + ["", "What it said lately:"] + said[-40:]
                        + ["", "Its work, traced:"] + [t[:400] for t in traced[-60:]])
    return title[:250], summary[:60000], done


run = None
for attempt in range(5):
    try:
        run = call("POST", API, {"name": NAME, "head_sha": SHA, "status": "in_progress",
                                 "output": {"title": "Starting", "summary": "The server is starting."}})
        break
    except Exception as e:
        print("progress: couldn't open the check run:", e, flush=True)
        time.sleep(10)
if run is None:
    sys.exit(0)
cid = run["id"]


def close(conclusion):
    title, summary, _ = read()
    try:
        call("PATCH", "%s/%d" % (API, cid), {"status": "completed", "conclusion": conclusion,
                                             "output": {"title": title, "summary": summary}})
    except Exception as e:
        print("progress: couldn't close the check run:", e, flush=True)


def stopped(signum, frame):
    close("cancelled")
    sys.exit(0)


signal.signal(signal.SIGTERM, stopped)

while True:
    time.sleep(60)
    title, summary, done = read()
    if done:
        close("success" if "MINIMUM MET" in title else "failure")
        break
    try:
        call("PATCH", "%s/%d" % (API, cid), {"output": {"title": title, "summary": summary}})
    except Exception as e:
        print("progress:", e, flush=True)
