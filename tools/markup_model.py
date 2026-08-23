# -*- coding: utf-8 -*-
# Тот же формат, что в MargeletMarkup, отдельной реализацией — чтобы проверить
# порядок вставки меток и разбор, не собирая приложение.
OPEN, CLOSE, DIGIT, DIGITS = '︀', '︁', '︂', 14
def d(n): return chr(ord(DIGIT) + n)
def is_digit(c): return DIGIT <= c < chr(ord(DIGIT)+DIGITS)

def encode(text, spans, open_first):
    marks = []
    for (start, end, kind, value) in spans:
        marks.append((start, kind, value, 1))
        marks.append((end, 0, 0, 0))
    # по убыванию позиции; на равных — порядок задаётся флагом
    marks.sort(key=lambda m: (-m[0], -m[3] if open_first else m[3]))
    out = list(text)
    for (pos, kind, value, is_open) in marks:
        piece = (OPEN + d(kind) + d(value)) if is_open else CLOSE
        out[pos:pos] = list(piece)
    return "".join(out)

def parse(text):
    runs, stack = [], []
    i = 0
    while i < len(text):
        c = text[i]
        if c == OPEN and i+2 < len(text) and is_digit(text[i+1]) and is_digit(text[i+2]):
            stack.append((ord(text[i+1])-ord(DIGIT), ord(text[i+2])-ord(DIGIT), i+3))
            i += 3
            continue
        if c == CLOSE and stack:
            kind, value, start = stack.pop()
            if i > start:
                runs.append((kind, value, start, i))
        i += 1
    return runs

def visible(text, a, b):
    return "".join(ch for ch in text[a:b] if ch not in (OPEN, CLOSE) and not is_digit(ch))

tests = [
    ("привет мир", [(7, 10, 0, 9)], ["мир"]),
    ("ABCD",       [(0, 2, 0, 3), (2, 4, 1, 5)], ["AB", "CD"]),          # встык
    ("ABCD",       [(0, 4, 0, 3), (1, 3, 2, 0)], ["ABCD", "BC"]),        # вложенные
    ("ABCD",       [(0, 4, 0, 3), (0, 2, 1, 1)], ["ABCD", "AB"]),        # с одной точки
    ("hi",         [(0, 2, 2, 0)], ["hi"]),                              # весь текст
]
for open_first in (True, False):
    print("=== вставляем сначала", "открывающие" if open_first else "закрывающие")
    for text, spans, want in tests:
        enc = encode(text, spans, open_first)
        got = sorted(visible(enc, s, e) for (_k, _v, s, e) in parse(enc))
        ok = got == sorted(want)
        print("   ", "ok " if ok else "ПЛОХО", text, "->", got, "ждали", sorted(want))
