from __future__ import annotations

import re

from scripts.cold_gate.history_repository import Change


ROOT_SUBJECT = "docs(project): establish orchestration ownership"
RELEASE_SUBJECT = "build(release): release orchestration 1.0.0"
DOCS_SUBJECT = "docs(project): document full-stack operations"
SUBJECT = re.compile(r"^(build|chore|ci|docs|feat|fix|test)\([a-z0-9-]+\): \S(?:.*\S)?$")
BANNED_TERMS = ("fixup!", "squash!", "reconstruct", "provenance", "devlog", "changelog")
GENERATED_DIRECTORIES = frozenset(
    {".runtime", "target", "build", "evidence", "results", "reports", "__pycache__"}
)
GENERATED_SUFFIXES = (".class", ".jar", ".log", ".pyc")
LARGE_EXCEPTIONS = (
    re.compile(r"(^|/)mvnw(?:\.cmd)?$"),
    re.compile(r"(^|/)\.mvn/wrapper/maven-wrapper\.properties$"),
    re.compile(r"(^|/)pom\.xml$"),
    re.compile(r"(^|/)src/main/resources/db/migration/V[1-9][0-9]*__[^/]+\.sql$"),
    re.compile(r"\.avsc$"),
)


def is_test_path(path: str) -> bool:
    if path.startswith(("tests/", "e2e/")):
        return True
    parts = path.split("/")
    return len(parts) >= 5 and parts[0] == "fixtures" and parts[2:4] == ["src", "test"]


def is_documentation(path: str) -> bool:
    return path.lower().endswith((".md", ".adoc", ".rst"))


def forbidden_path(path: str) -> bool:
    lower = path.lower()
    parts = lower.split("/")
    if any(term in part for term in BANNED_TERMS for part in parts):
        return True
    if any(part in GENERATED_DIRECTORIES for part in parts[:-1]):
        return True
    return (
        lower == "docker/jars"
        or lower.startswith("docker/.jars/")
        or lower.endswith(GENERATED_SUFFIXES)
    )


def is_large_exception(changes: tuple[Change, ...]) -> bool:
    return len(changes) == 1 and any(
        pattern.search(changes[0].path) for pattern in LARGE_EXCEPTIONS
    )
