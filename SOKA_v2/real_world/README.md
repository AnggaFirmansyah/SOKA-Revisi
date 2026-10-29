# Real-World Test dengan Docker dan Kubernetes kind

Folder ini menjalankan scheduler MCT, FCFS, dan Min-Min pada empat logical VM berbasis pod yang tersebar di dua logical host Kubernetes.

## Arsitektur

```text
Datacenter : 1 cluster kind bernama soka
Host       : 2 worker node
VM         : 4 pod, dua pod per worker
Runtime    : Docker Desktop
Dataset    : ../Dataset-Sintetik/synthetic_seed1.csv
```

Mapping pod yang dipakai:

| Logical host | Pod | Kapasitas scheduler |
|---|---|---:|
| `soka-worker` | `vm-0` | 1.000 MIPS |
| `soka-worker` | `vm-3` | 7.500 MIPS |
| `soka-worker2` | `vm-1` | 2.500 MIPS |
| `soka-worker2` | `vm-2` | 5.000 MIPS |

### Hubungan dengan simulasi sebelumnya

Algoritma yang dijalankan di real-world sama dengan algoritma pada simulasi CloudSim sebelumnya (`SOKA_v2/BatchRunner.java`). MCT memilih VM dengan completion time terkecil dan, jika seri, memilih indeks VM terkecil. FCFS memilih VM yang paling cepat tersedia. Min-Min memilih pasangan task-VM dengan completion time terkecil di antara task yang tersisa.

Perbedaannya ada pada model resource. Pada simulasi, tiap VM memiliki beberapa PE (vCPU 2, 2, 2, dan 4) dan penjadwalan dilakukan per PE. Pada real-world, tiap VM dimodelkan sebagai satu antrean eksekusi, yaitu satu pod dengan satu CPU limit, dan kecepatannya sama dengan MIPS VM tersebut. Dengan pemetaan 7.500 MIPS = 1 core, model multi-PE akan membutuhkan sekitar 6,3 core CPU limit, lebih besar dari 4 CPU Docker yang dipakai pada pengujian ini, sedangkan model satu antrean per VM hanya membutuhkan sekitar 2,13 core. Karena modelnya berbeda, angka real-world tidak dibandingkan langsung dengan angka simulasi. Yang dibandingkan adalah perilaku relatif antar-algoritma.

## Ruang lingkup pengujian

Pengujian ini adalah **local real-world emulation**:

- Docker Desktop menjalankan cluster kind pada satu laptop fisik.
- `soka-worker` dan `soka-worker2` adalah dua logical host Kubernetes, bukan dua server fisik.
- `vm-0` sampai `vm-3` adalah logical VM berbasis pod/container, bukan VM hypervisor penuh.
- Kubernetes menerapkan CPU limit melalui cgroup.
- `worker.py` benar-benar memakai CPU dengan busy loop, lalu `dispatcher.py` mengukur waktu eksekusinya.
- Pengujian belum mengukur network antarperangkat fisik dan belum menghasilkan tagihan cloud aktual.

Deskripsi yang tepat untuk pengujian ini:

> Eksperimen dijalankan pada satu perangkat fisik menggunakan Docker Desktop dan Kubernetes kind. Dua worker node digunakan sebagai logical host, sedangkan empat pod digunakan sebagai logical VM. CPU limit diterapkan melalui cgroup dan task dieksekusi secara nyata pada CPU perangkat.

Jangan menyebut setup ini sebagai dua physical server, dua cloud instance, atau dua VM hypervisor penuh.

## Quick start jika cluster sudah siap

Kalau cluster `soka` dan pod VM sudah pernah dibuat, jalankan empat perintah ini:

```powershell
cd "D:\koleg-v2\Semester 5\SOKA\SOKA-Revisi\SOKA_v2\real_world"
kubectl config use-context kind-soka
kubectl get pods -l app=vm -o wide
python dispatcher.py mct --n 1000
```

Untuk mencetak hanya satu provider:

```powershell
python dispatcher.py mct --n 1000 --cost-provider aws
python dispatcher.py mct --n 1000 --cost-provider gcp
python dispatcher.py mct --n 1000 --cost-provider azure
```

Untuk uji cepat sebelum test utama, ganti perintah terakhir menjadi:

```powershell
python dispatcher.py mct --n 50
```

## Prasyarat

