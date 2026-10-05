#!/usr/bin/env python3
"""中文排版硬规则门禁：只判确定性问题，可以接到 CI 上。

与 check_style.py 的分工：
    check_style.py      启发式扫描，管长句、AI 腔、破折号密度这些需要人看的项。
    check_typography.py 确定性判据，只报「一定错」的排版，适合当门禁。

检查项：
    弯引号（项目用直角引号时） / 中文紧贴行内代码 / 超链接紧贴中文 /
    数字紧贴单位 / 全角字母数字 / 重复标点 / 省略号写法 /
    半角标点接中文 / 中英未空格 / README 与发布说明里的分号 / 列表项末尾分号

只报告，不改文件。Python 3.7 以上，无第三方依赖。

用法：
    python check_typography.py README.md docs/
    python check_typography.py --quotes any .          # 项目用弯引号
    python check_typography.py --allow-tight-inline-code .
"""

import argparse
import html
import io
import os
import re
import sys

CJK = "\u3400-\u9fff"
CJK_CLS = "[%s]" % CJK
CJK_RE = re.compile(CJK_CLS)

DEFAULT_EXT = (".md", ".markdown", ".txt", ".xml", ".properties", ".yml", ".yaml",
               ".kt", ".kts", ".java", ".py", ".js", ".ts", ".cs", ".html", ".axaml",
               ".xaml", ".go", ".rs", ".swift", ".sh")
SKIP_DIR = {".git", ".gradle", ".kotlin", ".idea", "build", ".workbuddy", "node_modules",
            "target", "dist", "out", "venv", ".venv", "__pycache__", "Pods", "DerivedData",
            ".next", ".nuxt", "bin", "obj", "vendor"}
CODE_EXT = {".kt", ".kts", ".java", ".py", ".js", ".ts", ".cs", ".go", ".rs", ".swift",
            ".sh", ".c", ".cpp", ".h", ".hpp", ".rb", ".php", ".lua", ".m", ".mm"}
COMMENT_MARKS = ("//", "#", "*", "/*", "<!--", "--")

INLINE_CODE_RE = re.compile(r"`+[^`]+`+")
LINK_TARGET_RE = re.compile(r"\]\([^)]*\)")
HTML_TAG_RE = re.compile(r"<[^>]+>")
XML_TEXT_RE = re.compile(r">([^<]+)<")
LIST_RE = re.compile(r"^\s*(?:[-*+] |\d+[.)] )")

CURLY_QUOTES_RE = re.compile(r"[\u201c\u201d\u2018\u2019]")
TIGHT_CJK_LATIN_RE = re.compile(rf"{CJK_CLS}[A-Za-z0-9]|[A-Za-z0-9]{CJK_CLS}")
TIGHT_UNIT_RE = re.compile(
    r"(?<![A-Za-z0-9])\d+(?:\.\d+)?(?:ms|fps|Hz|kHz|MHz|GHz|px|dp|sp|dpi"
    r"|KiB|MiB|GiB|KB|MB|GB|TB|Gbps|Mbps)(?![A-Za-z0-9])")
FULLWIDTH_ALNUM_RE = re.compile(r"[Ａ-Ｚａ-ｚ０-９]")
REPEATED_PUNCT_RE = re.compile(r"[！？?!]{2,}")
ASCII_PUNCT_AFTER_CJK_RE = re.compile(rf"{CJK_CLS}[,;:!?]|[,;:!?]{CJK_CLS}")
HALFWIDTH_BRACKET_RE = re.compile(rf"{CJK_CLS}\(|\){CJK_CLS}")
SINGLE_ELLIPSIS_RE = re.compile(r"(?<!…)…(?!…)")
ELLIPSIS_WRONG_RE = re.compile(r"\.\.\.|。。。")
LINK_LEFT_RE = re.compile(rf"{CJK_CLS}!?\[[^\]]+\]\([^)]+\)")
LINK_RIGHT_RE = re.compile(r"\]\([^)]+\){CJK_CLS}")
URL_HINT = ("http://", "https://", "mailto:")
# 规范文档里用反例解释规则，这些行不参与判定
REVERSAL_RE = re.compile(r"^\s*(?:[-*+]\s*)?差[：:]")
# frontmatter 是机器可读元数据，不是正文
FRONT_MATTER_RE = re.compile(r"\A---\r?\n.*?\r?\n---\r?\n", re.S)


def mask_front_matter(text):
    """把开头的 frontmatter 换成等长空白，只留正文。

    不删字符，行号不偏移，报告里的定位照旧。
    """

    def blank(match):
        return re.sub(r"[^\n]", " ", match.group(0))

    return FRONT_MATTER_RE.sub(blank, text, count=1)


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


def mask_inline_code(line):
    return INLINE_CODE_RE.sub(" ", line)


