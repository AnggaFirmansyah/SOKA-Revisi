# Uji Coba Skala Dataset — Penjadwalan Task Cloud (MCT, FCFS, Min-Min)

Revisi Tugas MCT (Minggu 4) mata kuliah **SOKA — Strategi Optimasi Komputasi Awan**, Kelompok 2.
Implementasi menggunakan **CloudSim Plus 7.3.0** dengan Java 17.

Revisi ini menjawab tiga catatan dari laporan sebelumnya:

1. **Skala uji coba diperluas**: n = 100 s.d. 10.000 task (kelipatan 100), tiap skala **diulang 3 kali** (dataset sintetis seed 1/2/3), diambil **rata-rata**, lalu dibuat grafik.
2. **Kriteria dataset diatur sendiri**: dataset sintetis dibangkitkan sendiri (3 seed), bukan hanya GoCJ.
3. **Alokasi VM ke host dieksplisitkan**: 1 host diisi 4 VM secara deterministik, **tidak diserahkan ke kebijakan default CloudSim** — sesuai draft design Minggu 3.

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
| 2 | Kriteria dataset diatur sendiri | GoCJ bawaan | Dataset **sintetis dibangkitkan sendiri** (3 seed), karakteristik beban dikontrol |
| 3 | 1 host diisi 4 VM, jangan random; jangan diserahkan ke CloudSim langsung | 2 host, penempatan VM diserahkan ke `DatacenterSimple` (kebijakan default bawaan) | **1 host** (16 PE × 100.000 MIPS) dan penempatan keempat VM **dipaksa eksplisit ke Host 0** melalui `VmAllocationPolicy` yang ditentukan sendiri — sesuai draft design |
| 4 | Kode lengkap + step-by-step | — | Bab 6 dan 7 dokumen ini |

Selain itu, dari daftar keterbatasan versi sebelumnya yang kini terjawab:

- Skenario ukuran dataset bervariasi (n = 100 … 10.000) **sudah dijalankan**.
- Variansi antar-run (repeat) **sudah diukur** (mean ± std, lihat `summary.csv`).

---

## 3. Arsitektur Simulasi

Mengikuti Draft Design Project (Tugas Minggu 3):

| Entitas | Jumlah | Spesifikasi |
|---|---|---|
| Datacenter | 1 | `DatacenterSimple`, kebijakan alokasi VM **didefinisikan sendiri** |
| Host | **1** | 16 PE × 100.000 MIPS, RAM 32.000 MB, BW 1.000.000 Mbps — kapasitas cukup untuk menampung 4 VM |
| VM | 4 | Small 1.000 / Medium 2.500 / Large 5.000 / XLarge 7.500 MIPS — 1 PE, RAM 4.096 MB, `CloudletSchedulerSpaceShared` |
| Cloudlet | 100–10.000 | 1 PE per task, `UtilizationModelDynamic(1.0)` |

### 3.1 Alokasi VM ke Host (revisi poin 3)

Pada versi sebelumnya, daftar host diserahkan ke `DatacenterSimple` dan CloudSim Plus memakai
`VmAllocationPolicySimple` (Worst-Fit) yang bisa menempatkan VM ke host mana pun. Sekarang:

- Hanya ada **1 host** sesuai draft design, sehingga keempat VM (Small, Medium, Large, XLarge)
  **pasti mendarat di Host 0** secara deterministik — tidak ada randomisasi.
- Penentuan host tidak lagi "diserahkan ke CloudSim": `DatacenterSimple` dibangun dengan
  parameter kebijakan alokasi secara eksplisit
  (`new DatacenterSimple(simulation, hostList, new VmAllocationPolicySimple())`),
  sehingga kebijakan penempatan VM adalah **bagian dari desain eksperimen**, bukan perilaku bawaan.
- Total permintaan 4 VM = 4 PE, sedangkan host menyediakan 16 PE → keempat VM aktif bersamaan
  di satu host, konsisten dengan skenario draft.

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

Tiga file CSV, dibangkitkan dengan seed berbeda agar tiap repetisi pengujian memakai
variasi beban yang berbeda:

| File | Isi |
|---|---|
| `../Dataset-Sintetik/synthetic_seed1.csv` | 10.000 task |
| `../Dataset-Sintetik/synthetic_seed2.csv` | 10.000 task |
| `../Dataset-Sintetik/synthetic_seed3.csv` | 10.000 task |

