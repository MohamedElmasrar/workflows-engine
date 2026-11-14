#!/usr/bin/env python3
"""Measures throughput and latency of manual executions against the compose environment.

Usage: scripts/load-test.py [executions=200] [client-concurrency=20]
Workflow: Manual trigger -> Transform -> Condition -> Log (three worker tasks per execution).
Reported numbers are measured, not estimated; see docs/performance.md for a recorded run.
"""
import concurrent.futures, json, statistics, sys, time, urllib.request, urllib.parse, uuid
from datetime import datetime

GATEWAY, KEYCLOAK = "http://localhost:8080", "http://localhost:8180"
N = int(sys.argv[1]) if len(sys.argv) > 1 else 200
CONCURRENCY = int(sys.argv[2]) if len(sys.argv) > 2 else 20


def token():
    data = urllib.parse.urlencode({"grant_type": "password", "client_id": "workflow-cli",
                                   "username": "demo", "password": "demo"}).encode()
    return json.load(urllib.request.urlopen(f"{KEYCLOAK}/realms/workflow-platform/protocol/openid-connect/token", data))["access_token"]


TOKEN = token()


def call(method, path, body=None, headers=None):
    req = urllib.request.Request(GATEWAY + path, method=method, data=None if body is None else json.dumps(body).encode(),
                                 headers={"Authorization": f"Bearer {TOKEN}", "Content-Type": "application/json", **(headers or {})})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)


definition = {"schemaVersion": 1, "settings": {"maxConcurrentExecutions": 50, "executionTimeoutSeconds": 600}, "nodes": [
    {"id": "start", "type": "MANUAL_TRIGGER", "name": "Start", "config": {}},
    {"id": "shape", "type": "TRANSFORM", "name": "Shape", "config": {"set": {"n": "{{ trigger.n }}"}}},
    {"id": "check", "type": "CONDITION", "name": "Even?", "config": {"conditions": [{"left": "{{ nodes.shape.output.n }}", "operator": "GREATER_THAN", "right": -1}]}},
    {"id": "log", "type": "LOG", "name": "Log", "config": {"message": "n={{ nodes.shape.output.n }}"}}],
    "edges": [{"id": "e1", "source": "start", "target": "shape"}, {"id": "e2", "source": "shape", "target": "check"},
              {"id": "e3", "source": "check", "target": "log", "sourceHandle": "true"}]}
wf = call("POST", "/api/v1/workflows", {"name": f"Load test {datetime.now():%H:%M:%S}", "definition": definition})
call("POST", f"/api/v1/workflows/{wf['id']}/publish")
time.sleep(2)


def start(i):
    t0 = time.perf_counter()
    for _ in range(20):
        try:
            ex = call("POST", f"/api/v1/workflows/{wf['id']}/executions", {"input": {"n": i}}, {"Idempotency-Key": str(uuid.uuid4())})
            return ex["id"], (time.perf_counter() - t0) * 1000
        except urllib.error.HTTPError as e:
            if e.code not in (409, 429):
                raise
            time.sleep(0.5)
    raise RuntimeError("start failed")


t_start = time.time()
with concurrent.futures.ThreadPoolExecutor(CONCURRENCY) as pool:
    results = list(pool.map(start, range(N)))
submit_seconds = time.time() - t_start
ids = [r[0] for r in results]
request_ms = sorted(r[1] for r in results)

pending = set(ids)
details = {}
while pending and time.time() - t_start < 600:
    page = call("GET", f"/api/v1/executions?workflowId={wf['id']}&size=100&page=0")
    for p in range(page["totalPages"]):
        for e in call("GET", f"/api/v1/executions?workflowId={wf['id']}&size=100&page={p}")["items"]:
            if e["status"] in ("COMPLETED", "FAILED", "CANCELLED", "TIMED_OUT"):
                details[e["id"]] = e
                pending.discard(e["id"])
    time.sleep(1)


def ts(s):
    return datetime.fromisoformat(s.replace("Z", "+00:00")).timestamp()


done = [details[i] for i in ids if i in details]
e2e = sorted((ts(e["finishedAt"]) - ts(e["createdAt"])) * 1000 for e in done)
wall = max(ts(e["finishedAt"]) for e in done) - min(ts(e["createdAt"]) for e in done)
pct = lambda xs, p: xs[min(len(xs) - 1, int(round(p / 100 * (len(xs) - 1))))]
print(json.dumps({
    "executions": N, "client_concurrency": CONCURRENCY,
    "completed": sum(e["status"] == "COMPLETED" for e in done), "not_terminal": len(pending),
    "start_request_ms": {"p50": round(pct(request_ms, 50)), "p95": round(pct(request_ms, 95)), "max": round(request_ms[-1])},
    "end_to_end_ms": {"p50": round(pct(e2e, 50)), "p95": round(pct(e2e, 95)), "max": round(e2e[-1])},
    "submit_seconds": round(submit_seconds, 1),
    "wall_clock_seconds_first_created_to_last_finished": round(wall, 1),
    "throughput_executions_per_second": round(len(done) / wall, 1),
    "worker_tasks_per_second": round(3 * len(done) / wall, 1)}, indent=2))