def mask_prose(line):
    """把行内代码、链接目标、HTML 标签、引号里的引用内容换成空格，只留下正文。

    引号里的内容经常是规则自己的示例（例如「？？！！」），不剥掉会把示例当成问题。
    """
    out = INLINE_CODE_RE.sub(" ", line)
    out = LINK_TARGET_RE.sub("] ", out)
    out = HTML_TAG_RE.sub(" ", out)
    out = re.sub(r"\u300c[^\u300d]*\u300d", " ", out)
    out = re.sub(r"\u201c[^\u201d]*\u201d", " ", out)
    return out


def tight_inline_code(line):
    for m in INLINE_CODE_RE.finditer(line):
        left = line[m.start() - 1] if m.start() > 0 else ""
        right = line[m.end()] if m.end() < len(line) else ""
        if CJK_RE.fullmatch(left) or CJK_RE.fullmatch(right):
            return True
    return False


def segments_for(path, line, fenced):
    ext = os.path.splitext(path)[1].lower()
    if ext in (".md", ".markdown"):
        return [] if fenced else [line]
    if ext == ".xml":
        return [re.sub(r"\\[nrt]", " ", html.unescape(p)) for p in XML_TEXT_RE.findall(line)]
    if ext in CODE_EXT:
        s = line.strip()
        return [line] if any(s.startswith(mark) for mark in COMMENT_MARKS) else []
    return [line]


def scan(path, root, opts):
    rel = os.path.relpath(path, root).replace("\\", "/")
    try:
        text = io.open(path, encoding="utf-8").read()
    except (IOError, UnicodeDecodeError):
        return []
    if not CJK_RE.search(text):
        return []

    # frontmatter 是机器可读元数据，不是正文，不参与任一判定
    text = mask_front_matter(text)

    ext = os.path.splitext(path)[1].lower()
    is_markdown = ext in (".md", ".markdown")
    lower = rel.lower()
    no_semicolon = lower.endswith("readme.md") or "/release-notes/" in lower \
        or "/releases/" in lower

    rows = []
    in_fence = False
    for i, line in enumerate(text.splitlines(), 1):
        if is_markdown and re.match(r"^\s*(```|~~~)", line):
            in_fence = not in_fence
            continue
        if in_fence:
            continue
        if line.strip() in ("---", "+++") or line.strip().startswith(":::"):
            continue
        if REVERSAL_RE.match(line):
            continue

        has_url = any(h in line for h in URL_HINT)
        report = lambda name, detail="": rows.append((rel, i, name, detail))

        for segment in segments_for(path, line, in_fence):
            if not CJK_RE.search(segment):
                continue
            prose = mask_prose(segment)
            if opts.quotes == "straight" and CURLY_QUOTES_RE.search(mask_inline_code(segment)):
                report("弯引号", segment.strip()[:60])
            if REPEATED_PUNCT_RE.search(prose):
                report("重复标点", segment.strip()[:60])
            if ASCII_PUNCT_AFTER_CJK_RE.search(prose):
                report("半角标点接中文", segment.strip()[:60])
            if HALFWIDTH_BRACKET_RE.search(prose):
                report("半角括号接中文", segment.strip()[:60])
            if SINGLE_ELLIPSIS_RE.search(prose) or ELLIPSIS_WRONG_RE.search(prose):
                report("省略号写法", segment.strip()[:60])
            if FULLWIDTH_ALNUM_RE.search(prose):
                report("全角字母数字", segment.strip()[:60])
            if has_url:
                continue
            if TIGHT_CJK_LATIN_RE.search(prose):
                report("中英未空格", segment.strip()[:60])
            if TIGHT_UNIT_RE.search(prose):
                report("数字紧贴单位", segment.strip()[:60])

        if not is_markdown:
            continue
        if not opts.allow_tight_inline_code and not has_url and tight_inline_code(line):
            report("中文紧贴行内代码", line.strip()[:60])
        if LINK_LEFT_RE.search(line) or LINK_RIGHT_RE.search(line):
            report("超链接紧贴中文", line.strip()[:60])
        if no_semicolon and "；" in mask_inline_code(line):
            report("分号(该文件禁用)", line.strip()[:60])
        if LIST_RE.match(line) and line.rstrip().endswith("；") and not no_semicolon:
            report("列表项末尾分号", line.strip()[:60])
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
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("paths", nargs="*", default=["."],
                        help="files or directories, default the current directory")
    parser.add_argument("--ext", help="comma separated extension list, overrides the default")
    parser.add_argument("--quotes", choices=("straight", "any"), default="straight",
                        help="straight: report curly quotes (default); any: do not check")
    parser.add_argument("--allow-tight-inline-code", action="store_true",
                        help="allow Chinese to touch inline code without a space")
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
        rows.extend(scan(path, root, args))

    if args.quiet:
        print("typography: %d issue(s)" % len(rows))
        return 1 if rows else 0

    if not rows:
        print("typography contract verified")
        return 0
    print("typography contract failed: %d issue(s)" % len(rows))
    for rel, line, name, detail in rows:
        print("  %s:%d: [%s] %s" % (rel, line, name, detail))
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
