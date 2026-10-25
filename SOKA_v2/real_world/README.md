# Real World Cloud: Kubernetes (kind) - MCT Task Scheduling
Taruh folder ini di: SOKA_v2/real_world/   (dataset dibaca dari ../Dataset-Sintetik/)

Datacenter = 1 cluster kind | Host = 2 worker node | VM = 4 pod (2 per host, CPU di-limit sesuai MIPS)

## Setup (sekali, PowerShell, Docker Desktop jalan, CPU >= 4 core)
winget install Kubernetes.kind
winget install Kubernetes.kubectl
docker pull python:3.12-slim
kind create cluster --name soka --config kind-config.yaml
kind load docker-image python:3.12-slim --name soka
kubectl create configmap worker-code --from-file=worker.py
kubectl apply -f vms.yaml
kubectl wait --for=condition=Ready pod -l app=vm --timeout=120s
kubectl get pods -o wide

## Jalankan
python dispatcher.py mct --n 50      # uji kecil
python dispatcher.py mct --n 1000    # ~4 menit (dataset sintetis seed1)
python dispatcher.py fcfs --n 1000   # pembanding
python dispatcher.py minmin --n 1000 # pembanding
python dispatcher.py mct --dry       # hanya hitung jadwal, tanpa cluster

## Bersihkan
kind delete cluster --name soka
