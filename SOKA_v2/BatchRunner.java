import org.cloudbus.cloudsim.allocationpolicies.VmAllocationPolicyFirstFit;
import org.cloudbus.cloudsim.brokers.DatacenterBroker;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.datacenters.DatacenterSimple;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.hosts.HostSimple;
import org.cloudbus.cloudsim.resources.Pe;
import org.cloudbus.cloudsim.resources.PeSimple;
import org.cloudbus.cloudsim.schedulers.cloudlet.CloudletSchedulerSpaceShared;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelDynamic;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.vms.VmSimple;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * BatchRunner — uji coba skala dataset untuk 3 algoritma penjadwalan.
 *
 * Konfigurasi infrastruktur (datacenter, host, VM) diatur lewat
 * DC_SPECS[] dan VM_SPECS[] di bagian atas kelas.
 * Untuk mengubah jumlah/spek datacenter atau VM, cukup edit bagian
 * KONFIGURASI tanpa menyentuh logika simulasi di bawahnya.
 *
 * Mapping VM -> Host ditentukan eksplisit lewat hostOfVm[] di runOnce()
 * agar penempatan tidak diserahkan ke kebijakan default CloudSim.
 *
 * Uji coba: n = MIN_N..MAX_N (kelipatan STEP_N), tiap n diulang 3 kali
 * (seed dataset 1/2/3), hasil diekspor ke hasil/results.csv.
 */
public class BatchRunner {
    // =========================================================
    // [KONFIGURASI] DATACENTER & HOST
    // Tambah/hapus baris untuk mengubah jumlah datacenter.
    // Setiap baris = 1 datacenter dengan spek hostnya masing-masing.
    //   kolom: hostCount | hostPes | hostMips | hostRam | hostBw | hostStorage
    // =========================================================
    static final DatacenterSpec[] DC_SPECS = {
        new DatacenterSpec( 2,          8,        100_000,  32_000,  1_000_000,  1_000_000),
    //  new DatacenterSpec( 3,          4,         50_000,  16_000,  1_000_000,  1_000_000), // contoh DC ke-2
    };

    // =========================================================
    // [KONFIGURASI] VM
    // Tambah/hapus baris untuk mengubah jumlah & tipe VM.
    // Setiap baris = 1 VM.
    //   kolom: mips  | vCpu | ram   | bw     | size
    // Jumlah VM yang dibuat mengikuti jumlah baris di VM_SPECS.
    // =========================================================
    static final VmSpec[] VM_SPECS = {
        new VmSpec(     1_000,   1,    4_096,  10_000,  10_000),  // VM-0 Small
        new VmSpec(     2_500,   1,    4_096,  10_000,  10_000),  // VM-1 Medium
        new VmSpec(     5_000,   1,    4_096,  10_000,  10_000),  // VM-2 Large
        new VmSpec(     7_500,   1,    4_096,  10_000,  10_000),  // VM-3 XLarge
    };


     // =========================================================
    // [KONFIGURASI] BATAS PENGUJIAN JUMLAH TASK (n)
    // =========================================================
    static final int MIN_N  = 9_000;
    static final int MAX_N  = 10_000;
    static final int STEP_N = 100;


    // =========================================================
    // SPEC CLASSES — tidak perlu diubah
    // =========================================================

    /** Spesifikasi 1 datacenter beserta host-host di dalamnya. */
    static class DatacenterSpec {
        final int    hostCount, hostPes;
        final double hostMips;
        final long   hostRam, hostBw, hostStorage;

        DatacenterSpec(int hostCount, int hostPes, double hostMips,
                       long hostRam, long hostBw, long hostStorage) {
            this.hostCount   = hostCount;
            this.hostPes     = hostPes;
            this.hostMips    = hostMips;
            this.hostRam     = hostRam;
            this.hostBw      = hostBw;
            this.hostStorage = hostStorage;
        }
    }

    /** Spesifikasi 1 VM. */
    static class VmSpec {
        final double mips;
        final int    vCpu;
        final long   ram, bw, size;

        VmSpec(double mips, int vCpu, long ram, long bw, long size) {
            this.mips = mips;
            this.vCpu = vCpu;
            this.ram  = ram;
            this.bw   = bw;
            this.size = size;
        }
    }

 

