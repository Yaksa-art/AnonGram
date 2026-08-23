# -*- coding: utf-8 -*-
"""Собирает docs/plugins.html из docs/plugins.md.

Держать две одинаковых страницы руками — гарантированно развести их через
месяц. Источник один, markdown; html из него собирается этим скриптом.

Разметка поддержана ровно та, что встречается в plugins.md: заголовки,
абзацы, списки, таблицы, блоки кода, `моноширинный`, **жирный**, ссылки.
"""

import html
import re
import sys

HEAD = """<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Плагины Margelet</title>
<style>
:root { color-scheme: light dark; --bg:#fff; --fg:#1c1c1e; --dim:#6e6e73;
        --line:#e3e3e6; --code:#f5f5f7; --link:#1b7f4c; }
@media (prefers-color-scheme: dark) {
  :root { --bg:#131417; --fg:#e9e9ec; --dim:#9a9aa0;
          --line:#2a2c31; --code:#1c1e22; --link:#54c98a; }
}
* { box-sizing: border-box; }
body { margin:0 auto; padding:32px 20px 80px; max-width:720px; background:var(--bg);
       color:var(--fg); font:16px/1.6 -apple-system, "Segoe UI", Roboto, sans-serif; }
h1 { font-size:30px; margin:0 0 24px; letter-spacing:-.02em; }
h2 { font-size:20px; margin:40px 0 12px; letter-spacing:-.01em; }
p, li { color:var(--fg); }
a { color:var(--link); }
code { background:var(--code); padding:2px 5px; border-radius:5px;
       font:14px/1.5 ui-monospace, SFMono-Regular, Menlo, monospace; }
pre { background:var(--code); padding:14px 16px; border-radius:10px; overflow-x:auto; }
pre code { background:none; padding:0; }
table { border-collapse:collapse; width:100%; margin:16px 0; display:block; overflow-x:auto; }
td, th { border-bottom:1px solid var(--line); padding:8px 10px; text-align:left;
         vertical-align:top; }
th { color:var(--dim); font-weight:600; font-size:14px; }
blockquote { margin:0; padding-left:14px; border-left:3px solid var(--line); color:var(--dim); }
footer { margin-top:56px; color:var(--dim); font-size:14px; }
</style>
"""


def inline(text):
    text = html.escape(text)
    text = re.sub(r"`([^`]+)`", r"<code>\1</code>", text)
    text = re.sub(r"\*\*([^*]+)\*\*", r"<strong>\1</strong>", text)
    text = re.sub(r"\[([^\]]+)\]\(([^)]+)\)", r'<a href="\2">\1</a>', text)
    return text


def convert(source):
    out = []
    lines = source.split("\n")
    i = 0
    while i < len(lines):
        line = lines[i]
        if line.startswith("```"):
            i += 1
            code = []
            while i < len(lines) and not lines[i].startswith("```"):
                code.append(html.escape(lines[i]))
                i += 1
            out.append("<pre><code>" + "\n".join(code) + "</code></pre>")
        elif line.startswith("## "):
            out.append("<h2>" + inline(line[3:]) + "</h2>")
        elif line.startswith("# "):
            out.append("<h1>" + inline(line[2:]) + "</h1>")
        elif line.startswith("|"):
            rows = []
            while i < len(lines) and lines[i].startswith("|"):
                rows.append([c.strip() for c in lines[i].strip("|").split("|")])
                i += 1
            i -= 1
            # Вторая строка таблицы — разделитель, её выбрасываем; первая
            # становится шапкой, даже если ячейки в ней пустые.
            head, body = rows[0], rows[2:]
            table = ["<table><tr>"]
            table += ["<th>" + inline(c) + "</th>" for c in head]
            table.append("</tr>")
            for row in body:
                table.append("<tr>" + "".join(
                    "<td>" + inline(c) + "</td>" for c in row) + "</tr>")
            table.append("</table>")
            out.append("".join(table))
        elif line.strip() == "":
            pass
        else:
            # Первую строку абзаца берём всегда: она может начинаться и с
            # `кода`, а проверка на служебный знак остановила бы разбор на
            # месте — и цикл никуда бы не двинулся.
            block = [lines[i].strip()]
            i += 1
            while (i < len(lines) and lines[i].strip()
                   and lines[i][0] not in "#|" and not lines[i].startswith("```")):
                block.append(lines[i].strip())
                i += 1
            i -= 1
            out.append("<p>" + inline(" ".join(block)) + "</p>")
        i += 1
    return HEAD + "\n".join(out) + "\n"


if __name__ == "__main__":
    src, dst = sys.argv[1], sys.argv[2]
    with open(src, encoding="utf-8") as f:
        page = convert(f.read())
    with open(dst, "w", encoding="utf-8") as f:
        f.write(page)
    print(dst, len(page), "байт")
