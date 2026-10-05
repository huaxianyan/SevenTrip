#!/usr/bin/env python3
"""扫描中文技术文案的排版与 AI 腔问题。

只报告，不改文件。判据见 SKILL.md 的「硬规则速查」与「AI 腔清单」。
Python 3.7 以上，无第三方依赖。

用法：
    python check_style.py <文件或目录>...
    python check_style.py docs/ src/ --ext md,kt
    python check_style.py --only-issues .
    python check_style.py --quiet .

参数：
    paths           要扫描的文件或目录，可以给多个。都不给时扫当前目录。
    --ext           逗号分隔的扩展名白名单，覆盖默认值。
    --only-issues   不输出文件统计，只列问题。
    --quiet         只输出一行结论。
"""

import argparse
import io
import os
import re
import sys

CJK = "\u4e00-\u9fff"
CJK_CLS = "[%s]" % CJK

DEFAULT_EXT = (".md", ".kt", ".kts", ".java", ".xml", ".properties", ".yml", ".yaml",
               ".txt", ".json", ".py", ".js", ".ts", ".html", ".css", ".toml", ".ini",
               ".sh", ".cs", ".axaml", ".xaml")
SKIP_DIR = {".git", ".gradle", ".kotlin", ".idea", "build", ".workbuddy", "node_modules",
            "target", "dist", "out", "venv", ".venv", "__pycache__", "Pods", "DerivedData",
            ".next", ".nuxt", "bin", "obj", "vendor"}

# 直接当成问题的 AI 腔词
AI_WORDS = (
    "随着", "在当今", "值得注意的是", "需要指出的是", "让我们来看看", "接下来我们",
    "总的来说", "综上所述", "总而言之", "希望对你有帮助", "如有问题欢迎交流",
    "不仅", "与其说", "答案很简单", "原因很简单", "关键是什么",
    "强大", "灵活", "无缝", "全面", "极致", "优雅", "轻松", "一站式",
    "赋能", "抓手", "颗粒度", "底层逻辑", "范式",
    "在某种程度上", "在一定情况下可能会",
)
# 存疑：在有些项目里是正式的业务或技术名称，需要人工判断
AI_SOFT_WORDS = ("链路", "沉淀", "对齐", "闭环", "维度", "打通")

RE_RULES = (
    ("中英未空格", re.compile(r"(?<![\\])%s[A-Za-z0-9]|[A-Za-z0-9]%s" % (CJK_CLS, CJK_CLS))),
    ("半角标点接中文", re.compile(r"%s[,;:!?]|[,;:!?]%s" % (CJK_CLS, CJK_CLS))),
    ("半角括号接中文", re.compile(r"%s\(|\)%s" % (CJK_CLS, CJK_CLS))),
    ("省略号写法", re.compile(r"\.\.\.|。。。")),
    ("中文里的感叹号", re.compile(r"%s！" % CJK_CLS)),
    ("进行+动词", re.compile(r"进行(?![中时曲性行])(?=%s)" % CJK_CLS)),
    ("数字紧贴单位", re.compile(
        r"(?<![A-Za-z0-9])\d+(?:\.\d+)?(?:ms|fps|Hz|kHz|MHz|GHz|px|dp|sp|dpi"
        r"|KiB|MiB|GiB|KB|MB|GB|TB|Gbps|Mbps)(?![A-Za-z0-9])")),
)
CJK_ONE = re.compile(CJK_CLS)
INLINE_CODE_RX = re.compile(r"`+[^`]+`+")
URL_HINT = ("http://", "https://", "mailto:")
# 这几类规则在含 URL 的行上误报太多，跳过
URL_SENSITIVE = ("中英未空格", "半角标点接中文", "半角括号接中文")
# 引号风格：默认直角引号，弯引号报问题；--allow-curly-quotes 关闭
CURLY_QUOTES = re.compile(r"[\u201c\u201d\u2018\u2019]")
# 规范文档里用反例解释规则，这些行不参与判定
REVERSAL_RE = re.compile(r"^\s*(?:[-*+]\s*)?差[：:]")
# 代码文件只看注释行，免得字符串字面量与格式串被当成文案问题
CODE_EXT = {".kt", ".kts", ".java", ".py", ".js", ".ts", ".cs", ".sh", ".c", ".cpp",
            ".h", ".hpp", ".go", ".rs", ".rb", ".swift", ".m", ".mm", ".php", ".lua"}
COMMENT_MARKS = ("//", "#", "*", "/*", "<!--", "--")
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


def strip_quoted(line):
    """把引号与行内代码里的内容换成等长空格。

    规范类文件会引用这些词当作示例，剥掉引用内容才不会把示例本身当成问题。
    只替换内容，不删字符，长度不变，定位照旧。
    """

    def blank(match):
        return " " * len(match.group(0))

    out = re.sub(r"`[^`]*`", blank, line)
    out = re.sub(r"\u300c[^\u300d]*\u300d", blank, out)
    out = re.sub(r"\u201c[^\u201d]*\u201d", blank, out)
    return out


