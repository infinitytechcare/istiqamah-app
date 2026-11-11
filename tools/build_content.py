#!/usr/bin/env python3
"""
content_src.json + quran-json ডেটা → app/src/main/assets/content.json

আয়াতের আরবি (Noble Qur'an Encyclopedia-র উসমানি লিপি) ও বাংলা অনুবাদ (মুহিউদ্দীন খান)
quran-json npm প্যাকেট থেকে নেওয়া হয় — হাতে টাইপ করা হয় না, যাতে ভুল না ঢোকে।

ব্যবহার:
    npm pack quran-json && tar xzf quran-json-*.tgz      # একবার, যেকোনো ফোল্ডারে
    python3 tools/build_content.py --quran-dir path/to/package/dist
"""
import argparse
import json
import pathlib
import sys
import unicodedata

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "tools" / "content_src.json"
OUT = ROOT / "app" / "src" / "main" / "assets" / "content.json"

BN_DIGITS = str.maketrans("0123456789", "০১২৩৪৫৬৭৮৯")
BN_SOURCE = "মুহিউদ্দীন খান (tanzil.net, quran-json)"


def nfc(t):
    """বাংলা লেখা এক রকম ইউনিকোডে (যেমন য় দুইভাবে লেখা যায়)। আরবিতে হাত দিই না।"""
    return unicodedata.normalize("NFC", t) if t else t

# ব্যবহৃত সূরার বাংলা নাম — নতুন সূরা যোগ করলে এখানেও যোগ করুন
SURAH_BN = {
    2: "আল-বাকারা", 3: "আলে ইমরান", 4: "আন-নিসা", 13: "আর-রাদ", 14: "ইবরাহিম", 15: "আল-হিজর",
    17: "বনি ইসরাইল", 19: "মারইয়াম", 20: "ত্বা-হা", 23: "আল-মুমিনুন", 29: "আল-আনকাবুত", 30: "আর-রুম",
    33: "আল-আহযাব", 39: "আয-যুমার", 40: "আল-মুমিন", 41: "হা-মীম আস-সাজদা", 49: "আল-হুজুরাত", 50: "ক্বাফ",
    55: "আর-রহমান", 59: "আল-হাশর", 67: "আল-মুলক", 68: "আল-কলম", 70: "আল-মাআরিজ", 74: "আল-মুদ্দাসসির",
    75: "আল-কিয়ামাহ", 79: "আন-নাযিআত", 94: "আল-ইনশিরাহ", 98: "আল-বাইয়্যিনাহ", 99: "আয-যিলযাল", 107: "আল-মাউন",
}


def parse_ref(ref: str):
    s, a = ref.split(":")
    if "-" in a:
        a1, a2 = a.split("-")
    else:
        a1 = a2 = a
    return int(s), int(a1), int(a2)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--quran-dir", required=True, help="quran-json প্যাকেটের dist ফোল্ডার")
    args = ap.parse_args()

    quran = json.loads((pathlib.Path(args.quran_dir) / "quran_bn.json").read_text(encoding="utf-8"))
    src = json.loads(SRC.read_text(encoding="utf-8"))
    items = []

    for a in src["ayat"]:
        s, a1, a2 = parse_ref(a["ref"])
        if s not in SURAH_BN:
            print(f"SURAH_BN-এ সূরা {s}-এর নাম নেই", file=sys.stderr)
            return 1
        verses = [v for v in quran[s - 1]["verses"] if a1 <= v["id"] <= a2]
        if len(verses) != a2 - a1 + 1:
            print(f"{a['ref']}: আয়াত পাওয়া যায়নি", file=sys.stderr)
            return 1
        arabic = " ".join(f"{v['text']} ﴿{str(v['id']).translate(str.maketrans('0123456789', '٠١٢٣٤٥٦٧٨٩'))}﴾" for v in verses)
        bn = nfc(" ".join(v["translation"].strip() for v in verses))
        short = nfc(a.get("short")) or bn
        span = f"{a1}" if a1 == a2 else f"{a1}–{a2}"
        items.append({
            "id": f"q_{s}_{a1}" + ("" if a1 == a2 else f"_{a2}"),
            "type": "ayah",
            "tags": a["tags"],
            "ref_bn": f"সূরা {SURAH_BN[s]} {s}:{span}".translate(BN_DIGITS),
            "ref": {"surah": s, "ayah_from": a1, "ayah_to": a2},
            "arabic": arabic,
            "bn": bn,
            "bn_source": BN_SOURCE,
            "short_bn": short,
            "tone": a["tone"],
            "pair_with": a.get("pair_with"),
            "grading": None,
            "reviewed_by": a.get("reviewed_by"),
            "link": f"https://quran.com/{s}/{a1}" + ("" if a1 == a2 else f"-{a2}"),
        })

    for h in src["hadith"]:
        items.append({
            "id": h["id"],
            "type": "hadith",
            "tags": h["tags"],
            "ref_bn": h["ref_bn"],
            "ref": None,
            "arabic": "",
            "bn": nfc(h["bn"]),
            "bn_source": "বাংলা ভাবার্থ (রিভিউ প্রয়োজন)",
            "short_bn": nfc(h["short"]),
            "tone": h["tone"],
            "pair_with": h.get("pair_with"),
            "grading": h["grading"],
            "reviewed_by": h.get("reviewed_by"),
            "link": h.get("link"),
        })

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(
        json.dumps({"version": 1, "known_tags": src["known_tags"], "items": items}, ensure_ascii=False, indent=1),
        encoding="utf-8",
    )
    print(f"{len(items)}টি আইটেম লেখা হয়েছে → {OUT.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
