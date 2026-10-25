"""Jalan di dalam tiap VM (pod). Baca job dari stdin, eksekusi task berurutan,
cetak 1 baris JSON per task: {id, start, end} (epoch detik)."""
import sys, json, time

def burn(cpu_seconds):
    # habiskan CPU time. Kalau pod di-throttle (limit CPU kecil),
    # waktu dinding (wall) otomatis jadi lebih lama.
    t0 = time.process_time()
    while time.process_time() - t0 < cpu_seconds:
        pass

job = json.load(sys.stdin)            # {"start_at": epoch, "tasks": [[id, cpu_seconds], ...]}
time.sleep(max(0, job["start_at"] - time.time()))   # barrier: semua VM mulai bersamaan
for tid, cpu_s in job["tasks"]:
    s = time.time()
    burn(cpu_s)
    print(json.dumps({"id": tid, "start": s, "end": time.time()}), flush=True)