def tight_inline_code(line):
    """行内代码紧贴中文：中文与 `` `code` `` 之间应有半角空格。"""
    for m in INLINE_CODE_RX.finditer(line):
        left = line[m.start() - 1] if m.start() > 0 else ""
        right = line[m.end()] if m.end() < len(line) else ""
        if CJK_ONE.fullmatch(left) or CJK_ONE.fullmatch(right):
            return True
    return False


def curly_quote_hit(line):
    """弯引号检查。先把行内代码剥掉，被代码包住的示例不算问题。"""
    return bool(CURLY_QUOTES.search(INLINE_CODE_RX.sub(" ", line)))


def scan(path, root, allow_curly=False):
    """扫描一个文件，返回问题行与统计行。"""
    rel = os.path.relpath(path, root).replace("\\", "/")
    try:
        text = io.open(path, encoding="utf-8").read()
    except (IOError, UnicodeDecodeError):
        return [], None
    if not re.search(CJK_CLS, text):
        return [], None

    comments_only = os.path.splitext(path)[1].lower() in CODE_EXT
    rows = []
    in_fence = False
    in_front_matter = False
    for i, line in enumerate(text.splitlines(), 1):
        s = line.strip()
        # frontmatter 是机器可读元数据，不是正文，不参与任一判定
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
        if in_fence:
            continue
        if comments_only and not any(s.startswith(mark) for mark in COMMENT_MARKS):
            continue
        if REVERSAL_RE.match(s):
            continue
        # 引号与行内代码里的内容不参与判定，规范文件里的示例才不会被当成问题
        body = strip_quoted(line)
        has_url = any(h in line for h in URL_HINT)
        for name, rx in RE_RULES:
            if has_url and name in URL_SENSITIVE:
                continue
            m = rx.search(body)
            if m:
                rows.append((rel, i, name, m.group(0), s[:100]))
        for w in AI_WORDS:
            if w in body:
                rows.append((rel, i, "AI腔:" + w, w, s[:100]))
        for w in AI_SOFT_WORDS:
            if w in body:
                rows.append((rel, i, "存疑:" + w, w, s[:100]))
        if not allow_curly and curly_quote_hit(line):
            rows.append((rel, i, "弯引号", line.strip()[:40], s[:100]))
        if not has_url and tight_inline_code(line):
            rows.append((rel, i, "中文紧贴行内代码", line.strip()[:40], s[:100]))
        if "\u2014\u2014" in body:
            rows.append((rel, i, "破折号", "\u2014\u2014", s[:100]))
        # 长句与一逗到底只看正文行，跳过行注释与表格行；
        # 用 body 而不是 line，引号与行内代码里的示例不参与计数
        if not s.startswith("//") and not s.startswith("*") and not s.startswith("|"):
            for sent in re.split(r"[。！？；]", body):
                n_cjk = len(re.findall(CJK_CLS, sent))
                if n_cjk >= 60:
                    rows.append((rel, i, "长句(%d字)" % n_cjk, sent[:30], s[:100]))
                if sent.count("，") >= 5:
                    rows.append((rel, i, "一逗到底(%d逗号)" % sent.count("，"), "，", s[:100]))

    # frontmatter 不参与全文件级统计
    prose_text = mask_front_matter(text)
    # 破折号总数只统计正文里的，文档里用行内代码写的规则示例不算
    n_dash = len(re.findall("\u2014\u2014", INLINE_CODE_RX.sub(" ", prose_text)))
    if n_dash > 2:
        rows.append((rel, 0, "破折号总数", "x%d" % n_dash, ""))
    lower = rel.lower()
    if lower.endswith("readme.md") or "/release-notes/" in lower or "/releases/" in lower:
        n = prose_text.count("；")
        if n:
            rows.append((rel, 0, "分号(该文件禁用)", "x%d" % n, ""))

    straight = len(re.findall("[\u300c\u300d]", prose_text))
    curly = len(re.findall("[\u201c\u201d]", prose_text))
    stat = (rel, 0, "统计", "\u884c\u5c3e=%s \u5f15\u53f7=\u300c\u300dx%d \u201c\u201dx%d \u7834\u6298\u53f7=%d \u5206\u53f7=%d" % (
        "CRLF" if "\r\n" in text else "LF", straight, curly, n_dash, prose_text.count("；")), "")
    return rows, stat


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
    parser.add_argument("--only-issues", action="store_true", help="hide the per-file stats")
    parser.add_argument("--allow-curly-quotes", action="store_true",
                        help="do not report curly quotes (project uses them)")
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

    issues, stats = [], []
    for path in collect_files(paths, exts):
        rows, stat = scan(path, root, args.allow_curly_quotes)
        issues.extend(rows)
        if stat:
            stats.append(stat)

    if args.quiet:
        print("scanned %d file(s), %d issue(s)" % (len(stats), len(issues)))
        return 0

    if not args.only_issues:
        print("== file stats ==")
        for r in sorted(stats):
            print("  %-58s %s" % (r[0], r[3]))
        print()

    print("== %d issue(s) in %d file(s) ==" % (len(issues), len(stats)))
    for r in issues:
        print("  %s:%s [%s] %r | %s" % (r[0], r[1], r[2], r[3], r[4]))
    return 1 if issues else 0


if __name__ == "__main__":
    raise SystemExit(main())