- Windows PowerShell
- Docker Desktop dalam keadaan berjalan
- Docker Desktop menyediakan minimal 4 CPU. Menurut catatan awal, run 26 Oktober dilakukan pada Docker dengan 12 CPU. Run 29 Oktober, yang menjadi hasil utama, dilakukan pada Docker dengan 4 CPU.
- Python 3
- `kubectl`
- `kind`

Cek instalasi:

```powershell
docker --version
kubectl version --client
python --version
```

Jika `kind` sudah masuk PATH:

```powershell
kind version
```

Pada beberapa terminal Windows, PATH dari WinGet belum terbaca. Gunakan path lengkap berikut jika perintah `kind` tidak ditemukan:

```powershell
$kind = "C:\Users\lenovo LOQ\AppData\Local\Microsoft\WinGet\Packages\Kubernetes.kind_Microsoft.Winget.Source_8wekyb3d8bbwe\kind.exe"
& $kind version
```

## Setup pertama kali

Jalankan dari folder ini:

```powershell
cd "D:\koleg-v2\Semester 5\SOKA\SOKA-Revisi\SOKA_v2\real_world"
```

Install tool jika belum tersedia:

```powershell
winget install Kubernetes.kind --accept-package-agreements --accept-source-agreements
winget install Kubernetes.kubectl --accept-package-agreements --accept-source-agreements
```

Set path executable `kind` jika terminal belum mengenal perintah `kind`:

```powershell
$kind = "C:\Users\lenovo LOQ\AppData\Local\Microsoft\WinGet\Packages\Kubernetes.kind_Microsoft.Winget.Source_8wekyb3d8bbwe\kind.exe"
```

Download image worker:

```powershell
docker pull python:3.12-slim
```

Buat cluster kind:

```powershell
& $kind create cluster --name soka --config kind-config.yaml
```

Load image ke semua node cluster:

```powershell
& $kind load docker-image python:3.12-slim --name soka
```

Buat atau perbarui ConfigMap worker:

```powershell
kubectl create configmap worker-code --from-file=worker.py --dry-run=client -o yaml | kubectl apply -f -
```

Deploy empat pod VM:

```powershell
kubectl apply -f vms.yaml
kubectl wait --for=condition=Ready pod -l app=vm --timeout=120s
```

Cek pod:

```powershell
kubectl get pods -l app=vm -o wide
```

Semua pod harus berstatus `Running` dan `1/1` pada kolom `READY`.

## Menjalankan test setelah cluster sudah ada

Jika cluster `soka` sudah pernah dibuat, tidak perlu membuat cluster lagi. Jalankan:

```powershell
cd "D:\koleg-v2\Semester 5\SOKA\SOKA-Revisi\SOKA_v2\real_world"
kubectl config use-context kind-soka
kubectl get pods -l app=vm -o wide
```

Jika pod masih ada dan statusnya `Running`, langsung jalankan scheduler.

### Smoke test MCT

Gunakan 50 task untuk memastikan koneksi ke semua pod bekerja:

```powershell
python dispatcher.py mct --n 50
```

### Test utama MCT

Gunakan 1.000 task untuk hasil utama dan demo:

```powershell
python dispatcher.py mct --n 1000
```

Pada komputer pengujian, test 1.000 task membutuhkan beberapa menit.

### Test pembanding

FCFS:

```powershell
python dispatcher.py fcfs --n 1000
```

Min-Min:

```powershell
python dispatcher.py minmin --n 1000
```

Mode tanpa Kubernetes, hanya menghitung pembagian task:

```powershell
python dispatcher.py mct --n 1000 --dry
```

Parameter `--speedup` dapat dipakai untuk mengubah faktor percepatan waktu worker. Nilai default adalah 50:

```powershell
python dispatcher.py mct --n 1000 --speedup 100
```

## Memeriksa topologi

Pastikan dua pod berjalan pada setiap worker node:

```powershell
kubectl get nodes -L host -o wide
kubectl get pods -l app=vm -o wide
```

Topologi yang benar:

```text
soka-worker  : vm-0, vm-3
soka-worker2 : vm-1, vm-2
```

## Bukti konfigurasi untuk demo

Jalankan perintah berikut dari folder `real_world` untuk merekam bukti konfigurasi:

