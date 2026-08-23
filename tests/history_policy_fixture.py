from __future__ import annotations

import dataclasses

from scripts.cold_gate.history_repository import Change, Commit
from scripts.cold_gate.history_rules import DOCS_SUBJECT, RELEASE_SUBJECT, ROOT_SUBJECT


def history(*rows: tuple[str, tuple[str, ...]]) -> tuple[Commit, ...]:
    commits = []
    for index, (subject, paths) in enumerate(rows, 1):
        sha = f"{index:040x}"
        parents = () if not commits else (commits[-1].sha,)
        changes = tuple(Change(path, 1, 0) for path in paths)
        commits.append(Commit(sha, parents, subject, changes))
    return tuple(commits)


def valid_development() -> tuple[Commit, ...]:
    return history(
        (ROOT_SUBJECT, ("README.md",)),
        ("build(app): add behavior", ("src/app.py",)),
        ("test(app): verify behavior", ("tests/test_app.py",)),
    )


def valid_release() -> tuple[Commit, ...]:
    return history(
        (ROOT_SUBJECT, ("README.md",)),
        ("build(app): add behavior", ("src/app.py",)),
        ("test(app): verify behavior", ("tests/test_app.py",)),
        (RELEASE_SUBJECT, ("VERSION",)),
        (DOCS_SUBJECT, ("README.md",)),
    )


def changed(
    commits: tuple[Commit, ...], index: int, **values
) -> tuple[Commit, ...]:
    mutable = list(commits)
    mutable[index] = dataclasses.replace(mutable[index], **values)
    return tuple(mutable)
