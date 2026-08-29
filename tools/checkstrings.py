# -*- coding: utf-8 -*-
"""Ищет неэкранированные апострофы в строковых ресурсах.

aapt называет эту ошибку «Invalid unicode escape sequence», и по названию её
не найти. Я ловил её уже трижды — руками, каждый раз после упавшей сборки.
Дешевле проверить перед сборкой.
"""
import io
import re
import sys

BAD = 0
for path in sys.argv[1:]:
    text = io.open(path, encoding="utf-8").read()
    for line in text.split("\n"):
        m = re.search(r'<string name="([^"]+)">(.*)</string>', line)
        if not m:
            continue
        value = m.group(2)
        # Экранированный апостроф — \' ; всё остальное аapt не примет.
        cleaned = value.replace("\\'", "")
        if "'" in cleaned:
            print("НЕ ЭКРАНИРОВАН апостроф:", path.split("/")[-2], m.group(1))
            BAD += 1
print("плохих строк:", BAD)
sys.exit(1 if BAD else 0)