```powershell
kubectl get nodes -L host -o wide
kubectl get pods -l app=vm -o wide
kubectl get pod vm-0 -o jsonpath="vm-0 cpu={.spec.containers[0].resources.limits.cpu}\n"
kubectl get pod vm-1 -o jsonpath="vm-1 cpu={.spec.containers[0].resources.limits.cpu}\n"
kubectl get pod vm-2 -o jsonpath="vm-2 cpu={.spec.containers[0].resources.limits.cpu}\n"
kubectl get pod vm-3 -o jsonpath="vm-3 cpu={.spec.containers[0].resources.limits.cpu}\n"
```

Jika menggunakan Git Bash, cek cgroup dengan path conversion yang dimatikan:

```bash
for p in vm-0 vm-1 vm-2 vm-3; do printf "%s: " "$p"; MSYS_NO_PATHCONV=1 kubectl exec "$p" -- cat /sys/fs/cgroup/cpu.max; done
```

Bukti eksekusi scheduler:

```powershell
python dispatcher.py mct --n 1000
```

Simpan output terminal dan file `real_mct_<timestamp>.csv` sebagai bukti bahwa task benar-benar dikirim ke pod dan selesai dieksekusi.

## Batas klaim

Hasil ini boleh dipakai untuk menunjukkan dampak CPU limit, pembagian task, dan overhead eksekusi container. Hasil ini belum boleh dipakai untuk menyimpulkan:

- performa dua server fisik yang terpisah;
- latency jaringan antar-host fisik;
- harga tagihan Azure, GCP, atau AWS;
- overhead VM hypervisor Proxmox;
- ketahanan terhadap kegagalan host fisik.

## Output dan metrik

Setiap test mencetak:

- distribusi task per VM
- makespan ideal
- makespan real
- persentase selisih real terhadap ideal
- Degree of Imbalance (DI)
- utilization
- throughput

Contoh hasil MCT 1.000 task dari pengujian lokal:

```text
[MCT] 1000 task | distribusi per VM: [209, 277, 272, 242] | makespan ideal: 11939.99 s
Makespan real : 13049.32 s   (ideal 11939.99, selisih +9.29%)
DI            : 0.0610
Utilization   : 0.9759
Throughput    : 0.0766 task/s
```

File detail per task dibuat otomatis di folder `real_world`:

```text
real_mct_<timestamp>.csv
real_fcfs_<timestamp>.csv
real_minmin_<timestamp>.csv
```

Setiap CSV berisi kolom:

```text
task,vm,start,end
```

## Estimasi biaya cloud

Setiap test real-world juga mencetak estimasi biaya untuk tiga provider:

```text
Estimated cloud cost (USD, CPU-equivalent, simulated duration):
  AWS EC2 t3a.small (us-east-1): $... total [...]
  GCP Compute Engine e2-standard-2 (us-central1): $... total [...]
  Azure VM Standard_DS1_v2 (eastus): $... total [...]
```

Jalankan seperti biasa:

```powershell
python dispatcher.py mct --n 1000
```

Model biaya yang dipakai:

```text
biaya VM = durasi simulasi (jam) × porsi vCPU logical VM × harga referensi per vCPU-jam
total    = biaya vm-0 + biaya vm-1 + biaya vm-2 + biaya vm-3
```

Durasi biaya memakai durasi simulasi yang sudah dikalibrasi, bukan waktu dinding test lokal. Karena itu `--speedup` hanya mempercepat pengujian Docker dan tidak mengubah estimasi biaya workload cloud.

Harga baseline yang dicatat pada **26 Oktober 2026**:

| Provider | SKU referensi | Region | Harga instance | vCPU |
|---|---|---|---:|---:|
| AWS | EC2 `t3a.small` | `us-east-1` | USD 0,0188/jam | 2 |
| GCP | Compute Engine `e2-standard-2` | `us-central1` | USD 0,06701142/jam | 2 |
| Azure | VM `Standard_DS1_v2` | `eastus` | USD 0,073/jam | 1 |

Contoh estimasi dari hasil MCT 1.000 task yang sudah dijalankan:

| Provider | Estimasi total |
|---|---:|
| AWS | USD 0,065841 |
| GCP | USD 0,234686 |
| Azure | USD 0,511319 |

Sumber harga resmi:

- AWS EC2 On-Demand Pricing: <https://aws.amazon.com/ec2/pricing/on-demand/>
- Google Cloud Compute Engine Pricing: <https://cloud.google.com/compute/vm-instance-pricing>
- Azure Retail Prices API untuk Virtual Machines: <https://prices.azure.com/api/retail/prices>

Estimasi ini bukan tagihan aktual. Pengujian berjalan lokal tanpa billing cloud, dan harga provider dapat berubah berdasarkan region, OS, disk, network, pajak, serta model pembayaran. Nilai `MIPS` pada eksperimen dinormalisasi terhadap 7.500 MIPS sebagai satu vCPU logical, sehingga hasil ini disebut **CPU-equivalent cost estimate**.

## Grafik real-world

Install library grafik sekali:

```powershell
python -m pip install pandas matplotlib
```

Setelah beberapa test selesai, buat ringkasan dan grafik:

```powershell
python plot_real_results.py
```

Script membaca semua `real_<algoritma>_<timestamp>.csv` di folder ini. Outputnya:

| File | Informasi |
|---|---|
| `real_summary.csv` | Ringkasan metrik dan cost per run |
| `real_makespan.png` | Makespan real dibandingkan ideal |
| `real_efficiency.png` | Utilization, DI, dan throughput |
| `real_overhead.png` | Overhead makespan real terhadap ideal |
| `real_cost.png` | Perbandingan estimasi biaya AWS, GCP, dan Azure |
| `real_task_distribution.png` | Distribusi task pada vm-0 sampai vm-3 |
| `real_vm_usage.png` | Waktu CPU busy setiap logical VM |

Judul dan catatan grafik menyebut Docker Desktop, Kubernetes kind, dua logical host, dan empat logical VM supaya hasil tidak disalahartikan sebagai dua server fisik atau cloud instance sungguhan. Grafik memakai data yang benar-benar tercatat dari eksekusi pod lokal.

## Interpretasi hasil real-world

Interpretasi ini memakai **run 29 Oktober 2026** pada satu mesin dan kondisi yang sama. Enam run di folder ini (`real_*_20261029_*.csv`) menjadi dasar `real_summary.csv` dan semua grafik: MCT, FCFS, dan Min-Min pada 50 task serta 1.000 task. Tiga ulangan tambahan untuk n=1.000 per algoritma disimpan di `arsip/ulangan/` untuk mengukur stabilitas hasil. Semua run berjalan pada Docker Desktop dan Kubernetes kind dengan dua logical host dan empat logical VM.

### Ringkasan hasil utama

| Jumlah task | Algoritma | Makespan real (s) | Makespan ideal (s) | Overhead terhadap ideal | DI | Utilization | Throughput (task/s) | AWS cost (USD) |
|---:|---|---:|---:|---:|---:|---:|---:|---:|
| 50 | MCT | 567,02 | 569,08 | -0,36% | 0,0858 | 0,9695 | 0,0882 | 0,003116 |
| 50 | FCFS | 590,71 | 603,91 | -2,19% | 0,0918 | 0,9463 | 0,0846 | 0,003113 |
| 50 | Min-Min | 649,83 | 648,15 | 0,26% | 1,3241 | 0,6631 | 0,0769 | 0,003123 |
| 1.000 | MCT | 11.967,58 | 11.939,99 | 0,23% | 0,0652 | 0,9731 | 0,0836 | 0,065841 |
| 1.000 | FCFS | 11.899,78 | 12.039,02 | -1,16% | 0,0449 | 0,9844 | 0,0840 | 0,065828 |
| 1.000 | Min-Min | 11.996,02 | 12.051,62 | -0,46% | 0,1134 | 0,9614 | 0,0834 | 0,065838 |

### Stabilitas hasil (n=1.000, 4 run per algoritma)

| Algoritma | Jumlah run | Rata-rata makespan real (s) | Rentang (s) | Makespan ideal (s) | Rata-rata overhead | Rata-rata DI | Rata-rata utilization |
|---|---:|---:|---|---:|---:|---:|---:|
| MCT | 4 | 11.967,47 | 11.963,75 – 11.972,65 | 11.939,99 | 0,23% | 0,0654 | 0,9731 |
| FCFS | 4 | 11.900,35 | 11.889,13 – 11.908,68 | 12.039,02 | -1,15% | 0,0447 | 0,9844 |
| Min-Min | 4 | 11.996,96 | 11.993,75 – 12.002,84 | 12.051,62 | -0,45% | 0,1132 | 0,9617 |

