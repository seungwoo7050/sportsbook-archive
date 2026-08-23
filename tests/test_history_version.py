import pathlib
import tempfile
import unittest

from scripts.history_guard import verify_version
from tests.history_policy_fixture import valid_development, valid_release


class HistoryVersionTest(unittest.TestCase):
    def test_rejects_version_before_terminal_release(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary)
            verify_version(root, valid_development())
            (root / "VERSION").write_text("1.0.0\n")

            with self.assertRaisesRegex(RuntimeError, "before"):
                verify_version(root, valid_development())

    def test_requires_exact_released_version_content(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary)
            with self.assertRaisesRegex(RuntimeError, "not exactly"):
                verify_version(root, valid_release())
            (root / "VERSION").write_text("1.0\n")
            with self.assertRaisesRegex(RuntimeError, "not exactly"):
                verify_version(root, valid_release())

            (root / "VERSION").write_text("1.0.0\n")
            verify_version(root, valid_release())


if __name__ == "__main__":
    unittest.main()
