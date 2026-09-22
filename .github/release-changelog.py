#!/usr/bin/env python3
"""Move the [Unreleased] section of CHANGELOG.md to a released version.

Usage: release-changelog.py <major.minor.patch>

Renames the section with today's date, inserts a fresh empty [Unreleased]
section, and maintains the keepachangelog compare links at the bottom.
Exits non-zero when there is nothing to release or the changelog is
malformed, so a release run fails loudly instead of shipping an empty
release section.
"""

import re
import subprocess
import sys
from datetime import date
from pathlib import Path

UNRELEASED_HEADING = "## [Unreleased]"


def previous_ref() -> str:
    tags = subprocess.run(
        ["git", "describe", "--tags", "--abbrev=0", "--match", "[0-9]*.[0-9]*.[0-9]*"],
        capture_output=True, text=True)
    if tags.returncode == 0:
        return tags.stdout.strip()
    first = subprocess.run(
        ["git", "rev-list", "--max-parents=0", "HEAD"],
        capture_output=True, text=True, check=True)
    return first.stdout.strip()[:7]


def repository_slug() -> str:
    url = subprocess.run(
        ["git", "remote", "get-url", "origin"], capture_output=True, text=True, check=True
    ).stdout.strip()
    match = re.search(r"[:/]([^/:]+/[^/]+?)(?:\.git)?$", url)
    return match.group(1) if match else "sipgate/sparta-hss-foss"


def main() -> int:
    if len(sys.argv) != 2 or not re.fullmatch(r"\d+\.\d+\.\d+", sys.argv[1]):
        print(f"usage: {sys.argv[0]} <major.minor.patch>", file=sys.stderr)
        return 2
    version = sys.argv[1]

    changelog = Path("CHANGELOG.md")
    lines = changelog.read_text(encoding="utf-8").splitlines(keepends=True)

    try:
        unreleased = next(
            i for i, line in enumerate(lines) if line.rstrip("\n") == UNRELEASED_HEADING)
    except StopIteration:
        print("no [Unreleased] section in CHANGELOG.md", file=sys.stderr)
        return 1

    body_end = next(
        (i for i in range(unreleased + 1, len(lines)) if lines[i].startswith("## ")),
        len(lines))
    body = lines[unreleased + 1:body_end]
    if not any("###" in line or line.lstrip().startswith("-") for line in body):
        print("the [Unreleased] section is empty; add entries before releasing",
              file=sys.stderr)
        return 1

    lines[unreleased:body_end] = [
        f"{UNRELEASED_HEADING}\n",
        "\n",
        f"## [{version}] - {date.today().isoformat()}\n",
        *body,
    ]

    base = f"https://github.com/{repository_slug()}/compare"
    unreleased_link = f"[unreleased]: {base}/{version}...HEAD"
    version_link = f"[{version}]: {base}/{previous_ref()}...{version}"
    text = "".join(lines)
    definition = re.compile(r"^\[unreleased\]:.*$", re.MULTILINE | re.IGNORECASE)
    if definition.search(text):
        text = definition.sub(f"{unreleased_link}\n{version_link}", text, count=1)
    else:
        if not text.endswith("\n"):
            text += "\n"
        text += f"\n{unreleased_link}\n{version_link}\n"

    changelog.write_text(text, encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main())
