import unittest

from dispatcher import estimate_cost


class CostEstimateTests(unittest.TestCase):
    def test_cost_uses_simulated_vm_runtime_and_cpu_share(self):
        costs = estimate_cost("aws", [3600, 3600, 3600, 3600])

        expected = 0.0188 / 2 * (1000 / 7500 + 2500 / 7500 + 5000 / 7500 + 7500 / 7500)
        self.assertAlmostEqual(costs["total"], expected, places=10)
        self.assertEqual(len(costs["per_vm"]), 4)

    def test_all_supported_providers_have_a_positive_rate(self):
        for provider in ("aws", "gcp", "azure"):
            self.assertGreater(estimate_cost(provider, [3600] * 4)["total"], 0)


if __name__ == "__main__":
    unittest.main()
