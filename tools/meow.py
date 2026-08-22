#!/usr/bin/env python3
"""Мяуканье, синтезированное с нуля.

Готовый «бесплатный» звук из интернета брать не стал: у большинства таких
файлов лицензия всё-таки есть, просто её никто не открывает. Здесь звук мой
целиком, поэтому вопросов к нему нет.

Как оно устроено. Кошачий крик — это голос: пила из гортани, пропущенная
через рот. Высота идёт дугой (вверх и обратно вниз), а рот по дороге
перестраивается с «мя» на «у» — то есть две форманты съезжают вниз. Плюс
дрожь около двадцати герц, без неё выходит гудок, а не животное.

  python3 meow.py margelet_meow.ogg
"""
import subprocess
import sys

import numpy as np
import soundfile as sf

SR = 44100
DUR = 0.62


def formant(x, freq, bw, gain):
    """Резонанс: двухполюсный фильтр, считаю его вручную по разностному
    уравнению — так виднее, что происходит, чем через чужую библиотеку."""
    r = np.exp(-np.pi * bw / SR)
    theta = 2 * np.pi * freq / SR
    a1, a2 = -2 * r * np.cos(theta), r * r
    y = np.zeros_like(x)
    for n in range(2, len(x)):
        y[n] = x[n] - a1 * y[n - 1] - a2 * y[n - 2]
    return y * gain * (1 - r)


def main(out="margelet_meow.ogg"):
    n = int(SR * DUR)
    t = np.linspace(0, DUR, n, endpoint=False)
    k = t / DUR

    # высота: дуга от 380 к 620 и обратно к 300, плюс дрожь
    f0 = 380 + 240 * np.sin(np.pi * k ** 0.8) - 80 * k ** 2
    f0 *= 1 + 0.045 * np.sin(2 * np.pi * 19 * t)
    phase = 2 * np.pi * np.cumsum(f0) / SR
    # пила: у голоса гармоник много, чистая синусоида звучит свистком
    glottis = 2 * (phase / (2 * np.pi) % 1) - 1
    glottis *= 0.6 + 0.4 * np.sin(np.pi * k)

    # рот: «мя» -> «у», обе форманты съезжают вниз
    f1 = 860 - 400 * k
    f2 = 1900 - 900 * k
    voice = np.zeros(n)
    for f, bw, g in ((f1, 90, 1.0), (f2, 120, 0.7), (np.full(n, 2600.0), 200, 0.25)):
        voice += formant(glottis, float(np.mean(f)), bw, g)

    # немного придыхания в начале и в конце
    rng = np.random.default_rng(4)
    voice += rng.normal(0, 1, n) * 0.02 * np.exp(-8 * k) 

    env = np.minimum(1.0, np.minimum(k * 14, (1 - k) * 5)) ** 1.3
    y = voice * env
    y /= max(1e-9, np.abs(y).max()) / 0.9
    sf.write("meow.wav", y.astype(np.float32), SR)
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", "meow.wav",
                    "-c:a", "libvorbis", "-q:a", "4", out], check=True)
    print(f"{out}: {DUR:.2f} с")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "margelet_meow.ogg")
