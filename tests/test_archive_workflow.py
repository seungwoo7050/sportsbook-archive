import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
WORKFLOW = ROOT / ".github/workflows/archive.yml"


class ArchiveWorkflowTest(unittest.TestCase):
    def test_owns_one_exact_sha_archive_workflow(self):
        workflows = sorted(path.name for path in WORKFLOW.parent.glob("*.y*ml"))
        text = WORKFLOW.read_text()

        self.assertEqual(workflows, ["archive.yml"])
        required = (
            "branches: [orchestration]",
            "permissions:\n  contents: read",
            "uses: actions/checkout@v4",
            "ref: ${{ github.sha }}",
            "fetch-depth: 0",
            "persist-credentials: false",
            "uses: actions/setup-java@v4",
            "distribution: temurin",
            'java-version: "17"',
            "uses: actions/setup-python@v5",
            'python-version: "3.12"',
            "if: always()",
            "uses: actions/upload-artifact@v4",
            "path: evidence/cold-gate/**",
        )
        for value in required:
            with self.subTest(value=value):
                self.assertIn(value, text)
        self.assertNotIn("repository:", text)
        self.assertNotIn("load-workflow", text)

    def test_runs_static_guards_before_one_cold_stack_gate(self):
        text = WORKFLOW.read_text()
        commands = (
            "python3 -B scripts/history_guard.py",
            "python3 -B -m unittest discover -s tests",
            "python3 -I -B scripts/cold_release_gate.py",
        )
        for command in commands:
            self.assertEqual(text.count(command), 1)
        positions = tuple(text.index(command) for command in commands)
        self.assertEqual(positions, tuple(sorted(positions)))
        self.assertEqual(text.count("Run the one cold release gate"), 1)


if __name__ == "__main__":
    unittest.main()
