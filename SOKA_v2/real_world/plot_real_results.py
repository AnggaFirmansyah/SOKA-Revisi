"""Build charts from real-world Docker/kind scheduler result CSV files."""

import argparse
import csv
import re
from pathlib import Path

from dispatcher import ALGO, COST_PROVIDERS, estimate_cost, ideal_metrics, load_tasks

VM_COUNT = 4
RUN_RE = re.compile(r"real_(mct|fcfs|minmin)_\d{8}_\d{6}\.csv$")
CONTEXT = "Docker Desktop + Kubernetes kind | 2 logical hosts | 4 logical VMs"


def summarize_rows(algorithm, n, rows, ideal):
    runtime = [0.0] * VM_COUNT
    busy = [0.0] * VM_COUNT
    distribution = [0] * VM_COUNT

    for row in rows:
        vm = int(row["vm"])
        start = float(row["start"])
        end = float(row["end"])
        runtime[vm] = max(runtime[vm], end)
        busy[vm] += end - start
        distribution[vm] += 1

    makespan = max(runtime, default=0.0)
    mean_busy = sum(busy) / VM_COUNT
    di = (max(busy) - min(busy)) / mean_busy if mean_busy else 0.0
    utilization = sum(busy) / (VM_COUNT * makespan) if makespan else 0.0
    costs = {provider: estimate_cost(provider, runtime)["total"] for provider in COST_PROVIDERS}

    return {
        "algorithm": algorithm,
        "n": n,
        "makespan": makespan,
        "ideal_makespan": ideal,
        "overhead_pct": (makespan - ideal) / ideal * 100 if ideal else 0.0,
        "di": di,
        "utilization": utilization,
        "throughput": n / makespan if makespan else 0.0,
        "distribution": distribution,
        "vm_runtime": runtime,
        "vm_busy": busy,
        "aws_cost": costs["aws"],
        "gcp_cost": costs["gcp"],
        "azure_cost": costs["azure"],
    }


def read_run(path, dataset_path):
    match = RUN_RE.match(path.name)
    if not match:
        return None
    algorithm = match.group(1)
    with path.open(newline="", encoding="utf-8") as file:
        rows = list(csv.DictReader(file))
    n = len(rows)
    tasks = load_tasks(dataset_path, n)
    ideal, _ = ideal_metrics(tasks, ALGO[algorithm](tasks))
    return summarize_rows(algorithm, n, rows, ideal)


def flatten_summary(summary):
    row = {key: value for key, value in summary.items() if key not in {"distribution", "vm_runtime", "vm_busy"}}
    row.update({f"vm_{j}_tasks": value for j, value in enumerate(summary["distribution"])})
    row.update({f"vm_{j}_runtime": value for j, value in enumerate(summary["vm_runtime"])})
    row.update({f"vm_{j}_busy": value for j, value in enumerate(summary["vm_busy"])})
    return row


def load_summary(input_dir, dataset_path):
    summaries = [read_run(path, dataset_path) for path in sorted(input_dir.glob("real_*.csv"))]
    return [summary for summary in summaries if summary is not None]


