"""Dispatcher: hitung penjadwalan (MCT/FCFS/Min-Min), kirim ke VM (pod) di cluster
Kubernetes via kubectl exec, kumpulkan hasil, hitung metrik.
Pakai:  python dispatcher.py mct --n 50          (uji kecil)
        python dispatcher.py mct                 (1000 task)
        python dispatcher.py mct --dry           (hanya hitung jadwal, tanpa cluster)
        python dispatcher.py mct --speedup 100   (lebih cepat, task makin pendek)"""
import argparse, csv, json, subprocess, time, concurrent.futures as cf

MIPS = [1000, 2500, 5000, 7500]      # vm-0 .. vm-3
S = 50                               # faktor percepatan waktu (bisa diubah: --speedup)
FULL_CORE_MIPS = 7500

# Referensi harga On-Demand Linux, USD/jam, region US. Harga dapat berubah.
# Harga terakhir dicatat pada 2026-10-26 dan hanya dipakai sebagai baseline laporan.
COST_PROVIDERS = {
    "aws": {"label": "AWS EC2 t3a.small", "vcpu": 2, "hourly": 0.0188, "region": "us-east-1"},
    "gcp": {"label": "GCP Compute Engine e2-standard-2", "vcpu": 2, "hourly": 0.06701142, "region": "us-central1"},
    "azure": {"label": "Azure VM Standard_DS1_v2", "vcpu": 1, "hourly": 0.073, "region": "eastus"},
}


def estimate_cost(provider, runtime_seconds):
    """Estimate CPU-equivalent cloud cost from simulated VM runtimes."""
    profile = COST_PROVIDERS[provider]
    per_vm = [
        seconds / 3600 * (MIPS[j] / FULL_CORE_MIPS) * profile["hourly"] / profile["vcpu"]
        for j, seconds in enumerate(runtime_seconds)
    ]
    return {"per_vm": per_vm, "total": sum(per_vm)}

def load_tasks(path, n):
    with open(path) as f:
        L = [int(r["lengthMI"]) for r in csv.DictReader(f)]
    return L[:n] if n else L

# ---------- algoritma penjadwalan: return assign[j] = [task index, ...] urut eksekusi ----------
def mct(L):
    ready = [0.0] * len(MIPS); assign = [[] for _ in MIPS]
    for i, l in enumerate(L):                         # task diproses urut kedatangan
        ct = [ready[j] + l / MIPS[j] for j in range(len(MIPS))]
        j = ct.index(min(ct))                         # CT terkecil, seri -> indeks terkecil
        ready[j] = ct[j]; assign[j].append(i)
    return assign

def fcfs(L):
    ready = [0.0] * len(MIPS); assign = [[] for _ in MIPS]
    for i, l in enumerate(L):
        j = ready.index(min(ready))                   # VM paling cepat bebas, abaikan durasi task
        ready[j] += l / MIPS[j]; assign[j].append(i)
    return assign

def minmin(L):
    ready = [0.0] * len(MIPS); assign = [[] for _ in MIPS]; left = set(range(len(L)))
    while left:
        best = min(((ready[j] + L[i] / MIPS[j], i, j) for i in left for j in range(len(MIPS))))
        _, i, j = best
        ready[j] += L[i] / MIPS[j]; assign[j].append(i); left.remove(i)
    return assign

ALGO = {"mct": mct, "fcfs": fcfs, "minmin": minmin}

def ideal_metrics(L, assign):
    F = [sum(L[i] / MIPS[j] for i in assign[j]) for j in range(len(MIPS))]
    return max(F), F

# ---------- interaksi dengan cluster ----------
def kubectl(*a, **kw):
    return subprocess.run(["kubectl", *a], capture_output=True, text=True, **kw)

def show_topology():
    out = kubectl("get", "pods", "-l", "app=vm", "-o", "json")
    pods = json.loads(out.stdout)["items"]
    topo = {}
    for p in pods:
        topo.setdefault(p["spec"]["nodeName"], []).append(p["metadata"]["name"])
    print("Datacenter: kind-cluster")
    for node, vms in sorted(topo.items()):
        print(f"  Host {node}: {', '.join(sorted(vms))}")
    assert len(topo) >= 2 and all(len(v) >= 2 for v in topo.values()), "topologi < 2 host / 2 VM per host!"

def run_vm(j, tasks, L, start_at):
    payload = json.dumps({"start_at": start_at,
                          "tasks": [[i, L[i] / (FULL_CORE_MIPS * S)] for i in tasks]})
    r = kubectl("exec", "-i", f"vm-{j}", "--", "python", "/app/worker.py", input=payload)
    if r.returncode: raise RuntimeError(f"vm-{j}: {r.stderr}")
    return [json.loads(x) for x in r.stdout.splitlines()]

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("algo", choices=ALGO); ap.add_argument("--n", type=int, default=0)
    ap.add_argument("--csv", default="../Dataset-Sintetik/synthetic_seed1.csv"); ap.add_argument("--speedup", type=int, default=50); ap.add_argument("--dry", action="store_true")
    ap.add_argument("--cost-provider", choices=["all", *COST_PROVIDERS], default="all")
    a = ap.parse_args()
    global S; S = a.speedup

    L = load_tasks(a.csv, a.n)
    assign = ALGO[a.algo](L)
    ideal, _ = ideal_metrics(L, assign)
    print(f"[{a.algo.upper()}] {len(L)} task | distribusi per VM: {[len(x) for x in assign]} | makespan ideal: {ideal:.2f} s")
    if a.dry: return

    show_topology()
    start_at = time.time() + 5 + 0.5 * len(MIPS)      # kasih waktu semua exec siap, lalu mulai serentak
    with cf.ThreadPoolExecutor(len(MIPS)) as ex:
        res = list(ex.map(lambda j: run_vm(j, assign[j], L, start_at), range(len(MIPS))))

    rows, F, busy = [], [], []
    for j, r in enumerate(res):
        F.append((max(x["end"] for x in r) - start_at) * S if r else 0)
        busy.append(sum(x["end"] - x["start"] for x in r) * S)
        rows += [(x["id"], j, (x["start"] - start_at) * S, (x["end"] - start_at) * S) for x in r]
    mk = max(F); di = (max(busy) - min(busy)) / (sum(busy) / len(busy))
    util = sum(busy) / (len(MIPS) * mk)
    print(f"Makespan real : {mk:.2f} s   (ideal {ideal:.2f}, selisih {100*(mk-ideal)/ideal:+.2f}%)")
    print(f"DI            : {di:.4f}\nUtilization   : {util:.4f}\nThroughput    : {len(L)/mk:.4f} task/s")
    providers = COST_PROVIDERS if a.cost_provider == "all" else {a.cost_provider: COST_PROVIDERS[a.cost_provider]}
    print("Estimated cloud cost (USD, CPU-equivalent, simulated duration):")
    for provider, profile in providers.items():
        cost = estimate_cost(provider, F)
        detail = ", ".join(f"vm-{j}=${value:.6f}" for j, value in enumerate(cost["per_vm"]))
        print(f"  {profile['label']} ({profile['region']}): ${cost['total']:.6f} total [{detail}]")
    fn = f"real_{a.algo}_{time.strftime('%Y%m%d_%H%M%S')}.csv"
    with open(fn, "w", newline="") as f:
        w = csv.writer(f); w.writerow(["task", "vm", "start", "end"]); w.writerows(sorted(rows))
    print("Detail per task ->", fn)

if __name__ == "__main__":
    main()