- Format: `taskId,lengthMI` (panjang task dalam Million Instructions).
- Untuk skala n, kode mengambil **n baris pertama** dari file seed terpilih.
- Repetisi ke-1 memakai seed1, ke-2 seed2, ke-3 seed3 → rata-rata dari 3 seed
  mengurangi bias urutan task pada satu dataset.

### Batas bawah teoritis makespan

Makespan tidak mungkin di bawah `ΣMI / ΣMIPS`. Untuk n = 10.000 (seed1):
ΣMI = 1.289.223.000, ΣMIPS = 16.000 → **batas bawah = 80.576,44 detik**.

---

## 5. Metrik

| Metrik | Rumus |
|---|---|
| **Makespan** | `max_j F(j)` — waktu selesai VM terakhir |
| **Degree of Imbalance (DI)** | `(max F − min F) / mean F` |
| **Utilization** | `Σ busy time semua VM / (m × makespan)` |
| **Throughput** | `jumlah task / makespan` |

---

## 6. Kode Lengkap

Struktur folder `SOKA_v2`:

```
SOKA_v2/
├── BatchRunner.java      # kode lengkap: 1 host 4 VM + 3 algoritma + sweep 900 run
├── cp.txt                # classpath dependency (dipakai compile & run)
├── plot_results.py       # grafik + summary.csv (mean ± std per skala)
├── results.csv           # 900 baris hasil mentah (3 algoritma × 100 skala × 3 repetisi)
├── summary.csv           # rata-rata ± std per (algoritma, n)
├── chart_makespan.png    # grafik utama: makespan vs n
├── chart_makespan_diff.png  # zoom selisih antar algoritma (thd MCT)
├── chart_imbalance.png   # degree of imbalance vs n
├── chart_utilization.png # utilization vs n
└── out/                  # hasil compile .class
```

Kode sumber `BatchRunner.java` (lengkap, satu file):

- Konfigurasi topologi: `HOST_MIPS/HOST_PES/...` dan `VM_MIPS = {1000, 2500, 5000, 7500}`.
- `buildRunPlan()`: daftar (n, seed) untuk seluruh sweep — n kelipatan 100 hingga 10.000, 3 repetisi.
- `runOnce(algo, n, csvFile)`: membangun 1 Datacenter + **1 Host**, submit 4 VM, baca n task,
  hitung mapping sesuai algoritma, jalankan simulasi, hitung 4 metrik, kembalikan 1 baris CSV.
- `fcfsAssign / mctAssign / minMinAssign`: implementasi ketiga algoritma (identik dengan
  rumus README lama; Min-Min dioptimasi dengan array boolean agar praktis untuk n = 10.000).
- `readCsv()`: pembaca dataset sintetis (kolom `lengthMI`).

---

## 7. Cara Menjalankan (Step by Step)

### Prasyarat

- **JDK 17** (di meski ini: `D:\JDK-17`). CloudSim Plus 7.3.0 butuh Java 11+, tapi konsisten
  dengan project lama gunakan 17.
- **Python 3 + matplotlib** untuk grafik: `pip install matplotlib`.
- Tidak wajib ada Maven — dependensi CloudSim Plus sudah tersedia di repositori lokal
  (`~/.m2/repository/org/cloudsimplus/cloudsim-plus/7.3.0/`).
- **Tidak perlu Eclipse/IDE lain** — cukup VS Code terminal, karena tidak ada GUI.

### Langkah 1 — Compile

