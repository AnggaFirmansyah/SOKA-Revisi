# Real-World Test dengan Docker dan Kubernetes kind

Folder ini menjalankan scheduler MCT, FCFS, dan Min-Min pada empat pod VM yang tersebar di dua worker node Kubernetes.

## Arsitektur

```text
Datacenter : 1 cluster kind bernama soka
Host       : 2 worker node
VM         : 4 pod, dua pod per worker
Runtime    : Docker Desktop
Dataset    : ../Dataset-Sintetik/synthetic_seed1.csv
```

Mapping pod yang dipakai:

| Host | Pod | Kapasitas scheduler |
|---|---|---:|
| `soka-worker` | `vm-0` | 1.000 MIPS |
| `soka-worker` | `vm-3` | 7.500 MIPS |
| `soka-worker2` | `vm-1` | 2.500 MIPS |
| `soka-worker2` | `vm-2` | 5.000 MIPS |

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

Hasil yang sudah pernah dibuat:

- `real_mct_20261026_114418.csv`, smoke test 50 task
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

Jika tidak ada deployment, jalankan ulang:

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
