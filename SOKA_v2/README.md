# Uji Coba Skala Dataset — Penjadwalan Task Cloud (MCT, FCFS, Min-Min)

Revisi Tugas MCT (Minggu 4) mata kuliah **SOKA — Strategi Optimasi Komputasi Awan**, Kelompok 2.
Implementasi menggunakan **CloudSim Plus 7.3.0** dengan Java 17.

Revisi ini menjawab tiga catatan dari laporan sebelumnya:

1. **Skala uji coba diperluas**: n = 100 s.d. 10.000 task (kelipatan 100), tiap skala **diulang 3 kali** (dataset sintetis seed 1/2/3), diambil **rata-rata**, lalu dibuat grafik.
2. **Kriteria dataset diatur sendiri**: dataset sintetis dibangkitkan sendiri (3 seed), bukan hanya GoCJ.
3. **Alokasi VM ke host dieksplisitkan**: 2 host (masing-masing 8 PE), dengan pemetaan VM→host **ditetapkan sendiri** — VM-0 & VM-3 ke Host-0, VM-1 & VM-2 ke Host-1 — lewat `setFindHostForVmFunction`, **tidak diserahkan ke kebijakan default CloudSim**.

---

## 1. Anggota Kelompok

| No | Nama                              | NRP        |
|----|-----------------------------------|------------|
| 1  | Revalina Erica Permatasari        | 5027241007 |
| 2  | Clarissa Aydin Rahmazea           | 5027241014 |
| 3  | Muhammad Afrizan Rasya            | 5027241048 |
| 4  | Angga Firmansyah                  | 5027241062 |
| 5  | Jofanka Al-kautsar Pangestu Abady | 5027241107 |

---

## 2. Perubahan dari Versi Sebelumnya (Catatan Revisi)

| # | Revisi diminta | Sebelumnya | Sekarang |
|---|----------------|------------|----------|
| 1 | Uji coba 100–10.000 task (kelipatan 100), 3 kali pengujian, rata-rata, dimasukkan ke grafik | Hanya 1.000 task, 1 run per algoritma | 100 skala × 3 seed × 3 algoritma = **900 run**; rata-rata per skala digrafikkan (`chart_*.png`) |
| 2 | Kriteria dataset diatur sendiri | GoCJ bawaan | Dataset **sintetis dibangkitkan sendiri** dari kriteria yang didefinisikan sendiri (3 kelas beban, proporsi & rentang MI ditentukan tim), reproducible lewat `SyntheticDatasetGenerator.java` |
| 3 | Alokasi VM ke host dibuat eksplisit, jangan random / jangan diserahkan ke CloudSim langsung | 2 host, penempatan VM diserahkan ke `DatacenterSimple` (kebijakan default bawaan) | **2 host** (masing-masing 8 PE × 100.000 MIPS) dengan pemetaan **eksplisit** via `setFindHostForVmFunction`: VM-0 & VM-3 → Host-0, VM-1 & VM-2 → Host-1 |
| 4 | Kode lengkap + step-by-step | — | Bab 6 dan 7 dokumen ini |
| 5 | **Koreksi internal:** rumus *Degree of Imbalance* | Rata-rata *finish time* seluruh cloudlet dibagi jumlah VM → DI antar-algoritma nyaris identik (tidak informatif) | DI dihitung dari **total waktu sibuk per VM** (`ready[]`), mengikuti definisi `(max Tj − min Tj) / mean Tj` — lihat §5 |

Selain itu, dari daftar keterbatasan versi sebelumnya yang kini terjawab:

- Skenario ukuran dataset bervariasi (n = 100 ... 10.000) **sudah dijalankan**.
- Variansi antar-run (repeat) **sudah diukur** (mean ± std, lihat `summary.csv`).

---

## 3. Arsitektur Simulasi

Mengikuti Draft Design Project (Tugas Minggu 3):

| Entitas | Jumlah | Spesifikasi |
|---|---|---|
| Datacenter | 1 | `DatacenterSimple`, kebijakan alokasi VM **didefinisikan sendiri** (lihat §3.1) |
| Host | **2** | Masing-masing 8 PE × 100.000 MIPS, RAM 32.000 MB, BW 1.000.000 Mbps — kapasitas total 16 PE, cukup untuk 4 VM |
| VM | 4 | Small 1.000 / Medium 2.500 / Large 5.000 / XLarge 7.500 MIPS — 1 PE, RAM 4.096 MB, `CloudletSchedulerSpaceShared` |
| Cloudlet | 100–10.000 | 1 PE per task, `UtilizationModelDynamic(1.0)` |

