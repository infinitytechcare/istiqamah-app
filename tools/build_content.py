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
    17: "বনি ইসরাইল", 18: "আল-কাহফ", 21: "আল-আম্বিয়া", 58: "আল-মুজাদালাহ", 43: "আয-যুখরুফ", 62: "আল-জুমুআ", 63: "আল-মুনাফিকুন", 19: "মারইয়াম", 20: "ত্বা-হা", 23: "আল-মুমিনুন", 29: "আল-আনকাবুত", 30: "আর-রুম",
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
    ap = argparse.ArgumentParser(description="আয়াত/হাদিস ও আযকারের content ফাইল বানায়")
    ap.add_argument("--quran-dir", required=True, help="quran-json প্যাকেটের dist ফোল্ডার")
    ap.add_argument("--adhkar-dir", help="@kazishariar/morning-evening-adhkar-data প্যাকেট ফোল্ডার")
    ap.add_argument("--hisn-dir", help="@kazishariar/hisnul-muslim-data প্যাকেট ফোল্ডার")
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
    if args.adhkar_dir and args.hisn_dir:
        return build_azkar(quran, args.adhkar_dir, args.hisn_dir)
    return 0



# =====================================================================
# আযকার: tools/azkar_src.json → assets/azkar.json
# =====================================================================
AZKAR_SRC = ROOT / "tools" / "azkar_src.json"
AZKAR_OUT = ROOT / "app" / "src" / "main" / "assets" / "azkar.json"
AZKAR_ATTR = "আরবি: হিসনুল মুসলিম (Dua Speed Reader ডেটা, CC BY 4.0) ও কুরআন (quranenc.com)"
SURAH_NAMES = {112: "সূরা ইখলাস", 113: "সূরা ফালাক", 114: "সূরা নাস", 2: "সূরা আল-বাকারা"}
AR_DIGITS = str.maketrans("0123456789", "٠١٢٣٤٥٦٧٨٩")


def build_azkar(quran, adhkar_dir, hisn_dir) -> int:
    src = json.loads(AZKAR_SRC.read_text(encoding="utf-8"))["items"]
    me = {x["id"]: x for x in json.loads((pathlib.Path(adhkar_dir) / "data.json").read_text(encoding="utf-8"))["data"]}
    hisn = {x["id"]: x for x in json.loads((pathlib.Path(hisn_dir) / "data.json").read_text(encoding="utf-8"))["data"]}
    by_id = {x["id"]: x for x in src}

    def verses(s, a1, a2):
        return [v for v in quran[s - 1]["verses"] if a1 <= v["id"] <= a2]

    def quran_text(it):
        if "quran" in it:
            s, a1, a2 = parse_ref(it["quran"])
            vs = verses(s, a1, a2)
            ar = " ".join(f"{v['text']} ﴿{str(v['id']).translate(AR_DIGITS)}﴾" for v in vs)
            bn = " ".join(v["translation"].strip() for v in vs)
            return ar, nfc(bn)
        ars, bns = [], []
        for s in it["quran_surahs"]:
            vs = quran[s - 1]["verses"]
            ars.append(" ".join(f"{v['text']} ﴿{str(v['id']).translate(AR_DIGITS)}﴾" for v in vs))
            bns.append(f"{SURAH_NAMES.get(s, s)}: " + " ".join(v["translation"].strip() for v in vs))
        return "\n\n".join(ars), nfc("\n\n".join(bns))

    out, order = [], {}
    for it in src:
        if "quran" in it or "quran_surahs" in it:
            arabic, meaning = quran_text(it)
        elif "me" in it:
            arabic, meaning = me[it["me"]]["arabic"], None
        elif "hisn" in it:
            arabic, meaning = hisn[it["hisn"]]["arabic"], None
        else:
            arabic, meaning = it["arabic"], None
        meaning = nfc(it.get("meaning")) or (nfc(by_id[it["meaning_from"]]["meaning"]) if "meaning_from" in it else None) or meaning
        pron = it.get("pron")
        if pron is None and "pron_from" in it:
            pron = by_id[it["pron_from"]]["pron"]
        for s in it["sets"]:
            order[s] = order.get(s, 0) + 1
            out.append({
                "id": it["id"] if len(it["sets"]) == 1 else f"{it['id']}_{s.lower()}",
                "set": s,
                "order": order[s],
                "title": it["title"],
                "arabic": arabic.strip(),
                "pron": nfc(pron or ""),
                "meaning": meaning or "",
                "count": it["count"],
                "ref": it["ref"],
                "grading": it["grading"],
                "note": nfc(it.get("note", "")),
                "reviewed_by": it.get("reviewed_by"),
            })
    AZKAR_OUT.write_text(json.dumps({"version": 1, "attribution": AZKAR_ATTR, "items": out}, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"{len(out)}টি আযকার আইটেম লেখা হয়েছে → {AZKAR_OUT.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
