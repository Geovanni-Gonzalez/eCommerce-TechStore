import unittest
from scripts.qa.load_catalog_cart import p95, require_local_api


class LoadHarnessTest(unittest.TestCase):
    def test_p95_nearest_rank_including_single_sample(self):
        self.assertEqual(19, p95(list(range(1, 21))))
        self.assertEqual(7, p95([7]))
        with self.assertRaises(ValueError):
            p95([])

    def test_only_local_api_is_allowed(self):
        self.assertEqual("http://127.0.0.1:8080",
                         require_local_api("http://127.0.0.1:8080/"))
        for address in ("https://example.com", "http://127.0.0.1.evil.com",
                        "http://user:secret@localhost:8080",
                        "http://localhost:8080/api", "file:///etc/passwd"):
            with self.subTest(address=address):
                with self.assertRaises(ValueError):
                    require_local_api(address)


if __name__ == "__main__":
    unittest.main()