### 3.1 Alokasi VM ke Host (revisi poin 3)

Pada versi sebelumnya, daftar host diserahkan ke `DatacenterSimple` dan CloudSim Plus memakai
kebijakan penempatan bawaannya. Sekarang penempatan VM→host **ditetapkan sendiri** lewat
`setFindHostForVmFunction` pada `VmAllocationPolicySimple`:

- Ada **2 host**, masing-masing 8 PE × 100.000 MIPS. Pemetaan VM→host tetap dan deterministik
  (tidak ada randomisasi):

  | VM | MIPS | Host |
  |---|---|---|
  | VM-0 (Small) | 1.000 | **Host-0** |
  | VM-1 (Medium) | 2.500 | **Host-1** |
  | VM-2 (Large) | 5.000 | **Host-1** |
  | VM-3 (XLarge) | 7.500 | **Host-0** |

- Penempatan bukan lagi perilaku bawaan CloudSim: `DatacenterSimple` dibangun dengan
  `VmAllocationPolicySimple` yang fungsi `findHostForVm`-nya dioverride eksplisit
  (`policy.setFindHostForVmFunction((p, vm) -> Optional.ofNullable(placement.get(vm)))`),
  sehingga mapping VM→host adalah **bagian dari desain eksperimen**.
- Setiap host menampung 2 VM = 2 PE, sedangkan tiap host menyediakan 8 PE → keempat VM aktif
  bersamaan, konsisten dengan skenario draft.
- Penempatan tidak mengubah makespan (VM tetap berjalan dengan kecepatan MIPS yang sama),
  tetapi **log alokasi** kini menunjukkan VM-0/VM-3 di Host-0 dan VM-1/VM-2 di Host-1.

### 3.2 Algoritma yang dibandingkan

| Algoritma | Aturan pemilihan | Kompleksitas |
|---|---|---|
| **MCT** (utama) | Tiap task (sesuai urutan kedatangan) diassign ke VM dengan *completion time* paling awal: `CT(i,j) = ready(j) + length(i)/MIPS(j)` | `O(n·m)` |
| FCFS (baseline) | Tiap task diassign ke VM dengan *ready time* paling awal, tanpa melihat panjang task | `~O(n)` |
| Min-Min (pembanding) | Dari semua task tersisa, pasangan (task, VM) dengan CT global terkecil dikerjakan duluan; diulang hingga habis | `O(n²·m)` |

Penjadwalan ketiganya dihitung **di luar event engine CloudSim** (layer aplikasi), lalu hasil
mapping task→VM diserahkan ke simulator untuk eksekusi — pemisahan yang sama dengan versi
sebelumnya, sehingga hasil 1.000 task lama tetap konsisten.

---

## 4. Dataset Sintetis

Dataset **dibangkitkan sendiri** lewat `SyntheticDatasetGenerator.java` (bukan meniru statistik
GoCJ), supaya kriteria beban bisa dijelaskan dan hasilnya reproducible.

### 4.1 Kriteria dataset (diatur sendiri)

Beban kerja dimodelkan **tri-modal (right-skewed)**: mayoritas task pendek, sebagian menengah,
dan ekor panjang yang jarang — meniru pola umum beban cloud, tetapi parameter angkanya kami
tentukan sendiri:

| Kelas beban | Proporsi | Rentang panjang (MI) | Rata-rata kelas (MI) |
|---|---|---|---|
| **Short** | 50% | 10.000 – 150.000 | 80.000 |
| **Medium** | 35% | 200.000 – 450.000 | 325.000 |
| **Long** | 15% | 500.000 – 1.000.000 | 750.000 |

- Panjang task di tiap kelas **uniform integer** di dalam rentangnya.
- Rata-rata teoritis per task = 0,50 × 80.000 + 0,35 × 325.000 + 0,15 × 750.000 = **266.250 MI**.
  Rata-rata hasil generator: **264.900 – 267.100 MI/task** (per seed). Kelas Short/Medium/Long
  tidak saling tumpang tindih rentangnya.
- **Reproducible**: `java.util.Random` dengan seed eksplisit **1 / 2 / 3**, sehingga dataset
  yang sama selalu dihasilkan ulang (deterministik).

Angka min/median/mean dataset ini **berbeda jelas** dari GoCJ asli (GoCJ: 15.000 / 89.000 /
121.815 MI), jadi bukan resample dari GoCJ.