    public static void main(String[] args) throws IOException {
        String dataDir = args.length > 0 ? args[0] : "Dataset-Sintetik";
        String outFile = args.length > 1 ? args[1] : "results.csv";

        // List<String> csvFiles = Arrays.asList(
        //                         new java.io.File(dataDir, "synthetic_seed1.csv").getPath(),
        //         new java.io.File(dataDir, "synthetic_seed2.csv").getPath(),
        //         new java.io.File(dataDir, "synthetic_seed3.csv").getPath());
        // List<int[]> runPlan = buildRunPlan(csvFiles.size());

       List<String> csvFiles = Arrays.asList(
                Paths.get(dataDir, "synthetic_seed1.csv").toString(),
                Paths.get(dataDir, "synthetic_seed2.csv").toString(),
                Paths.get(dataDir, "synthetic_seed3.csv").toString());
        List<int[]> runPlan = buildRunPlan(csvFiles.size());

        try (PrintWriter writer = new PrintWriter(new FileWriter(outFile))) {
            writer.println("algorithm,n_tasks,rep,dataset_file,makespan,degree_of_imbalance,utilization,throughput,cloudlet_success");
            for (int[] plan : runPlan) {
                for (String algo : new String[]{"MCT", "FCFS", "MINMIN"}) {
                    String row = runOnce(algo, plan[0], csvFiles.get(plan[1]));
                    if (row != null) writer.println(row);
                }
                writer.flush();
                System.out.printf("[PROGRES] selesai n=%d rep=%d%n", plan[0], plan[1] + 1);
            }
        }
        System.out.println("[SELESAI] Hasil lengkap tersimpan di " + outFile);
    }

    /** Pasangan (n, rep) untuk seluruh sweep sesuai batas konfigurasi, 3 repetisi. */
    static List<int[]> buildRunPlan(int numSeeds) {
        List<int[]> plan = new ArrayList<>();
        for (int rep = 0; rep < 3; rep++) {
            int seed = rep % numSeeds;
            for (int n = MIN_N; n <= MAX_N; n += STEP_N) {
                plan.add(new int[]{n, seed});
            }
        }
        return plan;
    }


    // =========================================================
    // HELPER: Pembuatan infrastruktur
    // =========================================================

    /**
     * Membuat daftar Host untuk satu datacenter sesuai spesifikasinya.
     * Setiap host memiliki hostPes buah core CPU masing-masing hostMips MIPS.
     */
    static List<Host> buatHostList(DatacenterSpec spec) {
        List<Host> hostList = new ArrayList<>();
        for (int h = 0; h < spec.hostCount; h++) {
            List<Pe> peList = new ArrayList<>();
            for (int j = 0; j < spec.hostPes; j++)
                peList.add(new PeSimple(spec.hostMips));
            hostList.add(new HostSimple(spec.hostRam, spec.hostBw, spec.hostStorage, peList));
        }
        return hostList;
    }

    /**
     * Membuat daftar VM dari VM_SPECS.
     * Setiap VM menggunakan CloudletSchedulerSpaceShared (antri serial per VM).
     */
    static List<Vm> buatVmList() {
        List<Vm> vmList = new ArrayList<>();
        for (VmSpec spec : VM_SPECS) {
            Vm vm = new VmSimple(spec.mips, spec.vCpu);
            vm.setRam(spec.ram).setBw(spec.bw).setSize(spec.size);
            vm.setCloudletScheduler(new CloudletSchedulerSpaceShared());
            vmList.add(vm);
        }
        return vmList;
    }


     // =========================================================
    // SIMULASI UTAMA
    // =========================================================

    static String runOnce(String algo, int n, String csvFile) {
        List<Long> lengths = readCsv(csvFile, n);
        if (lengths.isEmpty()) {
            System.out.println("[ERROR] CSV kosong/gagal dibaca: " + csvFile);
            return null;
        }

        CloudSim simulation = new CloudSim();

        // --- Buat semua datacenter dari DC_SPECS ---
        // allHosts menampung seluruh host (lintas DC) untuk keperluan placement VM.
            new DatacenterSimple(
                    simulation,
                    buatHostList(spec),
                    new VmAllocationPolicyFirstFit());
        }

        DatacenterBroker broker = new DatacenterBrokerSimple(simulation);

        List<Vm> vmList = buatVmList();
        final boolean[] vmAllocated = new boolean[vmList.size()];
        for (int i = 0; i < vmList.size(); i++) {
            final int vmIndex = i;
            vmList.get(i).addOnHostAllocationListener(
                    info -> vmAllocated[vmIndex] = true);
        }
        broker.submitVmList(vmList);

        List<Cloudlet> cloudletList = new ArrayList<>();
        for (long len : lengths) {
            cloudletList.add(new CloudletSimple(len, 1)
                    .setUtilizationModel(new UtilizationModelDynamic(1.0)));
        }

        // ==== Penjadwalan: hitung mapping task->VM di luar CloudSim ====
        double[] ready = new double[vmList.size()];
        switch (algo) {
            case "FCFS" -> fcfsAssign(cloudletList, vmList, ready);
            case "MCT" -> mctAssign(cloudletList, vmList, ready);
            case "MINMIN" -> minMinAssign(cloudletList, vmList, ready);
            default -> throw new IllegalArgumentException("Algoritma tidak dikenal: " + algo);
        }

        broker.submitCloudletList(cloudletList);
                simulation.start();

