import org.cloudbus.cloudsim.brokers.DatacenterBroker;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.datacenters.Datacenter;
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
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MinMinSimulation {

    public static void main(String[] args) {

        CloudSim simulation = new CloudSim();

        List<Host> hostList = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            List<Pe> peList = new ArrayList<>();
            for (int j = 0; j < 8; j++) peList.add(new PeSimple(100000));
            Host host = new HostSimple(32000, 1000000, 1000000, peList);
            hostList.add(host);
        }
        Datacenter datacenter = new DatacenterSimple(simulation, hostList);

        DatacenterBroker broker = new DatacenterBrokerSimple(simulation);
        List<Vm> vmList = new ArrayList<>();
        double[] mipsCapacities = {1000, 2500, 5000, 7500};

        for (int i = 0; i < mipsCapacities.length; i++) {
            Vm vm = new VmSimple(mipsCapacities[i], 1);
            vm.setRam(4096).setBw(10000).setSize(10000);
            vm.setCloudletScheduler(new CloudletSchedulerSpaceShared());
            vmList.add(vm);
        }
        broker.submitVmList(vmList);

        List<Cloudlet> cloudletList = new ArrayList<>();
        String csvFile = "GoCJ_1000_task_simulation.csv";

        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            String line;
            boolean isHeader = true;
            while ((line = br.readLine()) != null) {
                if (isHeader) { isHeader = false; continue; }

                String[] values = line.split(",");
                long lengthMI = Long.parseLong(values[1]);

                Cloudlet cloudlet = new CloudletSimple(lengthMI, 1)
                    .setUtilizationModel(new UtilizationModelDynamic(1.0));
                cloudletList.add(cloudlet);
            }
        } catch (IOException e) {
            System.out.println("Gagal membaca CSV. Pastikan file GoCJ_1000_task_simulation.csv ada di root folder.");
            return;
        }

        double[] vmReadyTimes = new double[vmList.size()];

        List<Cloudlet> unassignedCloudlets = new ArrayList<>(cloudletList);

        while (!unassignedCloudlets.isEmpty()) {
            int winningCloudletIndex = -1;
            int winningVmIndex = -1;
            double globalMinCompletionTime = Double.MAX_VALUE;

            for (int c = 0; c < unassignedCloudlets.size(); c++) {
                Cloudlet cloudlet = unassignedCloudlets.get(c);
                
                int bestVmForThisCloudlet = -1;
                double minCompletionTimeForThisCloudlet = Double.MAX_VALUE;

                for (int v = 0; v < vmList.size(); v++) {
                    Vm vm = vmList.get(v);
                    double executionTime = cloudlet.getLength() / vm.getMips();
                    double completionTime = vmReadyTimes[v] + executionTime;

                    if (completionTime < minCompletionTimeForThisCloudlet) {
                        minCompletionTimeForThisCloudlet = completionTime;
                        bestVmForThisCloudlet = v;
                    }
                }

                if (minCompletionTimeForThisCloudlet < globalMinCompletionTime) {
                    globalMinCompletionTime = minCompletionTimeForThisCloudlet;
                    winningCloudletIndex = c;
                    winningVmIndex = bestVmForThisCloudlet;
                }
            }

            Cloudlet winningCloudlet = unassignedCloudlets.get(winningCloudletIndex);
            Vm winningVm = vmList.get(winningVmIndex);
            
            winningCloudlet.setVm(winningVm);
            vmReadyTimes[winningVmIndex] = globalMinCompletionTime;
            
            unassignedCloudlets.remove(winningCloudletIndex);
        }

        broker.submitCloudletList(cloudletList);
        System.out.println("Memulai Simulasi Min-Min di CloudSim Plus...");
        simulation.start();

        List<Cloudlet> finishedList = broker.getCloudletFinishedList();
        new CloudletsTableBuilder(finishedList).build();

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        String timestamp = LocalDateTime.now().format(dtf);
        
        String fileName = "Hasil_Simulasi_MinMin_" + timestamp + ".csv"; 
        exportToCsv(finishedList, fileName);
    }

    private static void exportToCsv(List<Cloudlet> list, String fileName) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(fileName))) {
            writer.println("Cloudlet_ID,Status,VM_ID,Actual_CPU_Time,Start_Time,Finish_Time");

            DecimalFormat dft = (DecimalFormat) DecimalFormat.getInstance(Locale.US);
            dft.applyPattern("###.##");

            double makespan = 0;

            for (Cloudlet cloudlet : list) {
                String status = cloudlet.getStatus().name();
                String cpuTime = dft.format(cloudlet.getActualCpuTime());
                String startTime = dft.format(cloudlet.getExecStartTime());
                String finishTime = dft.format(cloudlet.getFinishTime());
                
                writer.println(
                    cloudlet.getId() + "," + 
                    status + "," + 
                    cloudlet.getVm().getId() + "," + 
                    cpuTime + "," + 
                    startTime + "," + 
                    finishTime
                );

                if (cloudlet.getFinishTime() > makespan) {
                    makespan = cloudlet.getFinishTime();
                }
            }
            
            writer.println();
            writer.println("Waktu Penyelesaian Keseluruhan (Makespan),,,,," + dft.format(makespan));
            System.out.println("\nWaktu Penyelesaian Keseluruhan (Makespan): " + dft.format(makespan) + " detik");

            System.out.println("\n[BERHASIL] File hasil simulasi telah diekspor dan disimpan sebagai: " + fileName);
        } catch (IOException e) {
            System.out.println("\n[ERROR] Gagal menyimpan ke CSV: " + e.getMessage());
        }
    }
}