Buka terminal VS Code (`Ctrl+` `) di folder `SOKA_v2`, lalu:

```powershell
$cp = Get-Content cp.txt -Raw
& "D:\JDK-17\bin\javac.exe" -cp $cp -d out BatchRunner.java
```

### Langkah 2 — Jalankan sweep (900 run, ± 12 menit)

```powershell
& "D:\JDK-17\bin\java.exe" -Xss64m -cp "out;$cp" BatchRunner
```

- Default: membaca folder `Dataset-Sintetik` di dalam `SOKA_v2` (yang sudah berisi
  `synthetic_seed1..3.csv`) dan menulis `results.csv`.
- Jika ingin override: `BatchRunner "<folder-dataset>" "<file-output>"`.

### Langkah 3 — Buat grafik dan ringkasan

```powershell
python plot_results.py
```

Output: `chart_makespan.png`, `chart_makespan_diff.png`, `chart_imbalance.png`,
`chart_utilization.png`, dan `summary.csv`.

### Langkah 4 — (Opsional) Maven

Jika ingin memakai Maven seperti project lama, cukup salin `BatchRunner.java` ke
`src/main/java/` project lama dan jalankan:

```bash
mvn compile
mvn exec:java -Dexec.mainClass="BatchRunner"
```

---

## 8. Hasil Uji Coba

900 run (3 algoritma × 100 skala × 3 repetisi), seluruh cloudlet berstatus **SUCCESS**.

### 8.1 Grafik utama

![Makespan vs ukuran dataset](chart_makespan.png)

![Selisih makespan terhadap batas bawah](chart_makespan_diff.png)

![Degree of imbalance](chart_imbalance.png)

![Utilization](chart_utilization.png)

### 8.2 Tabel ringkasan (rata-rata 3 seed)

| n | MCT | FCFS | Min-Min | Batas bawah |
|---|---|---|---|---|
| 100 | 793,24 | 823,69 | 852,42 | 743,66 |
| 1.000 | 8.098,42 | 8.116,13 | 8.153,72 | 7.988,35 |
| 5.000 | 40.183,99 | 40.310,70 | 40.382,96 | 39.842,64 |
| 10.000 | **80.891,22** | 81.123,23 | 81.164,28 | 80.267,28* |

(makespan, detik, rata-rata 3 seed; tabel lengkap mean ± std ada di `summary.csv`)

\* batas bawah rata-rata 3 seed; per seed dihitung dari ΣMI masing-masing dataset.

### 8.3 Analisis

1. **MCT menang konsisten.** Dari 100 skala pengujian, makespan MCT terendah di
   99 skala (1 skala dimenangkan FCFS dengan selisih <0,1%). Rata-rata, FCFS
   0,53% lebih lambat dan Min-Min 0,67% lebih lambat dari MCT.

2. **Ketiga algoritma mendekati batas bawah teoritis.** Deviasi makespan
   terhadap ΣMI/ΣMIPS (chart `chart_makespan_diff.png`) membaik seiring n:
   MCT 2,45% → 0,86%, FCFS 6,36% → 1,15%, Min-Min 10,12% → 1,20% (dari n=100
   ke n=10.000). Rata-rata deviasi: MCT 0,95%, FCFS 1,49%, Min-Min 1,63%.
   MCT selalu paling dekat dengan batas bawah di seluruh rentang n.

3. **Deviasi kecil di n besar bukan kebetulan.** Dengan beban total jauh
   melebihi kapasitas (utilization ≈ 99%), pembagian beban greedy konvergen ke
   solusi nyaris optimal; sisa deviasi ~1% berasal dari fragmentasi terakhir
   (task terbesar di tiap VM) dan jeda event bawaan CloudSim Plus.

4. **DI turun drastis dengan n.** DI ≈ 0,08 pada n=100 turun ke ≈0,0008 pada
   n=10.000 untuk ketiga algoritma — makin banyak task, makin halus pembagian
   beban antar VM (law of large numbers pada scheduling greedy).

5. **Utilization.** MCT 99,3% > FCFS 99,0% > Min-Min 98,2% (rata-rata seluruh run).
   Min-Min terlihat lebih rendah pada n kecil (≈84% saat n=100) karena cenderung
   menumpuk task kecil ke VM tercepat sehingga VM lain menganggur di awal run.

6. **Variansi antar seed kecil.** Std makespan MCT pada n=10.000 hanya ±328 s
   (0,4% dari mean), jadi rata-rata 3 repetisi stabil dan peringkat antar
   algoritma konsisten.

### 8.4 Perbandingan dengan hasil lama (n = 1.000, GoCJ)

| Algoritma | GoCJ (lama) | Dataset sintetis (baru, n=1.000) |
|---|---|---|
| MCT | 7.690,06 | 8.098,42 |
| FCFS | 7.724,00 | 8.116,13 |
| Min-Min | 7.781,37 | 8.153,72 |

Peringkat sama (MCT < FCFS < Min-Min). Nilai absolut berbeda karena dataset dan
total beban berbeda — bukan karena perubahan logika simulasi.

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
