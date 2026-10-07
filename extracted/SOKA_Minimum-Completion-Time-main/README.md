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
- [Dataset](#dataset)
- [Cara Menjalankan](#cara-menjalankan)
- [Hasil Ujicoba](#hasil-ujicoba)
- [Validasi](#validasi)
- [Struktur Repo](#struktur-repo)
- [Keterbatasan](#keterbatasan)
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

Mengikuti spesifikasi Draft Design Project (Tugas Minggu 3):

| Entitas | Jumlah | Spesifikasi |
|---|---|---|
| Datacenter | 1 | `DatacenterSimple`, kebijakan bawaan CloudSim Plus |
| Host | 2 | 8 PE × 100.000 MIPS, RAM 32.000 MB, identik |
| VM | 4 | Small 1.000 / Medium 2.500 / Large 5.000 / XLarge 7.500 MIPS — 1 PE, RAM 4.096 MB, `SpaceShared` |
| Cloudlet | 1.000 | 1 PE per task, `UtilizationModelDynamic(1.0)` |

## **Dataset**

**GoCJ (Google Cloud Jobs)** — Hussain & Aleem (2018), [Mendeley Data](https://data.mendeley.com/datasets/b7bp6xhrcd/1). Ukuran job dalam Million Instructions (MI), dibangkitkan dari analisis Google cluster trace.

File yang dipakai: `GoCJ_1000_task_simulation.csv` — 1.000 task, hanya kolom `lengthMI` yang dipakai.

| Statistik | Nilai |
|---|---|
| Minimum / Maksimum | 15.000 / 900.000 MI |
| Median / Rata-rata | 89.000 / 121.815 MI |
| Total beban | 121.815.000 MI |
| Batas bawah teoritis makespan | ΣMI / ΣMIPS ≈ **7.613,44 detik** |

## Cara Menjalankan

Prasyarat: Java 17, Maven.

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
└── pom.xml
```

## **Keterbatasan**

- **Total cost (F2)** belum dihitung untuk ketiga algoritma; baru makespan (F1), imbalance (F3), utilization, dan throughput yang terukur lengkap (khusus MCT).
- Skenario ukuran dataset lebih kecil (n = 100, 200, 400, 800) belum dijalankan.
- Konfigurasi homogen (4 VM identik) belum dijalankan.
- Waktu komputasi algoritma belum diukur secara eksplisit.
- Ujicoba real-world (eksekusi task pada resource nyata) belum dilakukan.


## **Real World Scenario**

Setelah melakukan simulasi melalui CloudSim, kita dapat melakuakn pengujian secara real world menggunakan Docker. Untuk arsitekturnya sebagai berikut.

```text
               dispatcher.py
       (assign: MCT / FCFS / Min-Min)
                      │
     ┌──────────┬─────┴────┬──────────┐
     ▼          ▼          ▼          ▼
┌─────────┐┌─────────┐┌─────────┐┌─────────┐
│   VM-0  ││   VM-1  ││   VM-2  ││   VM-3  │
│0,133 CPU││0,333 CPU││0,667 CPU││1,000 CPU│
│worker.py││worker.py││worker.py││worker.py│
└─────────┘└─────────┘└─────────┘└─────────┘
     │          │          │          │
     └──────────┴─────┬────┴──────────┘
                      ▼
        start/end tiap task → metrik
```

- **VM** = container Docker `python:3.12-slim` dengan batas CPU (`--cpus`) yang rasionya sama dengan MIPS di simulasi.
- **Dispatcher** (`dispatcher.py`) menghitung penjadwalan, mengirim daftar task ke tiap worker lewat `stdin`, lalu mengumpulkan waktu mulai dan selesai tiap task.
- **Worker** (`worker.py`) mengeksekusi task dengan menghabiskan CPU time. Karena CPU container dibatasi, container yang lebih lambat otomatis membutuhkan waktu lebih lama (CFS throttling).

| VM | MIPS | `--cpus` |
|---|---|---|
| VM-0 | 1.000 | 0,133333 |
| VM-1 | 2.500 | 0,333333 |
| VM-2 | 5.000 | 0,666667 |
| VM-3 | 7.500 | 1,000000 |

### Model task

Satu task dengan panjang $L_i$ (MI) membutuhkan CPU time pada satu core penuh:

$$\text{CPU time} = \frac{L_i}{7500 \cdot S}$$

dengan $S = 50$ sebagai faktor percepatan waktu, agar satu run penuh cukup sekitar 2,5 menit dan bukan 7.690 detik. Seluruh waktu hasil pengukuran dikalikan kembali dengan $S$, sehingga bisa dibandingkan langsung dengan CloudSim (satuan detik simulasi).

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
- **Degree of Imbalance** $DI = \dfrac{\max_j F_j - \min_j F_j}{\overline{F}}$
- **Utilization** = total waktu sibuk seluruh VM / ($m \cdot$ makespan)
- **Throughput** = jumlah task / makespan

### Cara menjalankan

Prasyarat: Docker Desktop (alokasikan CPU ≥ 4 core), Python 3, dan `pip install pandas`.

```bash
docker pull python:3.12-slim
python dispatcher.py mct 50      # uji kecil: algoritma + jumlah task
```

Run penuh (3 putaran, urutan algoritma diselang-seling):

```powershell
1..3 | % { foreach($a in 'mct','fcfs','minmin'){ python dispatcher.py $a } }
python summarize.py
```

Setiap run menulis `real_<algoritma>_<timestamp>.csv` (detail per task) dan menambah satu baris ke `results.csv`.

### Validasi bahwa CPU limit bekerja

Saat run berjalan, di terminal lain:

```bash
docker stats --no-stream --format "table {{.ID}}\t{{.CPUPerc}}"
docker inspect --format "{{.HostConfig.NanoCpus}}" <container_id>
```

CPU% keempat container harus mendekati 13% / 33% / 67% / 100%, dan `NanoCpus` bernilai 133333000 / 333333000 / 666667000 / 1000000000.

### Hasil

**Uji awal, 50 task, 1 run per algoritma**
<img width="804" height="182" alt="image" src="https://github.com/user-attachments/assets/fc39267d-da59-4a24-8386-80d2da09beb2" />

| Algoritma | Makespan real | Ideal (hitungan) | DI | Utilization |
|---|---|---|---|---|
| MCT | 377,48 | 377,47 | 0,2912 | 0,8427 |
| FCFS | 415,73 | 418,80 | 0,3703 | 0,7942 |
| Min-Min | 426,32 | 427,10 | 0,8207 | 0,6936 |

**Run penuh, 1.000 task, rata-rata ± std dari TBD run**

<img width="799" height="187" alt="image" src="https://github.com/user-attachments/assets/255876c2-0f96-4b08-b896-5e85db71b557" />

| Algoritma | Makespan real (mean ± std) | Ideal | CloudSim | DI | Utilization |
|---|---|---|---|---|---|
| MCT | TBD | 7.634,80 | 7.690 | TBD | TBD |
| FCFS | TBD | 7.628,47 | 7.724 | TBD | TBD |
| Min-Min | TBD | 7.686,67 | 7.781 | TBD | TBD |

> Isi `TBD` dari keluaran `summarize.py`.

### Analisis

Isi setelah run penuh. Poin yang perlu dijawab:

1. Apakah urutan algoritma pada eksekusi real sama dengan di CloudSim?
2. Seberapa besar selisih makespan real terhadap nilai ideal dan terhadap CloudSim (dalam persen)?
3. Apakah selisih antar-algoritma lebih besar dari standar deviasi antar-run? Jika tidak, perbedaannya tidak signifikan secara statistik dan itu perlu ditulis apa adanya.

### Keterbatasan

- Penjadwalan masih **offline**: semua task diassign di awal dan dianggap tiba di $t = 0$.
- "VM" adalah container pada **satu mesin fisik** yang berbagi kernel, sehingga ada noise antar-container yang tidak ada pada VM terpisah.
- Beban hanya **CPU-bound**. RAM, I/O, dan jaringan tidak dimodelkan, begitu pula kolom `pesramMB` dan `priority` pada dataset.
- Waktu dipercepat dengan faktor $S = 50$. Overhead tetap (baca `stdin`, pencatatan waktu) ikut terkalikan $S$ saat dikonversi ke detik simulasi.
- Estimasi waktu eksekusi dianggap akurat ($ET = L / \mathrm{MIPS}$), tanpa variasi runtime.

### Struktur berkas

```
worker.py        # dijalankan di dalam tiap container
dispatcher.py    # penjadwalan + orkestrasi container + perhitungan metrik
summarize.py     # ringkasan mean ± std dari results.csv
results.csv      # satu baris per run
real_*.csv       # detail per task (task, vm, start, end)
```

## **Referensi**

- Braun, T.D., Siegel, H.J., Beck, N., Bölöni, L.L., Maheswaran, M., et al. (2001). A comparison of eleven static heuristics for mapping a class of independent tasks onto heterogeneous distributed computing systems. *Journal of Parallel and Distributed Computing*, 61(6), 810–837.
- Calheiros, R.N., Ranjan, R., Beloglazov, A., De Rose, C.A.F., Buyya, R. (2011). CloudSim: a toolkit for modeling and simulation of cloud computing environments and evaluation of resource provisioning algorithms. *Software: Practice and Experience*, 41(1), 23–50.
- Hussain, A., Aleem, M. (2018). GoCJ: Google Cloud Jobs Dataset for Distributed and Cloud Computing Infrastructures. *Data*, 3(4), 38. https://doi.org/10.3390/data3040038
- Silva Filho, M.C., Oliveira, R.L., Monteiro, C.C., Inácio, P.R.M., Freire, M.M. (2017). CloudSim Plus: a cloud computing simulation framework pursuing software engineering principles for improved modularity, extensibility and correctness. *IFIP/IEEE IM*, Lisbon.
