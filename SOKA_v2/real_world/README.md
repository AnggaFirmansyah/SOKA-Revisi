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

## Ruang lingkup pengujian

Pengujian ini adalah **local real-world emulation**:

- Docker Desktop menjalankan cluster kind pada satu laptop fisik.
- `soka-worker` dan `soka-worker2` adalah dua logical host Kubernetes, bukan dua server fisik.
- `vm-0` sampai `vm-3` adalah logical VM berbasis pod/container, bukan VM hypervisor penuh.
- Kubernetes menerapkan CPU limit melalui cgroup.
- `worker.py` benar-benar memakai CPU dengan busy loop, lalu `dispatcher.py` mengukur waktu eksekusinya.
- Pengujian belum mengukur network antarperangkat fisik dan belum menghasilkan tagihan cloud aktual.

Kalimat yang aman untuk laporan:

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
- Docker Desktop menyediakan minimal 4 CPU. Pengujian dilakukan pada Docker dengan 12 CPU.
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

Gunakan 1.000 task untuk hasil yang dipakai dalam laporan atau demo:

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

## Bukti untuk demo atau laporan

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
| AWS | USD 0,071911 |
| GCP | USD 0,256323 |
| Azure | USD 0,558458 |

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

Interpretasi berikut memakai enam run pada `real_summary.csv`: MCT, FCFS, dan Min-Min pada 50 task serta 1.000 task. Semua run berjalan pada Docker Desktop dan Kubernetes kind dengan dua logical host dan empat logical VM.

### Ringkasan hasil utama

| Jumlah task | Algoritma | Makespan real (s) | Overhead terhadap ideal | DI | Utilization | Throughput (task/s) | AWS cost (USD) |
|---:|---|---:|---:|---:|---:|---:|---:|
| 50 | MCT | 719,40 | 26,41% | 0,0819 | 0,7625 | 0,0695 | 0,003974 |
| 50 | FCFS | 759,32 | 25,73% | 0,0709 | 0,9582 | 0,0658 | 0,004052 |
| 50 | Min-Min | 647,33 | -0,13% | 1,3181 | 0,6648 | 0,0772 | 0,003115 |
| 1.000 | MCT | **13.049,32** | 9,29% | 0,0610 | 0,9759 | **0,0766** | **0,071911** |
| 1.000 | FCFS | 13.083,01 | **8,67%** | **0,0389** | **0,9819** | 0,0764 | 0,072494 |
| 1.000 | Min-Min | 13.323,99 | 10,56% | 0,1143 | 0,9622 | 0,0751 | 0,073145 |

### Perbandingan algoritma

1. **MCT paling baik untuk workload utama 1.000 task.** MCT menghasilkan makespan terendah, yaitu 13.049,32 detik. Nilai ini 33,69 detik lebih cepat daripada FCFS dan 274,67 detik lebih cepat daripada Min-Min. MCT juga menghasilkan throughput tertinggi dan estimasi biaya AWS terendah pada n=1.000.

2. **FCFS paling baik dalam pemakaian resource pada n=1.000.** FCFS memiliki utilization tertinggi, yaitu 0,9819, dan DI terendah, yaitu 0,0389. Artinya, pada run ini total waktu busy antar-VM paling merata dan resource paling sedikit menganggur. FCFS memiliki overhead terhadap jadwal ideal paling rendah, yaitu 8,67%.

3. **Min-Min unggul pada smoke test 50 task, tetapi tidak konsisten untuk workload besar.** Pada n=50, Min-Min menghasilkan makespan 647,33 detik dan throughput 0,0772 task/s. Namun, distribusi task-nya sangat terkonsentrasi pada VM tercepat, yaitu `[1, 7, 16, 26]`, sehingga DI mencapai 1,3181 dan utilization hanya 0,6648. Pada n=1.000, pola ini membuat Min-Min menjadi yang paling lambat, paling rendah throughput-nya, dan memiliki DI tertinggi di antara tiga algoritma.

4. **MCT tidak selalu memiliki overhead persentase paling rendah.** Pada n=1.000, overhead MCT adalah 9,29%, sedikit di atas FCFS 8,67%. Hal ini tidak membatalkan hasil makespan MCT. Overhead dihitung terhadap ideal masing-masing algoritma, sedangkan ideal MCT memang lebih rendah. Karena itu, makespan real dan overhead perlu dibaca bersama, bukan memakai overhead saja untuk menentukan algoritma terbaik.

5. **Cost mengikuti durasi workload.** Pada n=1.000, urutan estimasi biaya adalah MCT, FCFS, lalu Min-Min. Perbedaannya kecil karena ketiga algoritma menjalankan total workload yang sama dan memakai empat logical VM yang sama. Cost bukan pengukuran tagihan Docker lokal, melainkan CPU-equivalent estimate berdasarkan durasi simulasi dan harga referensi provider.

6. **n=50 dipengaruhi overhead eksekusi lokal.** Pada workload kecil, waktu persiapan `kubectl exec`, barrier start, CPU throttling, dan variasi scheduler laptop mengambil porsi besar dari total waktu. Karena itu, hasil n=1.000 lebih tepat dipakai untuk kesimpulan utama. Hasil n=50 tetap berguna untuk menunjukkan perilaku algoritma pada workload kecil.

### Kesimpulan untuk laporan

Pada local real-world emulation ini, **MCT menjadi pilihan utama untuk workload 1.000 task** karena menghasilkan makespan dan cost terendah serta throughput tertinggi. **FCFS menjadi pilihan terbaik jika prioritasnya adalah utilization dan pemerataan waktu busy antar-VM.** **Min-Min dapat unggul pada workload kecil tertentu, tetapi cenderung memusatkan task pada VM tercepat sehingga DI meningkat dan utilization turun.**

Kesimpulan tersebut berlaku untuk konfigurasi lokal yang diuji: satu device fisik, Docker Desktop, dua logical host kind, empat logical VM berbasis pod, CPU limit Kubernetes, dan dataset `synthetic_seed1.csv`. Kesimpulan ini tidak menyatakan bahwa hasilnya identik dengan dua server fisik, Proxmox, atau cloud instance terpisah.

Hasil yang sudah pernah dibuat:

- `real_mct_20261026_114418.csv`, smoke test 50 task
- `real_fcfs_20261026_144337.csv`, FCFS 50 task
- `real_minmin_20261026_144358.csv`, Min-Min 50 task
- `real_mct_20261026_114911.csv`, MCT 1.000 task
- `real_fcfs_20261026_115401.csv`, FCFS 1.000 task
- `real_minmin_20261026_115849.csv`, Min-Min 1.000 task

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