def write_summary(summaries, output_path):
    rows = [flatten_summary(summary) for summary in summaries]
    if not rows:
        raise ValueError("Tidak ada file real_<algo>_<timestamp>.csv yang bisa diproses")
    fieldnames = list(rows[0])
    with output_path.open("w", newline="", encoding="utf-8") as file:
        writer = csv.DictWriter(file, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def add_context(fig, title):
    fig.suptitle(title, fontsize=14, fontweight="bold")
    fig.text(0.5, 0.01, CONTEXT, ha="center", fontsize=9, color="#555555")


def plot_all(summaries, output_dir):
    import matplotlib

    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    import pandas as pd

    df = pd.DataFrame([flatten_summary(summary) for summary in summaries]).sort_values(["n", "algorithm"])
    colors = {"mct": "#1f77b4", "fcfs": "#ff7f0e", "minmin": "#2ca02c"}
    labels = {"mct": "MCT", "fcfs": "FCFS", "minmin": "Min-Min"}

    fig, ax = plt.subplots(figsize=(9, 5.5))
    for algorithm, group in df.groupby("algorithm"):
        group = group.sort_values("n")
        ax.plot(group["n"], group["makespan"], marker="o", label=labels[algorithm], color=colors[algorithm])
        ax.plot(group["n"], group["ideal_makespan"], linestyle="--", alpha=0.45, color=colors[algorithm])
    ax.set(title="Makespan real vs ideal", xlabel="Jumlah task", ylabel="Waktu (detik)")
    ax.grid(alpha=0.25)
    ax.legend()
    add_context(fig, "Real-world performance")
    fig.tight_layout(rect=(0, 0.04, 1, 0.94))
    fig.savefig(output_dir / "real_makespan.png", dpi=160)
    plt.close(fig)

    fig, axes = plt.subplots(1, 3, figsize=(14, 4.8))
    for algorithm, group in df.groupby("algorithm"):
        group = group.sort_values("n")
        axes[0].plot(group["n"], group["utilization"], marker="o", label=labels[algorithm], color=colors[algorithm])
        axes[1].plot(group["n"], group["di"], marker="o", label=labels[algorithm], color=colors[algorithm])
        axes[2].plot(group["n"], group["throughput"], marker="o", label=labels[algorithm], color=colors[algorithm])
    for ax, title, ylabel in zip(
        axes,
        ("Utilization", "Degree of Imbalance", "Throughput"),
        ("rasio", "DI", "task/detik"),
    ):
        ax.set(title=title, xlabel="Jumlah task", ylabel=ylabel)
        ax.grid(alpha=0.25)
    axes[0].legend()
    add_context(fig, "Real-world resource usage")
    fig.tight_layout(rect=(0, 0.04, 1, 0.92))
    fig.savefig(output_dir / "real_efficiency.png", dpi=160)
    plt.close(fig)

    fig, ax = plt.subplots(figsize=(9, 5.5))
    for algorithm, group in df.groupby("algorithm"):
        group = group.sort_values("n")
        ax.plot(group["n"], group["overhead_pct"], marker="o", label=labels[algorithm], color=colors[algorithm])
    ax.axhline(0, color="black", linewidth=0.8)
    ax.set(title="Overhead makespan real terhadap ideal", xlabel="Jumlah task", ylabel="Selisih (%)")
    ax.grid(alpha=0.25)
    ax.legend()
    add_context(fig, "Real-world overhead")
    fig.tight_layout(rect=(0, 0.04, 1, 0.94))
    fig.savefig(output_dir / "real_overhead.png", dpi=160)
    plt.close(fig)

    run_labels = [f"{labels[row.algorithm]}\nn={row.n}" for row in df.itertuples()]
    x = range(len(df))
    width = 0.25
    fig, ax = plt.subplots(figsize=(11, 5.5))
    for offset, (column, label, color) in enumerate(
        (("aws_cost", "AWS", "#ff9900"), ("gcp_cost", "GCP", "#4285f4"), ("azure_cost", "Azure", "#0078d4"))
    ):
        ax.bar([value + (offset - 1) * width for value in x], df[column], width, label=label, color=color)
    ax.set(title="Estimasi biaya cloud CPU-equivalent", ylabel="USD", xticks=list(x), xticklabels=run_labels)
    ax.grid(axis="y", alpha=0.25)
    ax.legend()
    add_context(fig, "Cloud cost reference")
    fig.tight_layout(rect=(0, 0.04, 1, 0.94))
    fig.savefig(output_dir / "real_cost.png", dpi=160)
    plt.close(fig)

    fig, ax = plt.subplots(figsize=(11, 5.5))
    bottom = [0] * len(df)
    vm_colors = ["#4c78a8", "#f58518", "#54a24b", "#e45756"]
    for j, color in enumerate(vm_colors):
        values = df[f"vm_{j}_tasks"]
        ax.bar(run_labels, values, bottom=bottom, label=f"vm-{j}", color=color)
        bottom = [old + new for old, new in zip(bottom, values)]
    ax.set(title="Distribusi task per logical VM", ylabel="Jumlah task")
    ax.legend(ncol=4)
    ax.grid(axis="y", alpha=0.25)
    add_context(fig, "Local real-world emulation")
    fig.tight_layout(rect=(0, 0.04, 1, 0.94))
    fig.savefig(output_dir / "real_task_distribution.png", dpi=160)
    plt.close(fig)

    fig, ax = plt.subplots(figsize=(11, 5.5))
    bottom = [0] * len(df)
    for j, color in enumerate(vm_colors):
        values = df[f"vm_{j}_busy"]
        ax.bar(run_labels, values, bottom=bottom, label=f"vm-{j}", color=color)
        bottom = [old + new for old, new in zip(bottom, values)]
    ax.set(title="Waktu CPU busy per logical VM", ylabel="CPU seconds terukur")
    ax.legend(ncol=4)
    ax.grid(axis="y", alpha=0.25)
    add_context(fig, "Local Docker CPU usage")
    fig.tight_layout(rect=(0, 0.04, 1, 0.94))
    fig.savefig(output_dir / "real_vm_usage.png", dpi=160)
    plt.close(fig)


def main():
    parser = argparse.ArgumentParser(description="Buat ringkasan dan grafik dari CSV real-world Docker/kind")
    parser.add_argument("--input-dir", default=".")
    parser.add_argument("--dataset", default="../Dataset-Sintetik/synthetic_seed1.csv")
    parser.add_argument("--output-dir", default=".")
    args = parser.parse_args()

    input_dir = Path(args.input_dir)
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    summaries = load_summary(input_dir, Path(args.dataset))
    write_summary(summaries, output_dir / "real_summary.csv")
    plot_all(summaries, output_dir)
    print(f"Processed {len(summaries)} result file(s)")
    print("Wrote real_summary.csv and 6 PNG charts")


if __name__ == "__main__":
    main()
