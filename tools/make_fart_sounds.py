"""Synthesizes the fart sounds for the Player Characters mod (no recordings involved).

Each sound is a low buzzing tone made of narrow pulses (like air pushed through a small gap), with a wobbling
pitch, a fluttering volume and a short hiss at the end. Needs numpy and soundfile:

    pip install numpy soundfile
    python make_fart_sounds.py ../src/main/resources/assets/playercharacters/sounds
"""
import sys
from pathlib import Path

import numpy as np
import soundfile as sf

RATE = 44100


def lowpass(signal, cutoff):
    """Two-pole low-pass filter."""
    out = np.zeros_like(signal)
    a = np.exp(-2 * np.pi * cutoff / RATE)
    y1 = y2 = 0.0
    for i, x in enumerate(signal):
        y1 = (1 - a) * x + a * y1
        y2 = (1 - a) * y1 + a * y2
        out[i] = y2
    return out


def fart(seconds, start_pitch, end_pitch, flutter_hz, seed, squeak=0.0):
    rng = np.random.default_rng(seed)
    n = int(seconds * RATE)
    t = np.arange(n) / RATE
    # pitch glides down with random wobble
    wobble = np.cumsum(rng.normal(0, 1, n)) / np.sqrt(RATE) * 6
    pitch = np.linspace(start_pitch, end_pitch, n) + wobble + squeak * 60 * np.sin(2 * np.pi * 3 * t) * (t < seconds * 0.4)
    phase = np.cumsum(pitch) / RATE
    # narrow pulses -> buzzy "brrr"
    frac = phase % 1.0
    pulses = np.exp(-frac * 18) - 0.06
    buzz = lowpass(pulses, 900) * 3.0
    # fluttering loudness
    flutter = 0.75 + 0.25 * np.sign(np.sin(2 * np.pi * flutter_hz * t + rng.uniform(0, 6))) * rng.uniform(0.6, 1.0)
    envelope = np.minimum(1, t / 0.015) * np.clip(1.15 - t / seconds, 0, 1) ** 0.7
    tone = buzz * flutter * envelope
    # breathy hiss that takes over at the end
    hiss = lowpass(rng.normal(0, 1, n), 1800) * 0.35
    hiss_env = np.clip((t - seconds * 0.55) / (seconds * 0.3), 0, 1) * np.clip(1 - t / seconds, 0, 1)
    sound = tone + hiss * (hiss_env + 0.05 * envelope)
    sound /= np.max(np.abs(sound)) + 1e-9
    return (sound * 0.9).astype(np.float32)


def main(out_dir):
    out = Path(out_dir)
    out.mkdir(parents=True, exist_ok=True)
    variants = [
        ("fart1", fart(0.75, 120, 82, 26, 1)),
        ("fart2", fart(1.05, 105, 70, 18, 2)),
        ("fart3", fart(0.55, 150, 115, 34, 3, squeak=1.0)),
    ]
    for name, samples in variants:
        sf.write(out / f"{name}.ogg", samples, RATE, format="OGG", subtype="VORBIS")
        print(name, f"{len(samples) / RATE:.2f}s")


if __name__ == "__main__":
    main(sys.argv[1])
