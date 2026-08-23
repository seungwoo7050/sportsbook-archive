import hashlib
import pathlib
import tempfile
import unittest
import zipfile

from scripts.cold_gate.artifacts import SERVICES, verify_release_artifacts


def class_bytes(major: int) -> bytes:
    return b"\xca\xfe\xba\xbe\x00\x00" + major.to_bytes(2, "big")


def write_release(root: pathlib.Path, major: int = 61, shared: str = "1.0.0") -> None:
    sums = []
    for service in SERVICES:
        jar = root / f"{service}.jar"
        start = f"com.sportsbook.{service}.Application"
        with zipfile.ZipFile(jar, "w") as archive:
            archive.writestr(
                "META-INF/MANIFEST.MF",
                f"Manifest-Version: 1.0\nStart-Class: {start}\n",
            )
            archive.writestr(
                "BOOT-INF/classes/" + start.replace(".", "/") + ".class",
                class_bytes(major),
            )
            archive.writestr(f"BOOT-INF/lib/shared-{shared}.jar", b"shared")
        sums.append(f"{hashlib.sha256(jar.read_bytes()).hexdigest()}  {jar.name}")
    (root / "SHA256SUMS").write_text("\n".join(sums) + "\n")


class ReleaseArtifactIdentityTest(unittest.TestCase):
    def test_verifies_hash_class_and_shared_protocol_identity(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary).resolve()
            write_release(root)

            actual = verify_release_artifacts(root)

            self.assertEqual(set(actual), {f"{service}.jar" for service in SERVICES})

    def test_rejects_checksum_drift(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary).resolve()
            write_release(root)
            (root / "wallet.jar").write_bytes(b"tampered")

            with self.assertRaisesRegex(RuntimeError, "checksum mismatch"):
                verify_release_artifacts(root)

    def test_rejects_non_java_17_application_class(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary).resolve()
            write_release(root, major=65)

            with self.assertRaisesRegex(RuntimeError, "not Java 17"):
                verify_release_artifacts(root)

    def test_rejects_shared_protocol_version_drift(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary).resolve()
            write_release(root, shared="1.0.1")

            with self.assertRaisesRegex(RuntimeError, "protocol dependency drifted"):
                verify_release_artifacts(root)


if __name__ == "__main__":
    unittest.main()
