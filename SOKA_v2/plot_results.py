import csv
from collections import defaultdict
import statistics as stats
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

import os
import sys

ALGOS = ["MCT", "FCFS", "MINMIN"]
LABELS = {"MCT": "MCT", "FCFS": "FCFS", "MINMIN": "Min-Min"}
COLORS = {"MCT": "#1f77b4", "FCFS": "#d62728", "MINMIN": "#2ca02c"}

# Total MIPS semua VM (1.000 + 2.500 + 5.000 + 7.500), dipakai untuk batas bawah makespan.
VM_MIPS_TOTAL = 1000 + 2500 + 5000 + 7500

SEED_FILES = ["synthetic_seed1.csv", "synthetic_seed2.csv", "synthetic_seed3.csv"]

def find_dataset_dir():
    """Cari folder Dataset-Sintetik: di folder kerja, atau satu tingkat di atasnya (root repo)."""
    for cand in ["../Dataset-Sintetik", "Dataset-Sintetik", "SOKA_v2/Dataset-Sintetik"]:
        if os.path.isdir(cand):
            return cand
    raise FileNotFoundError("Folder Dataset-Sintetik tidak ditemukan (dicari di ../, ./, dan SOKA_v2/).")

# data[algo][n] -> list of metric values across 3 reps
def load(path):
    data = {a: defaultdict(list) for a in ALGOS}
    with open(path, newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            n = int(row["n_tasks"])
            data[row["algorithm"]][n].append({
                "makespan": float(row["makespan"]),
                "di": float(row["degree_of_imbalance"]),
                "util": float(row["utilization"]),
                "tput": float(row["throughput"]),
            })
    return data

def mean_series(data, algo, key):
    ns = sorted(data[algo].keys())
    return ns, [stats.mean(r[key] for r in data[algo][n]) for n in ns]

def std_series(data, algo, key):
    ns = sorted(data[algo].keys())
    return [stats.stdev(r[key] for r in data[algo][n]) if len(data[algo][n]) > 1 else 0.0 for n in ns]

def main(path="results.csv", dataset_dir=None):
    data = load(path)
    dataset_dir = dataset_dir or find_dataset_dir()

    # 1. Makespan vs jumlah task
    plt.figure(figsize=(9, 5.5))
    for a in ALGOS:
        ns, y = mean_series(data, a, "makespan")
        plt.plot(ns, y, label=LABELS[a], color=COLORS[a], linewidth=1.8)
    plt.xlabel("Jumlah task (n)")
    plt.ylabel("Makespan rata-rata (detik)")
    plt.title("Makespan vs Ukuran Dataset (rata-rata 3 kali pengujian)")
    plt.legend()
    plt.grid(alpha=0.3)
    plt.tight_layout()
    plt.savefig("chart_makespan.png", dpi=150)

    # 2. Deviasi makespan thd batas bawah teoritis (ΣMI/ΣMIPS per dataset, per n)
    #    Total MI per (n, rep) dihitung ulang dari dataset asli.
    tot_mi = defaultdict(dict)  # tot_mi[seedfile][n] = total MI
    for seedfile in SEED_FILES:
        with open(os.path.join(dataset_dir, seedfile), newline="", encoding="utf-8") as f:
            rd = csv.reader(f)
            next(rd)
            mi = [int(r[1]) for r in rd]
        for n in sorted(data["MCT"].keys()):
            tot_mi[seedfile][n] = sum(mi[:n])
    sum_mips = float(VM_MIPS_TOTAL)

    # dev[algo][n] = list of % deviation per rep (3 nilai)
    dev = {a: defaultdict(list) for a in ALGOS}
    with open(path, newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            n = int(row["n_tasks"])
            lb = tot_mi[row["dataset_file"]][n] / sum_mips
            dev[row["algorithm"]][n].append((float(row["makespan"]) - lb) / lb * 100)

    plt.figure(figsize=(9, 5.5))
    for a in ALGOS:
        ns = sorted(dev[a].keys())
        plt.plot(ns, [stats.mean(dev[a][n]) for n in ns], label=LABELS[a], color=COLORS[a], linewidth=1.8)
    plt.axhline(0, color="gray", linestyle="--", linewidth=1)
    plt.xlabel("Jumlah task (n)")
    plt.ylabel("Deviasi makespan thd batas bawah (%)")
    plt.title("Deviasi Makespan terhadap Batas Bawah Teoritis (ΣMI/ΣMIPS)")
    plt.legend()
    plt.grid(alpha=0.3)
    plt.tight_layout()
    plt.savefig("chart_makespan_diff.png", dpi=150)

    # 3. Degree of Imbalance
    plt.figure(figsize=(9, 5.5))
    for a in ALGOS:
        ns, y = mean_series(data, a, "di")
        plt.plot(ns, y, label=LABELS[a], color=COLORS[a], linewidth=1.8)
    plt.xlabel("Jumlah task (n)")
    plt.ylabel("Degree of Imbalance (rata-rata)")
    plt.title("Degree of Imbalance vs Ukuran Dataset")
    plt.legend()
    plt.grid(alpha=0.3)
    plt.tight_layout()
    plt.savefig("chart_imbalance.png", dpi=150)

    # 4. Utilization
    plt.figure(figsize=(9, 5.5))
    for a in ALGOS:
        ns, y = mean_series(data, a, "util")
        plt.plot(ns, y, label=LABELS[a], color=COLORS[a], linewidth=1.8)
    plt.xlabel("Jumlah task (n)")
    plt.ylabel("Utilization (rata-rata)")
    plt.title("Utilisasi VM vs Ukuran Dataset")
    plt.ylim(0, 1.05)
    plt.legend()
    plt.grid(alpha=0.3)
    plt.tight_layout()
    plt.savefig("chart_utilization.png", dpi=150)

    # 5. Tabel ringkasan mean±std untuk laporan
    with open("summary.csv", "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["algorithm", "n_tasks", "makespan_mean", "makespan_std", "di_mean", "util_mean", "throughput_mean"])
        for a in ALGOS:
            for n in sorted(data[a].keys()):
                rows = data[a][n]
                w.writerow([
                    a, n,
                    f'{stats.mean(r["makespan"] for r in rows):.2f}',
                    f'{stats.stdev(r["makespan"] for r in rows):.2f}' if len(rows) > 1 else "0.00",
                    f'{stats.mean(r["di"] for r in rows):.4f}',
                    f'{stats.mean(r["util"] for r in rows):.4f}',
                    f'{stats.mean(r["tput"] for r in rows):.6f}',
                ])
    print("Selesai: chart_makespan.png, chart_makespan_diff.png, chart_imbalance.png, chart_utilization.png, summary.csv")

if __name__ == "__main__":
    # Pemakaian: python plot_results.py [results.csv] [folder-dataset]
    results = sys.argv[1] if len(sys.argv) > 1 else "results.csv"
    dataset = sys.argv[2] if len(sys.argv) > 2 else None
    main(results, dataset)
