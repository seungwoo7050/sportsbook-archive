import os
import pathlib
import tempfile
import unittest

from scripts.cold_gate.owned_path import (
    ensure_directory,
    require_directory,
    require_regular_file,
)


class OwnedPathTest(unittest.TestCase):
    def test_creates_only_below_a_physical_parent(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary).resolve()
            owned = root / "owned"

            self.assertTrue(ensure_directory(owned))
            self.assertFalse(ensure_directory(owned))
            require_directory(owned)

    def test_rejects_directory_symlinks_without_touching_the_target(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary).resolve()
            victim = root / "victim"
            victim.mkdir()
            link = root / "owned"
            link.symlink_to(victim, target_is_directory=True)

            with self.assertRaisesRegex(RuntimeError, "physical directory"):
                ensure_directory(link)
            self.assertEqual(list(victim.iterdir()), [])

    def test_accepts_only_physical_regular_markers(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary).resolve()
            marker = root / "marker"
            marker.write_text("owned\n")
            require_regular_file(marker)

            link = root / "link"
            link.symlink_to(marker)
            with self.assertRaisesRegex(RuntimeError, "regular file"):
                require_regular_file(link)

            fifo = root / "fifo"
            os.mkfifo(fifo)
            with self.assertRaisesRegex(RuntimeError, "regular file"):
                require_regular_file(fifo)


if __name__ == "__main__":
    unittest.main()