### 4.2 File

| File | Isi |
|---|---|
| `SOKA_v2/Dataset-Sintetik/synthetic_seed1.csv` | 10.000 task (seed 1) |
| `SOKA_v2/Dataset-Sintetik/synthetic_seed2.csv` | 10.000 task (seed 2) |
| `SOKA_v2/Dataset-Sintetik/synthetic_seed3.csv` | 10.000 task (seed 3) |

- Format: `taskId,lengthMI` (panjang task dalam Million Instructions).
- Untuk skala n, kode mengambil **n baris pertama** dari file seed terpilih.
- Repetisi ke-1 memakai seed1, ke-2 seed2, ke-3 seed3 → rata-rata dari 3 seed
  mengurangi bias urutan task pada satu dataset.

### 4.3 Batas bawah teoritis makespan

Makespan tidak mungkin di bawah `ΣMI / ΣMIPS`, dengan ΣMIPS = 1.000 + 2.500 + 5.000 + 7.500 = 16.000.
Untuk n = 10.000 (seed1): ΣMI = 2.648.893.633 → **batas bawah = 165.555,85 detik**.
Rata-rata 3 seed: ΣMI = 2.658.043.399 → batas bawah ≈ 166.127,71 detik.
Untuk n = 1.000 (seed1): batas bawah ≈ 16.443,07 detik.

---

## 5. Metrik

| Metrik | Rumus |
|---|---|
| **Makespan** | `max_j F(j)` — waktu selesai VM terakhir |
| **Degree of Imbalance (DI)** | `(max_j Tj − min_j Tj) / mean_j Tj`, dengan `Tj` = total waktu **sibuk** VM j |
| **Utilization** | `Σ busy time semua VM / (m × makespan)` |
| **Throughput** | `jumlah task / makespan` |

Kolom `results.csv` (satu baris per run):

```
algorithm, n_tasks, rep, dataset_file, makespan, degree_of_imbalance, utilization, throughput, cloudlet_success
```

- `algorithm`: `MCT` / `FCFS` / `MINMIN`; `n_tasks`: 100–10.000; `rep`: 1–3 (seed 1/2/3);
  `dataset_file`: nama berkas seed; `cloudlet_success`: jumlah cloudlet yang selesai (= `n_tasks`).

> **Catatan koreksi DI:** versi awal salah memakai rata-rata *finish time* seluruh cloudlet
> dibagi jumlah VM, sehingga DI MCT/FCFS/Min-Min nyaris identik (contoh lama: ketiganya
> 0,0082 di n=1.000). Karena semua task tiba di t=0, `Tj` = ready time akhir VM j, yang
> sudah tersimpan di array `ready[]` hasil penjadwalan. Setelah koreksi, DI antar-algoritma
> terpisah dengan benar (n=1.000: MCT 0,0087 / FCFS 0,0139 / Min-Min 0,0699).

---

## 6. Kode Lengkap

Struktur folder `SOKA_v2`:

```
SOKA_v2/
├── README.md                    # dokumen ini
├── BatchRunner.java             # sweep 100–10.000 × 3 seed × 3 algoritma (900 run)
├── SyntheticDatasetGenerator.java  # pembangkit dataset sintetis (kriteria §4.1, reproducible)
├── plot_results.py              # grafik + summary.csv (mean ± std per skala)
├── validasi_python.py           # replika Python MCT/FCFS/Min-Min untuk memeriksa hasil Java
├── Dataset-Sintetik/            # synthetic_seed1..3.csv (10.000 task tiap file)
├── .gitignore
│
│   (dibuat saat menjalankan, tidak disimpan di repo:)
├── out/                         # hasil compile .class
├── cp.txt                       # classpath Maven (dibuat oleh perintah di §7)
├── results.csv                  # 900 baris hasil mentah
├── summary.csv                  # rata-rata ± std per (algoritma, n)
└── chart_*.png                  # grafik
```

Ringkasan kode:

- `SyntheticDatasetGenerator.java`: menulis `taskId,lengthMI` untuk seed 1/2/3. Konstanta
  proporsi (`SHORT_PROP`, `MEDIUM_PROP`) dan rentang MI ada di bagian atas file.
