#!/usr/bin/env python3
"""Checkstyle 지적을 PR 리뷰 코멘트로 남긴다.

- 리뷰 코멘트는 diff 안의 줄만 가리킬 수 있어, PR에서 새로 추가된 줄의 지적만 올린다.
- 같은 파일, 같은 줄, 같은 규칙의 코멘트가 이미 있으면 다시 올리지 않는다(재실행 중복 방지).
- GITHUB_TOKEN만 쓴다. 비밀값을 읽지 않는다.
"""
import json
import os
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

MARK = "<!-- checkstyle -->"
MAX_COMMENTS = 20
REPORTS = [
    Path("build/reports/checkstyle/main.xml"),
    Path("build/reports/checkstyle/test.xml"),
]


def gh_api(path, *args, paged=False):
    if not paged:
        out = subprocess.run(
            ["gh", "api", path, *args], capture_output=True, text=True, check=True
        )
        return json.loads(out.stdout or "null")
    items, page = [], 1
    while True:
        sep = "&" if "?" in path else "?"
        url = f"{path}{sep}per_page=100&page={page}"
        out = subprocess.run(
            ["gh", "api", url, *args], capture_output=True, text=True, check=True
        )
        batch = json.loads(out.stdout or "[]")
        if not batch:
            return items
        items.extend(batch)
        if len(batch) < 100:
            return items
        page += 1


def added_lines(repo, pr):
    """PR에서 새로 추가된 줄 번호를 파일별로 모은다."""
    result = {}
    for f in gh_api(f"repos/{repo}/pulls/{pr}/files", paged=True):
        patch = f.get("patch")
        if not patch:
            continue
        lines = set()
        new_no = 0
        for raw in patch.splitlines():
            if raw.startswith("@@"):
                head = raw.split("+", 1)[1].split("@@", 1)[0].strip()
                new_no = int(head.split(",", 1)[0]) - 1
            elif raw.startswith("+"):
                new_no += 1
                lines.add(new_no)
            elif raw.startswith("-"):
                continue
            else:
                new_no += 1
        result[f["filename"]] = lines
    return result


def findings():
    root_dir = Path.cwd()
    out = []
    for report in REPORTS:
        if not report.exists():
            continue
        for file_el in ET.parse(report).getroot().findall("file"):
            try:
                rel = str(Path(file_el.get("name")).resolve().relative_to(root_dir))
            except ValueError:
                continue
            for err in file_el.findall("error"):
                source = err.get("source", "")
                rule = source.rsplit(".", 1)[-1]
                if rule.endswith("Check"):
                    rule = rule[: -len("Check")]
                out.append(
                    {
                        "path": rel,
                        "line": int(err.get("line", "0")),
                        "rule": rule or "Checkstyle",
                        "message": err.get("message", "").strip(),
                    }
                )
    return out


def main():
    repo = os.environ["GITHUB_REPOSITORY"]
    pr = os.environ["PR_NUMBER"]
    head_sha = os.environ["HEAD_SHA"]

    found = findings()
    if not found:
        print("Checkstyle 지적 없음")
        summary("지적 없음", 0, 0, 0, 0)
        return

    diff = added_lines(repo, pr)
    existing = {
        (c.get("path"), c.get("line"), c.get("body", "").split("`")[1] if "`" in c.get("body", "") else "")
        for c in gh_api(f"repos/{repo}/pulls/{pr}/comments", paged=True)
        if MARK in (c.get("body") or "")
    }

    posted = skipped_outside = skipped_dup = 0
    for f in found:
        if f["line"] not in diff.get(f["path"], set()):
            skipped_outside += 1
            continue
        if (f["path"], f["line"], f["rule"]) in existing:
            skipped_dup += 1
            continue
        if posted >= MAX_COMMENTS:
            break
        body = f"{MARK}\n**Checkstyle** `{f['rule']}`\n\n{f['message']}"
        subprocess.run(
            [
                "gh", "api", f"repos/{repo}/pulls/{pr}/comments",
                "-f", f"body={body}",
                "-f", f"commit_id={head_sha}",
                "-f", f"path={f['path']}",
                "-F", f"line={f['line']}",
                "-f", "side=RIGHT",
            ],
            check=True,
            stdout=subprocess.DEVNULL,
        )
        posted += 1
        print(f"코멘트: {f['path']}:{f['line']} {f['rule']}")

    summary("지적 있음", len(found), posted, skipped_outside, skipped_dup)


def summary(head, total, posted, outside, dup):
    path = os.environ.get("GITHUB_STEP_SUMMARY")
    text = (
        f"### Checkstyle: {head}\n\n"
        f"- 지적 {total}건\n"
        f"- 리뷰 코멘트로 올림 {posted}건\n"
        f"- diff 밖이라 건너뜀 {outside}건\n"
        f"- 이미 올려 둔 코멘트라 건너뜀 {dup}건\n"
    )
    print(text)
    if path:
        with open(path, "a", encoding="utf-8") as fp:
            fp.write(text)


if __name__ == "__main__":
    sys.exit(main())
