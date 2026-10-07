import org.cloudbus.cloudsim.allocationpolicies.VmAllocationPolicySimple;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * BatchRunner — uji coba skala dataset untuk 3 algoritma penjadwalan.
 *
 * Sesuai draft design (revisi Minggu 3 -> Minggu 4):
 *   - 1 Datacenter, 1 Host (16 PE x 100.000 MIPS, RAM 32 GB) — cukup untuk 4 VM
 *   - 4 VM heterogen: Small 1.000 / Medium 2.500 / Large 5.000 / XLarge 7.500 MIPS
 *   - Alokasi VM ke host DITENTUKAN EKSPLISIT (bukan diserahkan ke kebijakan
 *     default CloudSim): VmAllocationPolicySimple dipasang eksplisit pada
 *     DatacenterSimple, dan karena hanya ada 1 Host maka keempat VM selalu
 *     mendarat di Host 0.
 *
 * Uji coba: n = 100..10.000 (kelipatan 100), tiap n diulang 3 kali
 * (seed dataset 1/2/3), hasil rata-rata diekspor ke results.csv.
 */
public class BatchRunner {

    static final double HOST_MIPS = 100_000;
    static final int HOST_PES = 16;
    static final long HOST_RAM = 32_000;
    static final long HOST_BW = 1_000_000;
    static final long HOST_STORAGE = 1_000_000;

    static final double[] VM_MIPS = {1_000, 2_500, 5_000, 7_500};
    static final long VM_RAM = 4_096;
    static final long VM_BW = 10_000;
    static final long VM_SIZE = 10_000;

    public static void main(String[] args) throws IOException {
        String dataDir = args.length > 0 ? args[0] : "Dataset-Sintetik";
        String outFile = args.length > 1 ? args[1] : "results.csv";

        List<String> csvFiles = Arrays.asList(
                dataDir + "\\synthetic_seed1.csv",
                dataDir + "\\synthetic_seed2.csv",
                dataDir + "\\synthetic_seed3.csv");
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

    /** Pasangan (n, rep) untuk seluruh sweep: n kelipatan 100 s/d 10.000, 3 repetisi. */
    static List<int[]> buildRunPlan(int numSeeds) {
        List<int[]> plan = new ArrayList<>();
        for (int rep = 0; rep < 3; rep++) {
            int seed = rep % numSeeds;
            for (int n = 100; n <= 10_000; n += 100) {
                plan.add(new int[]{n, seed});
            }
        }
        return plan;
    }

    static String runOnce(String algo, int n, String csvFile) {
        List<Long> lengths = readCsv(csvFile, n);
        if (lengths.isEmpty()) {
            System.out.println("[ERROR] CSV kosong/gagal dibaca: " + csvFile);
            return null;
        }

        CloudSim simulation = new CloudSim();

        // ==== 1 Host; alokasi VM DITENTUKAN EKSPLISIT: semua VM dipaksa ke Host 0 ====
        List<Host> hostList = new ArrayList<>();
        List<Pe> peList = new ArrayList<>();
        for (int j = 0; j < HOST_PES; j++) peList.add(new PeSimple(HOST_MIPS));
        Host host0 = new HostSimple(HOST_RAM, HOST_BW, HOST_STORAGE, peList);
        hostList.add(host0);
        new DatacenterSimple(simulation, hostList, new VmAllocationPolicySimple());

        DatacenterBroker broker = new DatacenterBrokerSimple(simulation);

        List<Vm> vmList = new ArrayList<>();
        for (double mips : VM_MIPS) {
            Vm vm = new VmSimple(mips, 1);
            vm.setRam(VM_RAM).setBw(VM_BW).setSize(VM_SIZE);
            vm.setCloudletScheduler(new CloudletSchedulerSpaceShared());
            vmList.add(vm);
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

        List<Cloudlet> finished = broker.getCloudletFinishedList();
        double makespan = 0;
        double minFinish = Double.MAX_VALUE;
        double sumFinish = 0;
        double busySum = 0;
        for (Cloudlet c : finished) {
            double f = c.getFinishTime();
            makespan = Math.max(makespan, f);
            minFinish = Math.min(minFinish, f);
            sumFinish += f;
            busySum += c.getActualCpuTime();
        }

        int m = vmList.size();
        double meanFinish = sumFinish / m;
        double di = meanFinish > 0 ? (makespan - minFinish) / meanFinish : 0;
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
