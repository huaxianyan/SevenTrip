#!/usr/bin/env python3
"""按句子长度规则扫描中文文案。

- 整句（按 。！？ 切分）不超过 100 个汉字。
- 句内被逗号、顿号、冒号、分号分开的每一截不超过 30 个汉字。

只报告，不改文件。Python 3.7 以上，无第三方依赖。

用法：
    python check_clauses.py <文件或目录>...
    python check_clauses.py docs/ --max-clause 30
    python check_clauses.py . --quiet
"""

import argparse
import io
import os
import re
import sys

CJK = "\u4e00-\u9fff"
CJK_CLS = "[%s]" % CJK
SENT_SPLIT = re.compile(r"[。！？]")
CLAUSE_SPLIT = re.compile(r"[，、：；]")

DEFAULT_EXT = (".md", ".kt", ".kts", ".java", ".xml", ".properties", ".yml", ".yaml",
               ".txt", ".py", ".js", ".ts", ".html", ".cs", ".axaml", ".xaml")
SKIP_DIR = {".git", ".gradle", ".kotlin", ".idea", "build", ".workbuddy", "node_modules",
            "target", "dist", "out", "venv", ".venv", "__pycache__", "Pods", "DerivedData",
            ".next", ".nuxt", "bin", "obj", "vendor"}

# 代码文件只看注释行，免得字符串字面量与格式串被当成文案
CODE_EXT = {".kt", ".kts", ".java", ".py", ".js", ".ts", ".cs", ".sh", ".c", ".cpp",
            ".h", ".hpp", ".go", ".rs", ".rb", ".swift", ".m", ".mm", ".php", ".lua"}
COMMENT_MARKS = ("//", "#", "*", "/*", "<!--", "--")
# 规范文档里用反例解释规则，这些行不参与统计
REVERSAL_RE = re.compile(r"^\s*(?:[-*+]\s*)?差[：:]")


def mask_examples(line):
    """把行内代码、引号、HTML 标签与链接目标换成等长空格，只留下正文。

    规范文档会用引号或行内代码引用示例，剥掉后示例本身不会被当成超长句。
    只替换内容，不删字符（链接文字除外），长度不变。
    """

    def blank(match):
        return " " * len(match.group(0))

    out = re.sub(r"`[^`]*`", blank, line)
    out = re.sub(r"\u300c[^\u300d]*\u300d", blank, out)
    out = re.sub(r"\u201c[^\u201d]*\u201d", blank, out)
    out = re.sub(r"<[^>]+>", blank, out)
    # 链接只保留文字，目标里的路径与参数不参与计数
    out = re.sub(r"\[([^\]]*)\]\([^)]*\)", r"\1", out)
    return out


def use_utf8_output():
    """把输出固定成 UTF-8。

    Windows 控制台默认 GBK，详情里的间隔号、emoji 这类字符打印时会抛
    UnicodeEncodeError，把扫描器自己搞挂，而退出码仍是非零，容易被当成文案有问题。
    errors 用 replace 兜底，个别字符编不出来就退化成问号，不中断扫描。
    """
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8", errors="replace")
        except (AttributeError, ValueError, OSError):
            pass


def cjk_len(text):
    return len(re.findall(CJK_CLS, text))


def scan(path, root, max_sentence, max_clause):
    rel = os.path.relpath(path, root).replace("\\", "/")
    try:
        text = io.open(path, encoding="utf-8").read()
    except (IOError, UnicodeDecodeError):
        return []
    if not re.search(CJK_CLS, text):
        return []

    comments_only = os.path.splitext(path)[1].lower() in CODE_EXT
    rows = []
    in_fence = False
    in_front_matter = False
    for i, line in enumerate(text.splitlines(), 1):
        s = line.strip()
        if i == 1 and s == "---":
            in_front_matter = True
            continue
        if in_front_matter:
            if s == "---":
                in_front_matter = False
            continue
        if s.startswith("```"):
            in_fence = not in_fence
            continue
        if in_fence or "http://" in line or "https://" in line:
            continue
        if comments_only and not any(s.startswith(mark) for mark in COMMENT_MARKS):
            continue
        if REVERSAL_RE.match(s):
            continue
        if s.startswith("//") or s.startswith("*") or s.startswith("|"):
            continue
        # 行内代码与引号里的内容不参与计数
        body = mask_examples(line)
        for sent in SENT_SPLIT.split(body):
            n = cjk_len(sent)
            if n > max_sentence:
                rows.append((rel, i, "整句%d字" % n, sent.strip()[:90]))
            for clause in CLAUSE_SPLIT.split(sent):
                cn = cjk_len(clause)
                if cn > max_clause:
                    rows.append((rel, i, "分截%d字" % cn, clause.strip()[:90]))
    return rows


def collect_files(paths, exts):
    files = []
    for item in paths:
        if os.path.isfile(item):
            if os.path.splitext(item)[1].lower() in exts:
                files.append(item)
            continue
        for dirpath, dirnames, filenames in os.walk(item):
            dirnames[:] = sorted(d for d in dirnames if d not in SKIP_DIR)
            for fn in sorted(filenames):
                if os.path.splitext(fn)[1].lower() in exts:
                    files.append(os.path.join(dirpath, fn))
    return files


def main():
    use_utf8_output()
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("paths", nargs="*", default=["."])
    parser.add_argument("--ext", help="comma separated extension list, overrides the default")
    parser.add_argument("--max-sentence", type=int, default=100, help="max CJK chars per sentence")
    parser.add_argument("--max-clause", type=int, default=30, help="max CJK chars per clause")
    parser.add_argument("--quiet", action="store_true", help="print a single summary line")
    args = parser.parse_args()

    if args.ext:
        exts = {("." + e.strip().lstrip(".")).lower() for e in args.ext.split(",") if e.strip()}
    else:
        exts = set(DEFAULT_EXT)

    paths = args.paths or ["."]
    for p in paths:
        if not os.path.exists(p):
            print("path does not exist: %s" % p, file=sys.stderr)
            return 2

    root = paths[0] if len(paths) == 1 else os.path.commonpath(
        [os.path.abspath(p) for p in paths])
    if os.path.isfile(root):
        root = os.path.dirname(root)

    rows = []
    for path in collect_files(paths, exts):
        rows.extend(scan(path, root, args.max_sentence, args.max_clause))

    if args.quiet:
        print("%d hit(s)" % len(rows))
        return 0

    print("%d hit(s)" % len(rows))
    by_file = {}
    for r in rows:
        by_file[r[0]] = by_file.get(r[0], 0) + 1
    for name, count in sorted(by_file.items(), key=lambda kv: (-kv[1], kv[0])):
        print("  %-58s %d" % (name, count))
    print()
    for r in rows:
        print("%s:%d [%s] %s" % (r[0], r[1], r[2], r[3]))
    return 1 if rows else 0


if __name__ == "__main__":
    raise SystemExit(main())
