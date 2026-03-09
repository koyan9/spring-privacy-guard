#!/usr/bin/env python3
from __future__ import annotations

import os
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
UTF8_BOM = b"\xef\xbb\xbf"
CI_MODE = "--ci" in sys.argv[1:] or os.environ.get("GITHUB_ACTIONS") == "true"

LF_SUFFIXES = {".java", ".json", ".md", ".properties", ".py", ".sh", ".sql", ".txt", ".xml", ".yml", ".yaml"}
CRLF_SUFFIXES = {".cmd", ".ps1"}
LF_FILENAMES = {".editorconfig", ".gitattributes", ".gitignore", "mvnw"}
CRLF_FILENAMES = {"mvnw.cmd"}

REQUIRED_FILES = [
    Path("SECURITY.md"),
    Path("SUPPORT.md"),
    Path("docs/GITHUB_LABELS.md"),
    Path(".github/ISSUE_TEMPLATE/bug_report.md"),
    Path(".github/ISSUE_TEMPLATE/feature_request.md"),
    Path(".github/ISSUE_TEMPLATE/config.yml"),
    Path(".github/PULL_REQUEST_TEMPLATE.md"),
    Path("docs/INDEX.md"),
    Path("docs/RELEASE_ANNOUNCEMENT_v0.2.0.md"),
    Path("docs/RELEASE_ANNOUNCEMENT_v0.2.0.zh-CN.md"),
    Path("RELEASE_NOTES_v0.2.0.md"),
]

REQUIRED_SNIPPETS = {
    Path("README.md"): ["docs/INDEX.md"],
    Path("README.zh-CN.md"): ["docs/INDEX.md"],
    Path("docs/INDEX.md"): [
        "SECURITY.md",
        "SUPPORT.md",
        "docs/GITHUB_LABELS.md",
        "RELEASE_NOTES_v0.2.0.md",
        "docs/RELEASE_ANNOUNCEMENT_v0.2.0.md",
        "docs/RELEASE_ANNOUNCEMENT_v0.2.0.zh-CN.md",
    ],
    Path("AGENTS.md"): [".editorconfig", ".gitattributes"],
    Path("CONTRIBUTING.md"): [".editorconfig", ".gitattributes", "SECURITY.md", "SUPPORT.md", "docs/GITHUB_LABELS.md"],
}


def git_paths(*args: str) -> set[Path]:
    result = subprocess.run(
        ["git", *args],
        cwd=REPO_ROOT,
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    output = result.stdout
    if not output:
        return set()
    return {Path(item.decode("utf-8")) for item in output.split(b"\0") if item}


def tracked_paths() -> list[Path]:
    return sorted(git_paths("ls-files", "-z"))


def dirty_paths() -> set[Path]:
    return (
        git_paths("diff", "--name-only", "-z")
        | git_paths("diff", "--cached", "--name-only", "-z")
        | git_paths("ls-files", "--others", "--exclude-standard", "-z")
    )


def should_check(path: Path) -> bool:
    return path.name in (LF_FILENAMES | CRLF_FILENAMES) or path.suffix in (LF_SUFFIXES | CRLF_SUFFIXES)


def expected_eol(path: Path) -> str | None:
    if path.name in CRLF_FILENAMES or path.suffix in CRLF_SUFFIXES:
        return "crlf"
    if path.name in LF_FILENAMES or path.suffix in LF_SUFFIXES:
        return "lf"
    return None


def read_worktree_bytes(path: Path) -> bytes:
    return (REPO_ROOT / path).read_bytes()


def validate_utf8(path: Path, data: bytes, errors: list[str]) -> None:
    if data.startswith(UTF8_BOM):
        errors.append(f"{path}: UTF-8 BOM is not allowed")
        return
    try:
        data.decode("utf-8")
    except UnicodeDecodeError as exc:
        errors.append(f"{path}: not valid UTF-8 ({exc})")


def validate_eol(path: Path, data: bytes, errors: list[str]) -> None:
    eol = expected_eol(path)
    if eol == "lf":
        if b"\r" in data:
            errors.append(f"{path}: expected LF line endings")
    elif eol == "crlf":
        normalized = data.replace(b"\r\n", b"")
        if b"\n" in normalized or b"\r" in normalized:
            errors.append(f"{path}: expected CRLF line endings")


def validate_docs(errors: list[str]) -> None:
    for path in REQUIRED_FILES:
        if not (REPO_ROOT / path).exists():
            errors.append(f"{path}: required documentation file is missing")
    for path, snippets in REQUIRED_SNIPPETS.items():
        full_path = REPO_ROOT / path
        if not full_path.exists():
            errors.append(f"{path}: required documentation file is missing")
            continue
        content = full_path.read_text(encoding="utf-8")
        for snippet in snippets:
            if snippet not in content:
                errors.append(f"{path}: missing required reference '{snippet}'")


def main() -> int:
    errors: list[str] = []
    dirty = dirty_paths()
    tracked = tracked_paths()

    tracked_count = 0
    for path in tracked:
        if not should_check(path):
            continue
        full_path = REPO_ROOT / path
        if not full_path.exists():
            continue
        tracked_count += 1
        data = read_worktree_bytes(path)
        validate_utf8(path, data, errors)
        if CI_MODE or path in dirty:
            validate_eol(path, data, errors)

    for path in dirty:
        if path in tracked or not should_check(path):
            continue
        full_path = REPO_ROOT / path
        if not full_path.exists():
            continue
        data = read_worktree_bytes(path)
        validate_utf8(path, data, errors)
        validate_eol(path, data, errors)

    validate_docs(errors)

    if errors:
        print("Repository hygiene check failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    mode = "CI" if CI_MODE else "local"
    print(f"Repository hygiene check passed in {mode} mode for {tracked_count} tracked text files.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
