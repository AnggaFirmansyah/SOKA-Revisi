"""
validasi_python.py — replika Python dari tiga algoritma di BatchRunner.java
(MCT, FCFS, Min-Min) untuk memeriksa hasil simulasi CloudSim Plus.

Pemakaian (dari folder SOKA_v2):
    python skrip/validasi_python.py dataset/synthetic_seed1.csv 1000

Keluaran: makespan, Degree of Imbalance, utilisasi, dan throughput untuk
ketiga algoritma pada n task pertama dataset. Angka ini seharusnya sama
(selisih < 1%) dengan baris yang sesuai di hasil/results.csv.

Catatan:
  - Setting mengikuti BatchRunner.java: 4 VM dengan MIPS 1.000 / 2.500 / 5.000 / 7.500,
    setiap VM 1 PE, dan task dieksekusi berurutan (SpaceShared) mulai t = 0.
  - Makespan = waktu siap maksimum VM. DI = (maks Tj - min Tj) / rata-rata Tj,
    dengan Tj = total waktu sibuk VM j.
  - Min-Min memakai matriks completion time, jadi kompleksitasnya O(n^2 * m).
    Untuk n besar proses bisa lambat, tapi tetap praktis sampai n = 10.000.
"""
import csv
import sys

import numpy as np

VM_MIPS = np.array([1000.0, 2500.0, 5000.0, 7500.0])


def read_lengths(path, n):
    """Ambil n panjang task pertama (kolom lengthMI) dari file dataset."""
    lengths = []
    with open(path, newline="", encoding="utf-8") as f:
        rd = csv.reader(f)
        next(rd)  # header
        for row in rd:
            if len(lengths) >= n:
                break
            lengths.append(float(row[1]))
    return np.array(lengths)


def fcfs(lengths, mips):
    ready = np.zeros(len(mips))
    for L in lengths:
        j = int(np.argmin(ready))  # argmin: indeks pertama bila seri, sama dengan Java
        ready[j] += L / mips[j]
    return ready


def mct(lengths, mips):
    ready = np.zeros(len(mips))
    for L in lengths:
        ct = ready + L / mips
        j = int(np.argmin(ct))
        ready[j] = ct[j]
    return ready


def min_min(lengths, mips):
    n, m = len(lengths), len(mips)
    ready = np.zeros(m)
    done = np.zeros(n, dtype=bool)
    for _ in range(n):
        # CT untuk semua task tersisa x semua VM; baris = task (urutan asli), kolom = VM.
        ct = ready[None, :] + lengths[:, None] / mips[None, :]
        ct[done, :] = np.inf
        flat = int(np.argmin(ct))  # indeks pertama dalam urutan baris-lalu-kolom, sama dengan Java
        i, j = divmod(flat, m)
        ready[j] = ct[i, j]
        done[i] = True
    return ready


def metrics(ready, lengths, mips):
    makespan = float(ready.max())
    m = len(mips)
    t_avg = float(ready.mean())
    di = (ready.max() - ready.min()) / t_avg if t_avg > 0 else 0.0
    util = float(ready.sum()) / (m * makespan) if makespan > 0 else 0.0
    tput = len(lengths) / makespan if makespan > 0 else 0.0
    return makespan, di, util, tput


def main():
    if len(sys.argv) < 3:
        print("Pemakaian: python skrip/validasi_python.py <dataset.csv> <n>")
        sys.exit(1)
    path = sys.argv[1]
    n = int(sys.argv[2])
    lengths = read_lengths(path, n)
    if len(lengths) < n:
        print(f"[ERROR] Dataset hanya punya {len(lengths)} task, diminta {n}.")
        sys.exit(1)

    print(f"Dataset: {path}  n = {n}")
    print("algoritma,makespan,degree_of_imbalance,utilization,throughput")
    for name, fn in [("MCT", mct), ("FCFS", fcfs), ("MINMIN", min_min)]:
        ready = fn(lengths, VM_MIPS)
        ms, di, ut, tp = metrics(ready, lengths, VM_MIPS)
        print(f"{name},{ms:.2f},{di:.4f},{ut:.4f},{tp:.6f}")


if __name__ == "__main__":
    main()