Variasi antar-run sangat kecil, yaitu 0,07% sampai 0,16% dari makespan. Urutan makespan real sama pada keempat run: FCFS, lalu MCT, lalu Min-Min. Distribusi task per VM identik pada setiap run karena penjadwalan bersifat deterministik.

### Perbandingan algoritma

1. **Pada n=1.000, ketiga algoritma hampir setara dalam makespan real.** Selisih FCFS dengan MCT sekitar 0,56% dan selisih MCT dengan Min-Min sekitar 0,25%. Selisih sekecil ini tidak cukup untuk menyatakan satu algoritma jauh lebih baik.

2. **Urutan makespan ideal berbeda dari makespan real.** Pada jadwal ideal, MCT paling rendah (11.939,99 s), disusul FCFS (12.039,02 s) dan Min-Min (12.051,62 s). Pada eksekusi nyata, FCFS justru paling rendah. Penyebabnya adalah pembulatan kuota CPU oleh Kubernetes. Limit 133m, 333m, 667m, dan 1000m tercatat di `cpu.max` sebagai 14000, 34000, 67000, dan 100000 mikrodetik per periode 100000 mikrodetik, sehingga limit efektif menjadi 140m, 340m, 670m, dan 1000m. VM yang lebih kecil mendapat CPU sedikit di atas nominal. Pada FCFS, VM penentu makespan ideal adalah vm-1, yang memperoleh tambahan ini. Pada MCT, VM penentu adalah vm-3, yang tidak memperoleh tambahan. Jika makespan ideal dihitung ulang dengan kecepatan efektif tersebut, prediksinya adalah MCT 11.940,0 s, FCFS 11.871,2 s, dan Min-Min 11.991,7 s, dan selisihnya dengan rata-rata terukur hanya 0,04% sampai 0,25%. Jadi keunggulan FCFS pada makespan real merupakan efek konfigurasi quota CPU, bukan bukti bahwa FCFS lebih baik secara algoritmik.

3. **MCT paling baik pada workload kecil (n=50).** MCT menghasilkan makespan 567,02 s, FCFS 590,71 s, dan Min-Min 649,83 s. Urutan ini sama dengan urutan makespan ideal (569,08 s, 603,91 s, dan 648,15 s). Pada jumlah task kecil, keputusan penempatan tiap task berpengaruh lebih besar terhadap hasil akhir.

4. **FCFS paling merata, Min-Min paling timpang.** FCFS memiliki DI terendah (0,0447) dan utilization tertinggi (0,9844). Min-Min memiliki DI tertinggi (0,1132) karena menumpuk task pada VM tercepat, dengan distribusi `[60, 155, 314, 471]` pada n=1.000 dan `[1, 7, 16, 26]` pada n=50. Pada n=50, DI Min-Min mencapai 1,3241 dan utilization turun menjadi 0,6631.

5. **Cost tidak membedakan algoritma.** Estimasi cost dihitung dari total CPU-equivalent dan durasi simulasi, sedangkan total workload sama untuk ketiga algoritma. Pada n=1.000, selisih estimasi AWS antar-algoritma di bawah 0,0001 USD, sehingga cost tidak dipakai sebagai dasar memilih algoritma.

6. **n=50 dipakai sebagai smoke test.** Satu run n=50 hanya berlangsung sekitar 11 detik waktu nyata, sehingga hasilnya lebih sensitif terhadap kondisi mesin. Kesimpulan utama diambil dari n=1.000, dan n=50 dipakai untuk memperlihatkan perilaku algoritma pada workload kecil.

### Perbandingan dengan run awal 26 Oktober 2026

Run awal pada 26 Oktober 2026 disimpan di `arsip/26okt/` dan **tidak dipakai** untuk kesimpulan. Pada run itu, makespan real untuk n=1.000 sekitar 9% sampai 10% lebih lambat daripada ideal, dan waktu busy keempat VM naik seragam sekitar 9% dibanding prediksi. Pola seragam seperti ini khas persaingan CPU di level host. Jumlah CPU yang dialokasikan ke Docker bukan penyebabnya, karena run 26 Oktober memakai 12 CPU sedangkan run 29 Oktober cukup dengan 4 CPU. Dugaan yang tersisa adalah beban aplikasi lain atau mode daya laptop saat run, dan dugaan ini belum diverifikasi. Pada run 26 Oktober n=50 juga tidak konsisten antar algoritma (overhead MCT 26,41%, FCFS 25,73%, Min-Min -0,13%). Kodenya sama, sehingga perbedaan hasil menunjukkan bahwa pengukuran real-world sensitif terhadap kondisi host. Karena itu hasil 29 Oktober, yang diulang beberapa kali dan stabil, dipakai sebagai dasar.