        // Pastikan setiap VM berhasil dialokasikan pada salah satu datacenter.
        for (boolean allocated : vmAllocated) {
            if (!allocated) {
                System.out.printf("[PERINGATAN] VM tidak berhasil dialokasikan (algo=%s, n=%d)%n", algo, n);
                break;
            }
        }


        List<Cloudlet> finished = broker.getCloudletFinishedList();
        double makespan = 0;
        double busySum = 0;
        for (Cloudlet c : finished) {
            makespan = Math.max(makespan, c.getFinishTime());
            busySum += c.getActualCpuTime();
        }

        int m = vmList.size();
        // Degree of Imbalance = (max_j T_j - min_j T_j) / mean_j T_j,
        // dengan T_j = total waktu sibuk VM j. Array ready[] dari tahap penjadwalan
        // tepat menyimpan T_j per VM (semua task tiba di t=0), jadi dipakai langsung.
        // (Koreksi: versi sebelumnya keliru memakai rata-rata finish time seluruh cloudlet.)
        double tMax = ready[0], tMin = ready[0], tSum = 0;
        for (double t : ready) {
            tMax = Math.max(tMax, t);
            tMin = Math.min(tMin, t);
            tSum += t;
        }
        double tAvg = tSum / m;
        double di = tAvg > 0 ? (tMax - tMin) / tAvg : 0;
        double utilization = makespan > 0 ? busySum / (m * makespan) : 0;
        double throughput = makespan > 0 ? finished.size() / makespan : 0;

        return String.format(java.util.Locale.US,
                "%s,%d,%d,%s,%.2f,%.4f,%.4f,%.6f,%d",
                algo, n, planRep(csvFile), new java.io.File(csvFile).getName(),
                makespan, di, utilization, throughput, finished.size());
    }

    /** FCFS: task pertama datang, diassign ke VM dengan ready time paling awal. */
    static void fcfsAssign(List<Cloudlet> cloudlets, List<Vm> vms, double[] ready) {
        for (Cloudlet c : cloudlets) {
            int best = 0;
            double earliest = Double.MAX_VALUE;
            for (int j = 0; j < vms.size(); j++) {
                if (ready[j] < earliest) {
                    earliest = ready[j];
                    best = j;
                }
            }
            c.setVm(vms.get(best));
            ready[best] += c.getLength() / vms.get(best).getMips();
        }
    }

    /** MCT: tiap task diassign ke VM dengan completion time terkecil. */
    static void mctAssign(List<Cloudlet> cloudlets, List<Vm> vms, double[] ready) {
        for (Cloudlet c : cloudlets) {
            int best = 0;
            double minCt = Double.MAX_VALUE;
            for (int j = 0; j < vms.size(); j++) {
                double ct = ready[j] + c.getLength() / vms.get(j).getMips();
                if (ct < minCt) {
                    minCt = ct;
                    best = j;
                }
            }
            c.setVm(vms.get(best));
            ready[best] = minCt;
        }
    }

    /** Min-Min: dari semua task tersisa, pasangan (task, VM) dgn CT terkecil dieksekusi duluan.
     *  Persamaan logika dengan versi O(n^2*m) README, tapi pakai array boolean supaya
     *  tetap praktis untuk n = 10.000 (ArrayList.remove O(n) dihapus). */
    static void minMinAssign(List<Cloudlet> cloudlets, List<Vm> vms, double[] ready) {
        int n = cloudlets.size();
        int m = vms.size();
        boolean[] done = new boolean[n];
        double[] et = new double[m];
        for (int j = 0; j < m; j++) et[j] = vms.get(j).getMips();

        for (int assigned = 0; assigned < n; assigned++) {
            int winC = -1, winV = -1;
            double globalMin = Double.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                if (done[i]) continue;
                Cloudlet c = cloudlets.get(i);
                long len = c.getLength();
                for (int j = 0; j < m; j++) {
                    double ct = ready[j] + len / et[j];
                    if (ct < globalMin) {
                        globalMin = ct;
                        winC = i;
                        winV = j;
                    }
                }
            }
            cloudlets.get(winC).setVm(vms.get(winV));
            ready[winV] = globalMin;
            done[winC] = true;
        }
    }

    static List<Long> readCsv(String path, int limit) {
        List<Long> lengths = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line = br.readLine(); // header
            while ((line = br.readLine()) != null && lengths.size() < limit) {
                lengths.add(Long.parseLong(line.split(",")[1].trim()));
            }
        } catch (IOException | NumberFormatException e) {
            System.out.println("[ERROR] Gagal membaca " + path + ": " + e.getMessage());
        }
        return lengths;
    }

    static int planRep(String csvFile) {
        if (csvFile.contains("seed1")) return 1;
        if (csvFile.contains("seed2")) return 2;
        return 3;
    }
}
