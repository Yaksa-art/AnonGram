# -*- coding: utf-8 -*-
# Правильная сборка: режем по всем границам и держим стопку открытых меток.
# На каждом отрезке закрываем лишние (в обратном порядке) и открываем новые.
def html(text, ents):
    usable = [e for e in ents if e[0] >= 0 and e[1] > 0 and e[0] + e[1] <= len(text)]
    order = sorted(usable, key=lambda e: (e[0], -e[1], e[2]))   # порядок вложения
    bounds = sorted({0, len(text)} | {e[0] for e in usable} | {e[0]+e[1] for e in usable})
    out, stack = [], []
    for k in range(len(bounds) - 1):
        a, b = bounds[k], bounds[k+1]
        want = [e for e in order if e[0] <= a and a < e[0] + e[1]]
        # сколько общего начала у стопки и нужного набора
        same = 0
        while same < len(stack) and same < len(want) and stack[same] is want[same]:
            same += 1
        while len(stack) > same:
            out.append("</%s>" % stack.pop()[2])
        for e in want[same:]:
            out.append("<%s>" % e[2])
            stack.append(e)
        out.append(text[a:b])
    while stack:
        out.append("</%s>" % stack.pop()[2])
    return "".join(out)

def well_formed(s):
    st, i = [], 0
    while i < len(s):
        if s[i] == '<':
            j = s.index('>', i); tag = s[i+1:j]
            if tag.startswith('/'):
                if not st or st.pop() != tag[1:]: return False
            else: st.append(tag)
            i = j + 1
        else: i += 1
    return not st

def plain(s):
    out, i = [], 0
    while i < len(s):
        if s[i] == '<': i = s.index('>', i) + 1
        else: out.append(s[i]); i += 1
    return "".join(out)

tests = [
    ("ABCDEF", [(0, 6, "b"), (2, 2, "i")]),
    ("ABCDEF", [(0, 3, "b"), (3, 3, "i")]),
    ("ABCDEF", [(0, 6, "b"), (0, 3, "i")]),
    ("ABCDEF", [(0, 6, "b"), (3, 3, "i")]),
    ("ABCDEF", [(0, 4, "b"), (2, 4, "i")]),
    ("ABCDEF", [(0, 6, "b"), (1, 4, "i"), (2, 2, "u")]),
    ("ABCDEF", [(1, 3, "b"), (0, 2, "i"), (4, 2, "u")]),
    ("ABCDEF", []),
]
bad = 0
for text, ents in tests:
    s = html(text, ents)
    ok = well_formed(s) and plain(s) == text
    bad += 0 if ok else 1
    print("ok " if ok else "ПЛОХО", s)
print("провалов:", bad)
