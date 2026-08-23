from __future__ import annotations

from scripts.cold_gate.history_repository import Commit
from scripts.cold_gate.history_rules import (
    BANNED_TERMS,
    DOCS_SUBJECT,
    RELEASE_SUBJECT,
    ROOT_SUBJECT,
    SUBJECT,
    forbidden_path,
    is_documentation,
    is_large_exception,
    is_test_path,
)


def verify_history(commits: tuple[Commit, ...], minimum: int = 240) -> None:
    if len(commits) < minimum:
        raise RuntimeError(f"history has only {len(commits)} commits; expected at least {minimum}")
    _verify_release_shape(commits)
    for index, commit in enumerate(commits):
        label = commit.sha[:12]
        if index == 0:
            if commit.parents or commit.subject != ROOT_SUBJECT or _paths(commit) != ("README.md",):
                raise RuntimeError("history root is not the README-only ownership commit")
        elif commit.parents != (commits[index - 1].sha,):
            raise RuntimeError(f"{label} is not on the single linear parent chain")
        if not commit.message or commit.message != commit.subject:
            raise RuntimeError(f"{label} has an empty or multi-line message")
        if SUBJECT.fullmatch(commit.subject) is None:
            raise RuntimeError(f"{label} subject is not conventional")
        lower_subject = commit.subject.lower()
        if any(term in lower_subject for term in BANNED_TERMS):
            raise RuntimeError(f"{label} subject records forbidden process metadata")
        if not commit.changes or len(set(_paths(commit))) != len(commit.changes):
            raise RuntimeError(f"{label} has an empty or duplicate change set")
        if any(forbidden_path(path) for path in _paths(commit)):
            raise RuntimeError(f"{label} tracks generated or process artifacts")
        final_docs = index == len(commits) - 1 and commit.subject == DOCS_SUBJECT
        if commit.churn > 100 and not final_docs and not is_large_exception(commit.changes):
            raise RuntimeError(f"{label} exceeds the 100-line responsibility limit")
        _verify_documentation(commit, index, len(commits))
        terminal_release = index == len(commits) - 2 and commit.subject == RELEASE_SUBJECT
        if index != 0 and not terminal_release and not final_docs:
            _verify_development_boundary(commits, index)


def _verify_release_shape(commits: tuple[Commit, ...]) -> None:
    releases = [index for index, commit in enumerate(commits) if commit.subject == RELEASE_SUBJECT]
    documents = [index for index, commit in enumerate(commits) if commit.subject == DOCS_SUBJECT]
    if not releases and not documents:
        return
    if releases != [len(commits) - 2] or documents != [len(commits) - 1]:
        raise RuntimeError("release and final documentation commits are not terminal")
    if _paths(commits[-2]) != ("VERSION",) or _paths(commits[-1]) != ("README.md",):
        raise RuntimeError("terminal release commits have an invalid file boundary")


def _verify_documentation(commit: Commit, index: int, count: int) -> None:
    for path in _paths(commit):
        allowed_readme = path == "README.md" and (
            index == 0 or (index == count - 1 and commit.subject == DOCS_SUBJECT)
        )
        allowed_version = path == "VERSION" and (
            index == count - 2 and commit.subject == RELEASE_SUBJECT
        )
        if (is_documentation(path) and not allowed_readme) or (
            path in {"README.md", "VERSION"} and not (allowed_readme or allowed_version)
        ):
            raise RuntimeError(f"{commit.sha[:12]} mixes documentation into development")


def _verify_development_boundary(commits: tuple[Commit, ...], index: int) -> None:
    commit = commits[index]
    if "(release):" in commit.subject:
        raise RuntimeError(f"{commit.sha[:12]} uses the reserved release scope")
    if commit.subject.startswith("docs("):
        raise RuntimeError(f"{commit.sha[:12]} is an intermediate documentation commit")
    tests = tuple(change for change in commit.changes if is_test_path(change.path))
    production = tuple(change for change in commit.changes if not is_test_path(change.path))
    if tests and production:
        raise RuntimeError(f"{commit.sha[:12]} mixes production and tests")
    if bool(tests) != commit.subject.startswith("test("):
        raise RuntimeError(f"{commit.sha[:12]} subject does not match its change boundary")
    if production and len(production) > 2:
        raise RuntimeError(f"{commit.sha[:12]} changes more than two production files")
    if production:
        if index + 1 >= len(commits) or not all(
            is_test_path(change.path) for change in commits[index + 1].changes
        ):
            raise RuntimeError(f"{commit.sha[:12]} is not followed by an adjacent test commit")


def _paths(commit: Commit) -> tuple[str, ...]:
    return tuple(change.path for change in commit.changes)
