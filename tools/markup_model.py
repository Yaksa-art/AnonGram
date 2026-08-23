# -*- coding: utf-8 -*-
# Новый алфавит: невидимые знаки U+2060..U+2064, которых разбор эмодзи не трогает.
# Значения — троичные цифры: вид 2 разряда, значение 3 разряда.
OPEN, CLOSE = '⁠', '⁡'
TRITS = ['⁢', '⁣', '⁤']
KIND_TRITS, VALUE_TRITS = 2, 3

def enc_num(n, count):
    out = []
    for _ in range(count):
        out.append(TRITS[n % 3]); n //= 3
    return "".join(out)          # младший разряд первым

def dec_num(s):
    n, mul = 0, 1
    for ch in s:
        n += TRITS.index(ch) * mul; mul *= 3
    return n

def mark(kind, value):
    return OPEN + enc_num(kind, KIND_TRITS) + enc_num(value, VALUE_TRITS)

MARK_LEN = 1 + KIND_TRITS + VALUE_TRITS

def encode(text, spans):
    # (позиция, открывающая?, начало куска, конец куска, вид, значение)
    marks = []
    for (start, end, kind, value) in spans:
        marks.append((start, 1, start, end, kind, value))
        marks.append((end,   0, start, end, kind, value))
    # По убыванию позиции. На равной позиции: сначала открывающие (вставка в
    # одну точку переворачивает порядок). Среди открывающих — сначала короткий
    # кусок, он ляжет внутрь; среди закрывающих — сначала внешний, тогда
    # внутренний закроется раньше него.
    marks.sort(key=lambda m: (-m[0], -m[1], m[3] if m[1] else m[2]))
    out = list(text)
    for (pos, is_open, start, end, kind, value) in marks:
        out[pos:pos] = list(mark(kind, value) if is_open else CLOSE)
    return "".join(out)

def parse(text):
    runs, stack, i = [], [], 0
    while i < len(text):
        c = text[i]
        if c == OPEN and i + MARK_LEN <= len(text) and all(x in TRITS for x in text[i+1:i+MARK_LEN]):
            kind = dec_num(text[i+1:i+1+KIND_TRITS])
            value = dec_num(text[i+1+KIND_TRITS:i+MARK_LEN])
            stack.append((kind, value, i + MARK_LEN)); i += MARK_LEN; continue
        if c == CLOSE and stack:
            kind, value, start = stack.pop()
            if i > start: runs.append((kind, value, start, i))
        i += 1
    return runs

def visible(t, a, b):
    return "".join(ch for ch in t[a:b] if ch not in (OPEN, CLOSE) and ch not in TRITS)

tests = [
    ("ABCDEF", [(0, 6, 0, 1), (0, 2, 1, 2), (4, 6, 2, 3)],
        [("ABCDEF", 0, 1), ("AB", 1, 2), ("EF", 2, 3)]),
    ("ABCD", [(0, 2, 0, 1), (0, 4, 1, 2), (2, 4, 2, 3)],
        [("AB", 0, 1), ("ABCD", 1, 2), ("CD", 2, 3)]),
    ("привет мир", [(7, 10, 0, 9)], [("мир", 0, 9)]),
    ("ABCD", [(0, 2, 0, 3), (2, 4, 1, 5)], [("AB", 0, 3), ("CD", 1, 5)]),
    ("ABCD", [(0, 4, 0, 13), (1, 3, 2, 0)], [("ABCD", 0, 13), ("BC", 2, 0)]),
    ("ABCD", [(0, 4, 0, 3), (0, 2, 1, 1)], [("ABCD", 0, 3), ("AB", 1, 1)]),
    ("hi", [(0, 2, 2, 12)], [("hi", 2, 12)]),
]
bad = 0
for text, spans, want in tests:
    e = encode(text, spans)
    got = sorted((visible(e, s, en), k, v) for (k, v, s, en) in parse(e))
    ok = got == sorted(want)
    bad += 0 if ok else 1
    print("ok " if ok else "ПЛОХО", text, "->", got)
print("невидимых знаков на кусок:", MARK_LEN + 1, "| провалов:", bad)
