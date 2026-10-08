import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Random;

/**
 * SyntheticDatasetGenerator — pembangkit dataset sintetis untuk uji coba penjadwalan.
 *
 * Kriteria dataset DITENTUKAN SENDIRI (bukan meniru statistik GoCJ), mengikuti
 * karakteristik umum beban kerja cloud yang heterogen dan right-skewed:
 *
 *   Dataset dibagi menjadi 3 kelas beban (tri-modal), tiap task diundi merata
 *   (uniform integer) di dalam rentang kelasnya, lalu kelas dipilih sesuai proporsi:
 *
 *     | Kelas  | Proporsi | Rentang panjang (MI) | Panjang rata-rata |
 *     |--------|----------|----------------------|-------------------|
 *     | Short  |   50%    |  10.000 .. 150.000   |      80.000       |
 *     | Medium |   35%    | 200.000 .. 450.000   |     325.000       |
 *     | Long   |   15%    | 500.000 .. 1.000.000 |     750.000       |
 *
 *   Panjang rata-rata per task kira-kira 266.250 MI
 *   (0,50 x 80.000 + 0,35 x 325.000 + 0,15 x 750.000).
 *
 * Dua parameter di atas (proporsi kelas dan rentang MI) adalah "kriteria" yang
 * kita atur sendiri; keduanya sengaja dipisah dari distribusi GoCJ agar dataset
 * benar-benar independen dan bisa dijelaskan ke dosen.
 *
 * Reproducible: dipakai java.util.Random dengan seed eksplisit (1, 2, 3),
 * sehingga dataset yang sama selalu dihasilkan ulang (deterministik).
 *
 * Output: tiga file CSV "taskId,lengthMI" dengan 10.000 task per file,
 *         synthetic_seed1.csv .. synthetic_seed3.csv.
 *
 * Pemakaian:
 *   javac -d out SyntheticDatasetGenerator.java
 *   java -cp out SyntheticDatasetGenerator [folder-output] [jumlah-task] [jumlah-seed]
 *   (default: folder "Dataset-Sintetik", 10.000 task, 3 seed)
 */
public class SyntheticDatasetGenerator {

    // ==== KRITERIA DATASET (diatur sendiri) ====
    static final double SHORT_PROP = 0.50;
    static final double MEDIUM_PROP = 0.35;
    // sisa 15% = Long

    static final long SHORT_MIN = 10_000, SHORT_MAX = 150_000;
    static final long MEDIUM_MIN = 200_000, MEDIUM_MAX = 450_000;
    static final long LONG_MIN = 500_000, LONG_MAX = 1_000_000;

    static final int DEFAULT_TASKS = 10_000;
    static final int DEFAULT_SEEDS = 3;

    public static void main(String[] args) throws IOException {
        String outDir = args.length > 0 ? args[0] : "Dataset-Sintetik";
        int numTasks = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_TASKS;
        int numSeeds = args.length > 2 ? Integer.parseInt(args[2]) : DEFAULT_SEEDS;

        java.nio.file.Files.createDirectories(Paths.get(outDir));

        System.out.printf(Locale.US,
                "Membangkitkan %d task x %d seed ke folder '%s'%n",
                numTasks, numSeeds, outDir);
        System.out.printf(Locale.US,
                "Kriteria: Short %.0f%% [%d..%d] MI, Medium %.0f%% [%d..%d] MI, Long %.0f%% [%d..%d] MI%n",
                SHORT_PROP * 100, SHORT_MIN, SHORT_MAX,
                MEDIUM_PROP * 100, MEDIUM_MIN, MEDIUM_MAX,
                (1 - SHORT_PROP - MEDIUM_PROP) * 100, LONG_MIN, LONG_MAX);

        for (int seed = 1; seed <= numSeeds; seed++) {
            String file = Paths.get(outDir, "synthetic_seed" + seed + ".csv").toString();
            generate(file, numTasks, seed);
            System.out.println("[OK] " + file);
        }
        System.out.println("[SELESAI] Dataset sintetis siap dipakai BatchRunner.");
    }

    static void generate(String path, int numTasks, int seed) throws IOException {
        Random rnd = new Random(seed);
        long sum = 0;
        long min = Long.MAX_VALUE, max = Long.MIN_VALUE;
        int nShort = 0, nMedium = 0, nLong = 0;

        try (PrintWriter w = new PrintWriter(new FileWriter(path))) {
            w.println("taskId,lengthMI");
            for (int id = 1; id <= numTasks; id++) {
                double u = rnd.nextDouble();
                long len;
                if (u < SHORT_PROP) {
                    len = uniform(rnd, SHORT_MIN, SHORT_MAX);
                    nShort++;
                } else if (u < SHORT_PROP + MEDIUM_PROP) {
                    len = uniform(rnd, MEDIUM_MIN, MEDIUM_MAX);
                    nMedium++;
                } else {
                    len = uniform(rnd, LONG_MIN, LONG_MAX);
                    nLong++;
                }
                w.println(id + "," + len);
                sum += len;
                min = Math.min(min, len);
                max = Math.max(max, len);
            }
        }

        System.out.printf(Locale.US,
                "    seed%d: n=%d  Short=%d Medium=%d Long=%d  min=%d max=%d mean=%.1f totalMI=%d%n",
                seed, numTasks, nShort, nMedium, nLong, min, max, sum / (double) numTasks, sum);
    }

    /** Bilangan bulat acak merata di [min, max]. */
    static long uniform(Random rnd, long min, long max) {
        return min + (long) (rnd.nextDouble() * (max - min + 1));
    }
}