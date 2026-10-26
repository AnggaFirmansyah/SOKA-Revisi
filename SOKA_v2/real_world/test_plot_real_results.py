import unittest

from plot_real_results import summarize_rows


class RealWorldPlotDataTests(unittest.TestCase):
    def test_summary_reconstructs_metrics_from_task_csv_rows(self):
        rows = [
            {"task": "0", "vm": "0", "start": "0", "end": "4"},
            {"task": "1", "vm": "1", "start": "0", "end": "2"},
            {"task": "2", "vm": "1", "start": "2", "end": "5"},
        ]

        summary = summarize_rows("mct", 3, rows, ideal=4.5)

        self.assertEqual(summary["algorithm"], "mct")
        self.assertEqual(summary["n"], 3)
        self.assertEqual(summary["distribution"], [1, 2, 0, 0])
        self.assertAlmostEqual(summary["makespan"], 5.0)
        self.assertAlmostEqual(summary["utilization"], 0.45)
        self.assertAlmostEqual(summary["throughput"], 0.6)
        self.assertAlmostEqual(summary["overhead_pct"], (5 / 4.5 - 1) * 100)


if __name__ == "__main__":
    unittest.main()
