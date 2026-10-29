# Minimum Completion Time (MCT) - Cloud Task Scheduling

Implementasi dan ujicoba algoritma **Minimum Completion Time (MCT)** untuk penjadwalan task independen di lingkungan cloud tersimulasi, menggunakan [CloudSim Plus](http://cloudsimplus.org/) 7.3.0. Dibuat untuk mata kuliah SOKA, Tugas Minggu 4.

Dua algoritma pembanding, yaitu FCFS dan Min-Min turut diimplementasikan untuk memperkuat analisis perbandingan.

## **Kelompok 2**

| No | Nama                              | NRP        |
|----|-----------------------------------|------------|
| 1  | Revalina Erica Permatasari        | 5027241007 |
| 2  | Clarissa Aydin Rahmazea           | 5027241014 |
| 3  | Muhammad Afrizan Rasya            | 5027241048 |
| 4  | Angga Firmansyah                  | 5027241062 |
| 5  | Jofanka Al-kautsar Pangestu Abady | 5027241107 |

## **Daftar Isi**

- [Algoritma](#algoritma)
- [Arsitektur Simulasi](#arsitektur-simulasi)
- [Revisi Minggu 4](#revisi-minggu-4)
- [Dataset](#dataset)
- [Cara Menjalankan](#cara-menjalankan)
- [Hasil Ujicoba](#hasil-ujicoba)
- [Validasi](#validasi)
- [Struktur Repo](#struktur-repo)
- [Keterbatasan](#keterbatasan)
- [Real World Scenario](#real-world-scenario)
- [Referensi](#referensi)

## Algoritma

**Minimum Completion Time (MCT)** memproses task satu per satu sesuai urutan kedatangan, dan memetakan setiap task ke VM yang memberikan *completion time* paling awal.

```math
CT(T_i, V_j) = RT(V_j) + ET(T_i, V_j)
```

- $`RT(V_j)`$ : ready time VM j (kapan VM tersebut bebas)
- $`ET(T_i, V_j)`$ : waktu eksekusi task i pada VM j = $\mathrm{length}(T_i) / \mathrm{MIPS}(V_j)$
- Jika beberapa VM memberi CT yang sama, dipilih VM dengan **indeks terkecil**

```
ready[j] = 0 untuk setiap VM j
U = daftar task, terurut sesuai urutan kedatangan

untuk setiap task i di U:
    untuk setiap VM j:
        CT[i][j] = ready[j] + ET(i, j)
    j* = argmin_j( CT[i][j] )     // jika seri: indeks j terkecil
    tugaskan task i ke VM j*
    ready[j*] = CT[i][j*]

makespan = max_j( ready[j] )
```

MCT dipilih sebagai algoritma utama karena (1) memperhitungkan beban VM saat ini sehingga lebih adaptif pada lingkungan heterogen dibanding FCFS, dan (2) kompleksitasnya `O(n·m)` lebih ringan dibanding Min-Min yang `O(n²·m)`.

| Algoritma | Cara memilih | Kompleksitas |
|---|---|---|
| **MCT** (utama) | VM dengan completion time paling awal, per task sesuai urutan | $O(n \cdot m)$ |
| FCFS (baseline) | VM dengan ready time tercepat, tanpa melihat lama eksekusi | $\sim O(n)$ |
| Min-Min (pembanding) | Dari semua task tersisa, pilih completion time minimum paling kecil dulu | $O(n^2 \cdot m)$ |

## **Arsitektur Simulasi**

Mengikuti spesifikasi Draft Design Project (Tugas Minggu 3), dengan revisi Minggu 4
(lihat [Revisi Minggu 4](#revisi-minggu-4)):

| Entitas | Jumlah | Spesifikasi |
|---|---|---|
| Datacenter | 1 | `DatacenterSimple` dengan `VmAllocationPolicyFirstFit` dipasang **eksplisit** (draft §3.1 semula memakai default CloudSim; direvisi sesuai catatan dosen) |
| Host | 2 | Identik, sesuai draft Tabel 4: 8 PE × 100.000 MIPS, RAM 32.000 MB. Keempat VM ditempatkan ke Host 0; Host 1 cadangan |
| VM | 4 | Small 1.000 / Medium 2.500 / Large 5.000 / XLarge 7.500 MIPS — 1 PE, RAM 4.096 MB, `SpaceShared` |
| Cloudlet | 1.000 (single-run) / 100–10.000 (batch sweep, lihat `SOKA_v2/`) | 1 PE per task, `UtilizationModelDynamic(1.0)` |

## **Revisi Minggu 4**

Menjawab catatan revisi terhadap laporan Minggu 4:

| # | Revisi diminta | Sekarang |
|---|---|---|
| 1 | Uji coba 100–10.000 task (kelipatan 100), 3 kali, rata-rata, digrafikkan | `SOKA_v2/BatchRunner.java`: 100 skala × 3 seed × 3 algoritma = 900 run + grafik (lihat `SOKA_v2/`) |
| 2 | Kriteria dataset diatur sendiri | Dataset sintetis dengan kriteria sendiri (3 kelas beban Short/Medium/Long, proporsi & rentang MI ditentukan tim, **bukan** meniru GoCJ), reproducible lewat `SOKA_v2/SyntheticDatasetGenerator.java` |
| 3 | 1 host diisi 4 VM, tidak random; jangan diserahkan ke CloudSim langsung | Topologi **tetap 2 Host × 8 PE** (sesuai draft Tabel 3–4). Penempatan VM kini **eksplisit**: `VmAllocationPolicyFirstFit` dipasang di `DatacenterSimple` pada **seluruh** simulasi (`MctSimulation`, `FcfsSimulation`, `MinMinSimulation`, `BatchRunner`), sehingga keempat VM selalu mendarat di Host 0 (deterministik) dan Host 1 menjadi cadangan. Penempatan dicetak/diperiksa tiap run. Ini merevisi draft §3.1 yang semula memakai kebijakan default |
| 4 | **(koreksi internal)** rumus Degree of Imbalance | Semula memakai rata-rata *finish time* seluruh cloudlet → DI antar-algoritma nyaris identik. Kini memakai total **busy time per VM** (`ready[]`): `(max Tj − min Tj) / mean Tj` |

Detail lengkap (kriteria dataset, hasil sweep, grafik) ada di [`SOKA_v2/README.md`](SOKA_v2/README.md).

## **Dataset**

Dua dataset dipakai: **GoCJ** untuk single-run 1.000 task (bagian ini), dan **dataset sintetis buatan sendiri** untuk sweep n = 100–10.000 (kriteria dan generator di [`SOKA_v2/README.md`](SOKA_v2/README.md) §4).

**GoCJ (Google Cloud Jobs)** — Hussain & Aleem (2018), [Mendeley Data](https://data.mendeley.com/datasets/b7bp6xhrcd/1). Ukuran job dalam Million Instructions (MI), dibangkitkan dari analisis Google cluster trace.

File yang dipakai: `GoCJ_1000_task_simulation.csv` — 1.000 task, hanya kolom `lengthMI` yang dipakai.

| Statistik | Nilai |
|---|---|
| Minimum / Maksimum | 15.000 / 900.000 MI |
| Median / Rata-rata | 89.000 / 121.815 MI |
| Total beban | 121.815.000 MI |
| Batas bawah teoritis makespan | ΣMI / ΣMIPS ≈ **7.613,44 detik** |

## Cara Menjalankan

Prasyarat: Java 17, Maven. Langkah di bawah untuk single-run (GoCJ, 1.000 task); sweep n = 100–10.000 dijelaskan di [`SOKA_v2/README.md`](SOKA_v2/README.md) §7.

```bash
mvn compile
mvn exec:java -Dexec.mainClass="MctSimulation"
mvn exec:java -Dexec.mainClass="FcfsSimulation"
mvn exec:java -Dexec.mainClass="MinMinSimulation"
```

Setiap run membaca `GoCJ_1000_task_simulation.csv` dan menulis hasil ke `Hasil_Simulasi_<Algoritma>_<timestamp>.csv` berisi `Cloudlet_ID, Status, VM_ID, Actual_CPU_Time, Start_Time, Finish_Time` per task, ditutup satu baris ringkasan makespan.

## Hasil Ujicoba

Seluruh 1.000 cloudlet berstatus **SUCCESS** pada ketiga run.

| Algoritma | Makespan (detik) | Selisih thd batas bawah | Distribusi VM (Small/Medium/Large/XLarge) |
|---|---|---|---|
| **MCT** | **7.690,06** | +1,01% | 137 / 236 / 352 / 275 |
| FCFS | 7.724,00 | +1,46% | 49 / 148 / 328 / 475 |
| Min-Min | 7.781,37 | +2,20% | 61 / 155 / 313 / 471 |

**Peringkat:** MCT < FCFS < Min-Min. Selisih antar-algoritma kecil (<1,2%), ketiganya berada dalam 1-2,2% dari batas bawah teoritis.

Metrik tambahan untuk MCT:

| Metrik | Nilai |
|---|---|
| Degree of Imbalance | 0,011 |
| Resource Utilization | 99,1% |
| Throughput | 0,13 task/detik |

### Temuan menarik: Min-Min sedikit lebih lambat dari FCFS

Berlawanan dengan ekspektasi umum, Min-Min (7.781,37 detik) tampil sedikit di bawah FCFS (7.724,00 detik) pada dataset ini. Ini konsisten dengan kelemahan Min-Min yang terdokumentasi di literatur (Braun et al., 2001): Min-Min cenderung memprioritaskan task kecil berturut-turut ke VM tercepat, sehingga VM tersebut menumpuk banyak task. Dataset GoCJ bersifat *right-skewed* (919 dari 1.000 task ≤ 135.000 MI, 63 task > 500.000 MI), yang kemungkinan memperkuat efek ini.

## **Validasi**

Setiap algoritma direplikasi manual (Python, logika identik dengan kode Java) dan dibandingkan dengan hasil CloudSim Plus:

| Algoritma | Manual | CloudSim Plus | Selisih |
|---|---|---|---|
| MCT | 7.634,80 | 7.690,06 | 0,72% |
| FCFS | 7.628,47 | 7.724,00 | 1,25% |
| Min-Min | 7.686,67 | 7.781,37 | 1,23% |

Selisih 0,7-1,3% konsisten di ketiganya, sejalan dengan jeda minimum antar-event bawaan CloudSim Plus (`minTimeBetweenEvents` = 0,1 detik). Khusus MCT, pemetaan task→VM hasil hitungan manual dibandingkan satu per satu dengan keluaran simulator: **kecocokan 100%**.

## **Struktur Repo**

```
├── src/main/java/
│   ├── MctSimulation.java      # algoritma utama
│   ├── FcfsSimulation.java     # baseline
│   └── MinMinSimulation.java   # pembanding
├── GoCJ_1000_task_simulation.csv        # dataset
├── Hasil_Simulasi_MCT_*.csv             # hasil run MCT
├── Hasil_Simulasi_FCFS_*.csv            # hasil run FCFS
├── Hasil_Simulasi_MinMin_*.csv          # hasil run Min-Min
├── pom.xml
└── SOKA_v2/                             # REVISI Minggu 4 (uji coba skala)
    ├── SyntheticDatasetGenerator.java   # generator dataset sintetis (kriteria sendiri)
    ├── BatchRunner.java                 # sweep n=100–10.000, 3 seed, 3 algoritma
    ├── plot_results.py                  # 5 grafik + summary.csv
    ├── Dataset-Sintetik/                # synthetic_seed1..3.csv
    ├── results.csv / summary.csv        # hasil sweep
    ├── chart_*.png                      # 5 grafik
    ├── real_world/                      # dispatcher.py, worker.py, manifest kind/pod, hasil + grafik real-world
    └── README.md                        # dokumentasi lengkap revisi (cara menjalankan, hasil, analisis)
```

## **Keterbatasan**

- **Total cost (F2)** belum dihitung untuk ketiga algoritma; baru makespan (F1), imbalance (F3), utilization, dan throughput yang terukur lengkap.
- Konfigurasi homogen (4 VM identik) belum dijalankan.
- Waktu komputasi algoritma belum diukur secara eksplisit.
- Ujicoba real-world dijalankan pada satu laptop (Docker Desktop + kind) dengan host dan VM logical; belum diuji pada dua perangkat fisik atau cloud sungguhan (lihat [Real World Scenario](#real-world-scenario)).
- Penempatan VM→host tidak memengaruhi makespan karena kapasitas host jauh melampaui kebutuhan 4 VM dan mapping task→VM dihitung di layer aplikasi, bukan oleh CloudSim.


## **Real World Scenario**

> **Status:** run penuh 1.000 task sudah selesai (rata-rata 4 run per algoritma, 29 Oktober 2026). Kode, panduan setup lengkap, grafik, dan data mentah ada di [`SOKA_v2/real_world/`](SOKA_v2/real_world/README.md).

Setelah melakukan simulasi melalui CloudSim, pengujian dilanjutkan secara real world pada cluster Kubernetes lokal (kind di atas Docker Desktop). Arsitekturnya sebagai berikut.

```text
                  dispatcher.py
          (assign: MCT / FCFS / Min-Min)
                        │  kubectl exec
        ┌───────────────┴────────────────┐
        ▼                                ▼
┌─────────────────┐              ┌─────────────────┐
│ Host 1 (worker) │              │ Host 2 (worker) │
│ ┌─────┐ ┌─────┐ │              │ ┌─────┐ ┌─────┐ │
│ │VM-0 │ │VM-3 │ │              │ │VM-1 │ │VM-2 │ │
│ │133m │ │1000m│ │              │ │333m │ │667m │ │
│ └─────┘ └─────┘ │              │ └─────┘ └─────┘ │
└─────────────────┘              └─────────────────┘
   (1 datacenter = 1 cluster kind, worker.py jalan di tiap pod)
                        ▼
          start/end tiap task → metrik
```

- **Datacenter** = 1 cluster kind (`soka`). **Host** = 2 worker node. **VM** = 4 pod `python:3.12-slim`, 2 pod per host.
- **Dispatcher** (`dispatcher.py`) menghitung penjadwalan, mengirim daftar task ke tiap worker lewat `kubectl exec` (stdin), lalu mengumpulkan waktu mulai dan selesai tiap task.
- **Worker** (`worker.py`) mengeksekusi task dengan menghabiskan CPU time. Karena CPU limit pod dibatasi (cgroup), pod yang lebih kecil otomatis membutuhkan waktu lebih lama (CFS throttling).

| VM | Host | MIPS | CPU limit pod |
|---|---|---|---|
| VM-0 | host-1 | 1.000 | 133m |
| VM-1 | host-2 | 2.500 | 333m |
| VM-2 | host-2 | 5.000 | 667m |
| VM-3 | host-1 | 7.500 | 1000m |

### Model task

Satu task dengan panjang $L_i$ (MI) membutuhkan CPU time pada satu core penuh:

$$\text{CPU time} = \frac{L_i}{7500 \cdot S}$$

dengan $S = 50$ sebagai faktor percepatan waktu, agar satu run 1.000 task cukup sekitar 4 menit dan bukan sekitar 12.000 detik. Seluruh waktu hasil pengukuran dikalikan kembali dengan $S$, sehingga bisa dibandingkan dengan makespan ideal model yang sama (satuan detik simulasi).

### Algoritma

Waktu eksekusi dan waktu selesai task $T_i$ pada VM $V_j$:

$$ET(T_i, V_j) = \frac{\mathrm{length}(T_i)}{\mathrm{MIPS}(V_j)}$$

$$CT(T_i, V_j) = RT(V_j) + ET(T_i, V_j)$$

| Algoritma | Aturan pemilihan | Kompleksitas |
|---|---|---|
| FCFS | VM dengan $RT$ terkecil, tanpa melihat durasi task | $\sim O(n)$ |
| MCT | VM dengan $CT(T_i, V_j)$ terkecil untuk tiap task | $O(n \cdot m)$ |
| Min-Min | pasangan (task, VM) dengan $CT$ global terkecil, diulang | $O(n^2 \cdot m)$ |

### Metrik

- **Makespan** = $\max_j F_j$, dengan $F_j$ waktu selesai VM $V_j$.
- **Degree of Imbalance** $DI = \dfrac{\max_j T_j - \min_j T_j}{\overline{T}}$, dengan $T_j$ total waktu sibuk VM $V_j$ (sama dengan `dispatcher.py`).
- **Utilization** = total waktu sibuk seluruh VM / ($m \cdot$ makespan)
- **Throughput** = jumlah task / makespan

### Cara menjalankan

Prasyarat: Docker Desktop (alokasikan CPU ≥ 4 core), `kubectl`, `kind`, dan Python 3. Setup cluster langkah demi langkah ada di [`SOKA_v2/real_world/README.md`](SOKA_v2/real_world/README.md). Jika cluster sudah siap:

```powershell
cd SOKA_v2/real_world
kubectl get pods -l app=vm -o wide
python dispatcher.py mct --n 50        # uji kecil
python dispatcher.py mct --n 1000      # run penuh (sekitar 4 menit)
python dispatcher.py mct --n 1000 --dry   # hanya hitung jadwal, tanpa cluster
python plot_real_results.py            # ringkasan + grafik
```

Setiap run menulis `real_<algoritma>_<timestamp>.csv` (detail per task: `task,vm,start,end`).

### Validasi bahwa CPU limit bekerja

```bash
kubectl get nodes -L host -o wide
kubectl get pods -l app=vm -o wide
for p in vm-0 vm-1 vm-2 vm-3; do printf "%s: " "$p"; kubectl exec "$p" -- cat /sys/fs/cgroup/cpu.max; done
```

`cpu.max` keempat pod berisi 14000 / 34000 / 67000 / 100000 per periode 100000 µs. Kubernetes membulatkan kuota ke atas, sehingga limit efektif 140m / 340m / 670m / 1000m.

### Hasil

Semua hasil di bawah memakai `synthetic_seed1.csv`, run 29 Oktober 2026 (Docker 4 CPU). Run awal 26 Oktober disimpan di `arsip/26okt/` dan tidak dipakai (hasilnya terganggu kondisi host).

**Uji kecil, 50 task, 1 run per algoritma**

| Algoritma | Makespan real (s) | Ideal (s) | DI | Utilization |
|---|---:|---:|---:|---:|
| MCT | 567,02 | 569,08 | 0,0858 | 0,9695 |
| FCFS | 590,71 | 603,91 | 0,0918 | 0,9463 |
| Min-Min | 649,83 | 648,15 | 1,3241 | 0,6631 |

**Run penuh, 1.000 task, rata-rata dari 4 run**

| Algoritma | Makespan real (s) | Rentang (s) | Ideal (s) | Overhead | DI | Utilization |
|---|---:|---|---:|---:|---:|---:|
| MCT | 11.967,47 | 11.963,75 – 11.972,65 | 11.939,99 | +0,23% | 0,0654 | 0,9731 |
| FCFS | 11.900,35 | 11.889,13 – 11.908,68 | 12.039,02 | -1,15% | 0,0447 | 0,9844 |
| Min-Min | 11.996,96 | 11.993,75 – 12.002,84 | 12.051,62 | -0,45% | 0,1132 | 0,9617 |

### Analisis

1. **Makespan real hampir setara pada n=1.000.** Selisih FCFS dan MCT sekitar 0,56%, MCT dan Min-Min sekitar 0,25%, sedangkan variasi antar-run hanya 0,07% sampai 0,16%.
2. **Urutan ideal berbeda dari urutan real.** MCT terbaik pada jadwal ideal, tetapi FCFS terendah pada eksekusi nyata karena pembulatan kuota CPU Kubernetes (vm-1 pada FCFS mendapat tambahan kecepatan, vm-3 pada MCT tidak). Dengan kecepatan efektif, prediksi ideal berbeda dari rata-rata terukur hanya 0,01% sampai 0,25%.
3. **MCT paling baik pada n=50**, urutannya sama dengan urutan ideal.
4. **FCFS paling merata, Min-Min paling timpang** (menumpuk task pada VM tercepat, DI 0,1132 pada n=1.000 dan 1,3241 pada n=50).

Analisis lengkap ada di [`SOKA_v2/real_world/README.md`](SOKA_v2/real_world/README.md).

### Keterbatasan

- Penjadwalan masih **offline**: semua task diassign di awal dan dianggap tiba di $t = 0$.
- Datacenter, host, dan VM bersifat **logical** (cluster kind, node, pod) pada **satu laptop fisik** yang berbagi kernel, sehingga ada noise antar-pod yang tidak ada pada VM atau server terpisah. Hasil sensitif terhadap kondisi host (terlihat pada run 26 Oktober).
- Beban hanya **CPU-bound**. RAM, I/O, dan jaringan tidak dimodelkan, begitu pula kolom `pesramMB` dan `priority` pada dataset.
- Waktu dipercepat dengan faktor $S = 50$. Overhead tetap (baca `stdin`, pencatatan waktu) ikut terkalikan $S$ saat dikonversi ke detik simulasi.
- Estimasi waktu eksekusi dianggap akurat ($ET = L / \mathrm{MIPS}$), tanpa variasi runtime.
- Estimasi biaya cloud adalah CPU-equivalent estimate, bukan tagihan aktual.

### Struktur berkas

```
SOKA_v2/real_world/
├── worker.py                  # dijalankan di dalam tiap pod
├── dispatcher.py              # penjadwalan + orkestrasi pod + perhitungan metrik
├── kind-config.yaml           # cluster kind: 1 control-plane + 2 worker (host-1, host-2)
├── vms.yaml                   # 4 pod VM dengan CPU limit
├── plot_real_results.py       # ringkasan + grafik dari real_*.csv
├── test_*.py                  # unit test
├── real_summary.csv           # ringkasan per run
├── real_*.csv                 # detail per task (task, vm, start, end)
├── real_*.png                 # grafik
└── arsip/                     # run awal 26 Okt dan ulangan n=1.000
```

## **Referensi**

- Braun, T.D., Siegel, H.J., Beck, N., Bölöni, L.L., Maheswaran, M., et al. (2001). A comparison of eleven static heuristics for mapping a class of independent tasks onto heterogeneous distributed computing systems. *Journal of Parallel and Distributed Computing*, 61(6), 810–837.
- Calheiros, R.N., Ranjan, R., Beloglazov, A., De Rose, C.A.F., Buyya, R. (2011). CloudSim: a toolkit for modeling and simulation of cloud computing environments and evaluation of resource provisioning algorithms. *Software: Practice and Experience*, 41(1), 23–50.
- Hussain, A., Aleem, M. (2018). GoCJ: Google Cloud Jobs Dataset for Distributed and Cloud Computing Infrastructures. *Data*, 3(4), 38. https://doi.org/10.3390/data3040038
- Silva Filho, M.C., Oliveira, R.L., Monteiro, C.C., Inácio, P.R.M., Freire, M.M. (2017). CloudSim Plus: a cloud computing simulation framework pursuing software engineering principles for improved modularity, extensibility and correctness. *IFIP/IEEE IM*, Lisbon.
