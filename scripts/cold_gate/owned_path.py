from __future__ import annotations

import stat
from pathlib import Path


def require_directory(path: Path) -> None:
    try:
        metadata = path.lstat()
    except FileNotFoundError as exception:
        raise RuntimeError(f"owned directory is missing: {path}") from exception
    if path.is_symlink() or not stat.S_ISDIR(metadata.st_mode):
        raise RuntimeError(f"owned path is not a physical directory: {path}")
    if path.resolve(strict=True) != path.absolute():
        raise RuntimeError(f"owned directory resolves outside its path: {path}")


def ensure_directory(path: Path) -> bool:
    if path.exists() or path.is_symlink():
        require_directory(path)
        return False
    require_directory(path.parent)
    path.mkdir()
    require_directory(path)
    return True


def require_regular_file(path: Path) -> None:
    try:
        metadata = path.lstat()
    except FileNotFoundError as exception:
        raise RuntimeError(f"owned marker is missing: {path}") from exception
    if path.is_symlink() or not stat.S_ISREG(metadata.st_mode):
        raise RuntimeError(f"owned marker is not a regular file: {path}")
    if path.resolve(strict=True) != path.absolute():
        raise RuntimeError(f"owned marker resolves outside its path: {path}")