- `BatchRunner.java`:
  - Konfigurasi: `HOST_MIPS/HOST_PES/...` dan `VM_MIPS = {1000, 2500, 5000, 7500}`.
  - `buildRunPlan()`: daftar (n, seed) untuk seluruh sweep — n kelipatan 100 sampai 10.000, 3 repetisi.
  - `runOnce(algo, n, csvFile)`: membangun 1 Datacenter + **2 Host** (masing-masing 8 PE),
    menempatkan 4 VM ke host secara eksplisit (`setFindHostForVmFunction`), menghitung mapping
    task→VM sesuai algoritma, menjalankan simulasi, lalu mengembalikan 1 baris CSV.
  - `fcfsAssign / mctAssign / minMinAssign`: ketiga algoritma. Min-Min memakai array boolean
    agar tetap praktis untuk n = 10.000.
  - `readCsv()`: membaca kolom `lengthMI`.
- `plot_results.py`: membaca `results.csv` dan folder dataset (dicari otomatis di `../`, `./`,
  atau `SOKA_v2/`), lalu menulis grafik dan `summary.csv`. Pemakaian:
  `python plot_results.py [results.csv] [folder-dataset]`.
- `validasi_python.py`: mereplikasi ketiga algoritma di Python untuk n task pertama dataset.
  Dipakai untuk memeriksa `results.csv` (lihat §7, langkah 4).

---

## 7. Cara Menjalankan

### Prasyarat

- JDK 17 dan Maven (CloudSim Plus 7.3.0 dikelola lewat `pom.xml` di root repo).
- Python 3 dengan `matplotlib` dan `numpy`: `pip install matplotlib numpy`.

Semua perintah di bawah dijalankan dari **root repo** kecuali disebutkan lain.
Di Windows, ganti pemisah classpath `:` dengan `;`.

### Langkah 1 — Siapkan classpath (sekali saja)

```bash
mvn -q dependency:build-classpath -Dmdep.outputFile=SOKA_v2/cp.txt
CP=$(cat SOKA_v2/cp.txt)
mkdir -p SOKA_v2/out
javac -cp "$CP" -d SOKA_v2/out SOKA_v2/SyntheticDatasetGenerator.java SOKA_v2/BatchRunner.java
```

### Langkah 2 — (Opsional) Bangkitkan ulang dataset sintetis

Hasilnya deterministik; menjalankan ulang akan menghasilkan file yang sama.

```bash
java -cp SOKA_v2/out SyntheticDatasetGenerator SOKA_v2/Dataset-Sintetik 10000 3
```

### Langkah 3 — Jalankan sweep (900 run)

```bash
java -Xss128m -cp "SOKA_v2/out:$CP" BatchRunner SOKA_v2/Dataset-Sintetik SOKA_v2/results.csv
```

Output: `SOKA_v2/results.csv`.

### Langkah 4 — Validasi dan grafik

```bash
cd SOKA_v2
python3 validasi_python.py Dataset-Sintetik/synthetic_seed1.csv 1000
python3 plot_results.py results.csv
```

Yang diharapkan dari `validasi_python.py` dibandingkan dengan baris `n_tasks = 1000`, `rep = 1`
di `results.csv`:

- `degree_of_imbalance` harus sama persis (sampai 4 desimal) untuk ketiga algoritma.
- `makespan` berbeda sekitar 0,2–0,8% (Java selalu sedikit lebih tinggi karena overhead eksekusi CloudSim).
  Pengujian pada dataset sebelumnya menghasilkan selisih 0,15–0,8% untuk n = 100 sampai 3.000.

Output grafik: `chart_makespan.png`, `chart_makespan_diff.png`, `chart_imbalance.png`,
`chart_utilization.png`, dan `summary.csv`.

---

## 8. Hasil Uji Coba

900 run (3 algoritma × 100 skala × 3 repetisi) pada dataset sintetis 50/35/15,
seluruh cloudlet berstatus **SUCCESS** (`cloudlet_success` = `n_tasks` di tiap baris).

### 8.1 Grafik utama

![Makespan vs ukuran dataset](chart_makespan.png)

![Selisih makespan terhadap batas bawah](chart_makespan_diff.png)

![Degree of imbalance](chart_imbalance.png)

![Utilization](chart_utilization.png)

### 8.2 Tabel ringkasan (rata-rata 3 seed)

| n | MCT | FCFS | Min-Min | Batas bawah |
|---|---|---|---|---|
| 100 | **1.544,58** | 1.690,00 | 1.615,41 | 1.512,64 |
| 1.000 | **17.057,17** | 17.141,49 | 17.106,38 | 16.957,01 |
| 5.000 | **83.613,06** | 83.728,57 | 83.839,99 | 83.280,90 |
| 10.000 | **166.681,08** | 167.050,91 | 167.077,65 | 166.127,71* |

