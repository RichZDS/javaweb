"""20 个新账号同时刷题：每人开一次 20 题随机练习并逐题提交，统计提交答案的耗时和失败数。"""
import http.client, json, math, secrets, sys, threading, time

HOST, PORT, BASE = "127.0.0.1", 18090, "/quiz/api"
ROOT_ID, USERS = int(sys.argv[1]), int(sys.argv[2])
lock, lat, errors = threading.Lock(), [], []
barrier = threading.Barrier(USERS)


def worker():
    conn = http.client.HTTPConnection(HOST, PORT, timeout=60)

    def call(method, path, body=None, token=None):
        h = {"Content-Type": "application/json"}
        if token:
            h["Authorization"] = "Bearer " + token
        t0 = time.perf_counter()
        conn.request(method, BASE + path, body=json.dumps(body).encode() if body is not None else None, headers=h)
        r = conn.getresponse()
        raw = r.read()
        return (time.perf_counter() - t0) * 1000, r.status, (json.loads(raw)["data"] if raw else None)

    _, st, auth = call("POST", "/auth/register", {"username": "perf_" + secrets.token_hex(4),
                                                  "password": secrets.token_urlsafe(12)})
    tok = auth["token"]
    call("PUT", "/me", {"targetCategoryId": ROOT_ID}, tok)
    barrier.wait()
    _, st, s = call("POST", "/practice/sessions", {"mode": "RANDOM", "categoryIds": [ROOT_ID], "count": 20}, tok)
    for q in s["questions"]:
        t = q["type"]
        ans = {"SINGLE": ["A"], "MULTIPLE": ["A", "B"], "JUDGE": ["T"]}.get(t, ["x"] * max(1, q.get("blankCount") or 1))
        ms, st, _ = call("POST", f"/practice/sessions/{s['id']}/answers",
                         {"questionId": q["id"], "userAnswer": ans, "timeSpentSec": 5}, tok)
        with lock:
            (lat if st == 200 else errors).append(ms if st == 200 else st)


t0 = time.perf_counter()
threads = [threading.Thread(target=worker) for _ in range(USERS)]
for t in threads:
    t.start()
for t in threads:
    t.join()
wall = time.perf_counter() - t0
s = sorted(lat)
n = len(s)
print(f"{USERS} 人同时，各提交 20 题：成功 {n} 次，失败 {len(errors)} 次 {errors[:5]}；"
      f"中位数 {(s[n // 2 - 1] + s[n // 2]) / 2:.1f} ms，95 分位 {s[math.ceil(0.95 * n) - 1]:.1f} ms，最大 {s[-1]:.1f} ms；"
      f"总用时（含注册）{wall:.1f} s")