### Kesimpulan

Pada local real-world emulation ini, ketiga algoritma menghasilkan makespan yang hampir setara pada 1.000 task (selisih di bawah 0,6%). **MCT** unggul pada jadwal ideal dan pada workload kecil. **FCFS** paling merata antar-VM (DI terendah, utilization tertinggi). **Min-Min** paling timpang karena memusatkan task pada VM tercepat. Perbedaan antara urutan ideal dan urutan real dijelaskan oleh pembulatan kuota CPU Kubernetes.

Kesimpulan tersebut berlaku untuk konfigurasi lokal yang diuji: satu device fisik, Docker Desktop, dua logical host kind, empat logical VM berbasis pod, CPU limit Kubernetes, faktor percepatan waktu 50x (`--speedup 50`), dan dataset `synthetic_seed1.csv`. Kesimpulan ini tidak menyatakan bahwa hasilnya identik dengan dua server fisik, Proxmox, atau cloud instance terpisah.

Run yang dipakai:

- `real_mct_20261029_020024.csv`, MCT 50 task
- `real_fcfs_20261029_022116.csv`, FCFS 50 task
- `real_minmin_20261029_022734.csv`, Min-Min 50 task
- `real_mct_20261029_023717.csv`, MCT 1.000 task
- `real_fcfs_20261029_024122.csv`, FCFS 1.000 task
- `real_minmin_20261029_024530.csv`, Min-Min 1.000 task

Ulangan n=1.000 (tiga per algoritma, plus satu run MCT n=50 tambahan) ada di `arsip/ulangan/`. Run awal 26 Oktober ada di `arsip/26okt/`.

## Troubleshooting

### `kind` tidak dikenali

Gunakan executable dengan path lengkap:

```powershell
$kind = "C:\Users\lenovo LOQ\AppData\Local\Microsoft\WinGet\Packages\Kubernetes.kind_Microsoft.Winget.Source_8wekyb3d8bbwe\kind.exe"
& $kind get clusters
```

Atau tutup PowerShell, buka terminal baru, lalu coba `kind version` lagi.

### Pod belum `Running`

```powershell
kubectl get pods -o wide
kubectl describe pod vm-0
kubectl describe pod vm-1
kubectl describe pod vm-2
kubectl describe pod vm-3
```

Pastikan image sudah di-load ke cluster:

```powershell
& $kind load docker-image python:3.12-slim --name soka
```

Jika pod tidak ada, jalankan ulang:

```powershell
kubectl apply -f vms.yaml
kubectl wait --for=condition=Ready pod -l app=vm --timeout=120s
```

### ConfigMap worker belum ada

```powershell
kubectl create configmap worker-code --from-file=worker.py --dry-run=client -o yaml | kubectl apply -f -
kubectl delete pod -l app=vm
kubectl apply -f vms.yaml
kubectl wait --for=condition=Ready pod -l app=vm --timeout=120s
```

### `kubectl exec` gagal

Cek status pod dan konteks Kubernetes:

```powershell
kubectl config current-context
kubectl get pods -l app=vm -o wide
```

Konteks yang benar adalah:

```text
kind-soka
```

### Cek file cgroup CPU dari Git Bash

Git Bash dapat mengubah path Linux menjadi path Windows. Gunakan `MSYS_NO_PATHCONV=1`:

```bash
for p in vm-0 vm-1 vm-2 vm-3; do printf "%s: " "$p"; MSYS_NO_PATHCONV=1 kubectl exec "$p" -- cat /sys/fs/cgroup/cpu.max; done
```

## Menghapus cluster

Jangan menghapus cluster sebelum demo selesai. Jika semua test sudah selesai dan cluster tidak diperlukan lagi:

```powershell
& $kind delete cluster --name soka
```

Perintah ini menghapus cluster kind, tetapi tidak menghapus source code, dataset, atau file CSV hasil.