(makespan, detik, rata-rata 3 seed; tabel lengkap mean ± std ada di `summary.csv`)

\* batas bawah rata-rata 3 seed; per seed dihitung dari ΣMI masing-masing dataset.

### 8.3 Analisis

1. **MCT menang konsisten.** Dari 100 skala pengujian, makespan MCT terendah di
   **seluruh 100 skala**. Rata-rata, FCFS 0,49% lebih lambat dan Min-Min 0,36% lebih lambat
   dari MCT.

2. **Ketiga algoritma mendekati batas bawah teoritis.** Deviasi makespan
   terhadap ΣMI/ΣMIPS (chart `chart_makespan_diff.png`) membaik seiring n:
   MCT 2,11% → 0,33%, FCFS 11,93% → 0,56%, Min-Min 6,78% → 0,57% (dari n=100
   ke n=10.000). Rata-rata deviasi: MCT **0,45%**, FCFS 0,94%, Min-Min 0,81%.
   MCT selalu paling dekat dengan batas bawah di seluruh rentang n.

3. **Deviasi kecil di n besar bukan kebetulan.** Dengan beban total jauh
   melebihi kapasitas (utilization ≈ 99%), pembagian beban greedy konvergen ke
   solusi nyaris optimal; sisa deviasi <1% berasal dari fragmentasi terakhir
   (task terbesar di tiap VM) dan jeda event bawaan CloudSim Plus.

4. **DI turun drastis dengan n, dan membedakan algoritma.** Rata-rata seluruh skala:
   MCT **0,0047** < FCFS 0,0077 < Min-Min 0,0186 — MCT paling seimbang, sesuai
   tujuannya menyeimbangkan beban. DI MCT turun dari 0,0490 (n=100) ke 0,0007
   (n=10.000); makin banyak task, makin halus pembagian beban antar VM.

5. **Utilization.** MCT 99,6% > FCFS 99,4% > Min-Min 99,0% (rata-rata seluruh skala).
   Min-Min lebih rendah terutama pada n kecil (n=100 ≈ 85,6%) karena cenderung
   menumpuk task kecil ke VM tercepat sehingga VM lain menganggur di awal run.

6. **Variansi antar seed.** Std makespan MCT pada n=10.000 ±739,05 s (0,44% dari mean);
   pada n besar rata-rata 3 repetisi stabil dan peringkat antar-algoritma konsisten.
   Variansi lebih besar di n kecil karena subset awal tiap seed punya komposisi kelas
   beban yang lebih "acak".

### 8.4 Perbandingan dengan hasil lama (n = 1.000, GoCJ)

| Algoritma | GoCJ (versi lama) | Dataset sintetis (baru, n=1.000) |
|---|---|---|
| MCT | 7.690,06 | 17.057,17 |
| FCFS | 7.724,00 | 17.141,49 |
| Min-Min | 7.781,37 | 17.106,38 |

Peringkat di dataset sintetis: MCT < Min-Min < FCFS (selisih antar-algoritma < 0,5%).
Nilai absolut berbeda karena dataset dan total beban berbeda (GoCJ ≈ 121.815 MI/task;
sintetis ≈ 265.800 MI/task) — bukan karena perubahan logika simulasi.

---

## 9. Keterbatasan

- Dataset masih di-truncate dari 10.000 task pertama per seed; skala kecil (n<1.000)
  memakai subset awal, sehingga karakter beban tiap n tidak identik (terlihat pada
  std yang lebih besar di n=500).
- Penjadwalan offline: semua task dianggap tiba di t=0.
- Estimasi waktu eksekusi diasumsikan akurat (ET = length/MIPS), tanpa noise runtime.
- Total cost belum dimodelkan.

---

## 10. Referensi

- Calheiros, R.N., et al. (2011). *CloudSim: A toolkit for modeling and simulation of cloud computing environments*. Software: Practice and Experience, 41(1), 23–50.
- Silva Filho, M.C., et al. (2017). *CloudSim Plus: A cloud computing simulation framework pursuing software engineering principles*. IFIP/IEEE IM, Lisbon.
- Braun, T.D., et al. (2001). *A comparison of eleven static heuristics for mapping a class of independent tasks onto heterogeneous distributed computing systems*. JPDC, 61(6), 810–837.
- Hussain, A., Aleem, M. (2018). *GoCJ: Google Cloud Jobs Dataset*. Data, 3(4), 38.
