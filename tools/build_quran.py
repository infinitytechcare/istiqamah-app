#!/usr/bin/env python3
"""
পুরো কুরআনের পাঠ (আরবি + বাংলা অর্থ) আর পৃষ্ঠা/পারা/রুকু/সিজদার মানচিত্র → app/src/main/assets/quran.json

উৎস:
  - quran-json (risan/quran-json, CC BY-SA 4.0): আরবি পাঠ ও মুহিউদ্দীন খানের বাংলা অনুবাদ
  - quran-meta (MIT): মাদানি মুসহাফের ৬০৪ পৃষ্ঠা, ৩০ পারা, ৫৫৬ রুকু, সিজদার আয়াত
    (প্যাকেজের কোড চালানো হয় না — HafsLists ফাইল থেকে শুধু সংখ্যার তালিকা টেক্সট হিসেবে পড়া হয়)

ব্যবহার:
  python3 tools/build_quran.py --quran-dir <quran-json/dist> --meta-file <quran-meta/dist/HafsLists-*.js>
"""
import argparse
import json
import pathlib
import re
import sys
import unicodedata

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "app" / "src" / "main" / "assets" / "quran.json"

SURAH_BN = [
    "আল-ফাতিহা", "আল-বাকারা", "আলে ইমরান", "আন-নিসা", "আল-মায়িদা", "আল-আনআম", "আল-আরাফ", "আল-আনফাল", "আত-তাওবা", "ইউনুস",
    "হুদ", "ইউসুফ", "আর-রাদ", "ইবরাহিম", "আল-হিজর", "আন-নাহল", "বনি ইসরাইল", "আল-কাহফ", "মারইয়াম", "ত্বা-হা",
    "আল-আম্বিয়া", "আল-হাজ্জ", "আল-মুমিনুন", "আন-নূর", "আল-ফুরকান", "আশ-শুআরা", "আন-নামল", "আল-কাসাস", "আল-আনকাবুত", "আর-রুম",
    "লুকমান", "আস-সাজদা", "আল-আহযাব", "সাবা", "ফাতির", "ইয়াসিন", "আস-সাফফাত", "সোয়াদ", "আয-যুমার", "আল-মুমিন",
    "হা-মিম সাজদা", "আশ-শুরা", "আয-যুখরুফ", "আদ-দুখান", "আল-জাসিয়া", "আল-আহকাফ", "মুহাম্মাদ", "আল-ফাতহ", "আল-হুজুরাত", "কাফ",
    "আয-যারিয়াত", "আত-তুর", "আন-নাজম", "আল-কামার", "আর-রহমান", "আল-ওয়াকিয়া", "আল-হাদিদ", "আল-মুজাদালাহ", "আল-হাশর", "আল-মুমতাহিনা",
    "আস-সাফ", "আল-জুমুআ", "আল-মুনাফিকুন", "আত-তাগাবুন", "আত-তালাক", "আত-তাহরিম", "আল-মুলক", "আল-কালাম", "আল-হাক্কাহ", "আল-মাআরিজ",
    "নূহ", "আল-জিন", "আল-মুযযাম্মিল", "আল-মুদ্দাসসির", "আল-কিয়ামাহ", "আদ-দাহর", "আল-মুরসালাত", "আন-নাবা", "আন-নাযিআত", "আবাসা",
    "আত-তাকভির", "আল-ইনফিতার", "আল-মুতাফফিফিন", "আল-ইনশিকাক", "আল-বুরুজ", "আত-তারিক", "আল-আ'লা", "আল-গাশিয়াহ", "আল-ফাজর", "আল-বালাদ",
    "আশ-শামস", "আল-লাইল", "আদ-দুহা", "আলাম নাশরাহ", "আত-তীন", "আল-আলাক", "আল-কদর", "আল-বাইয়্যিনাহ", "আয-যিলযাল", "আল-আদিয়াত",
    "আল-কারিআহ", "আত-তাকাসুর", "আল-আসর", "আল-হুমাযাহ", "আল-ফিল", "কুরাইশ", "আল-মাউন", "আল-কাউসার", "আল-কাফিরুন", "আন-নাসর",
    "আল-লাহাব", "আল-ইখলাস", "আল-ফালাক", "আন-নাস",
]

# ডিজিটাল উৎসে অনুবাদের অংশ বাদ পড়েছে বলে জানা আয়াত — পাঠকে সতর্কতা দেখায় (আলেমের রিভিউ বাকি)
KNOWN_BN_ISSUES = {(7, 156), (11, 114), (25, 70)}


def nfc(s: str) -> str:
    return unicodedata.normalize("NFC", s)


def load_bn_fixes() -> dict:
    """tools/quran_bn_fixes.json — ডেটাসেটের ভাঙা বাংলা অক্ষরের হাতে-করা সংশোধন"""
    f = pathlib.Path(__file__).resolve().parent / "quran_bn_fixes.json"
    out = {}
    for s, a, bad, good in json.loads(f.read_text(encoding="utf-8"))["fixes"]:
        out.setdefault((s, a), []).append((bad, good))
    return out


def fix_bn(fixes: dict, s: int, a: int, text: str) -> str:
    text = nfc(text)
    for bad, good in fixes.get((s, a), []):
        bad, good = nfc(bad), nfc(good)
        if bad not in text:
            raise SystemExit(f"সংশোধন মেলেনি: {s}:{a} '{bad}'")
        text = text.replace(bad, good)
    return text


def meta_lists(path: pathlib.Path) -> dict:
    """HafsLists ফাইল থেকে নামের পরের প্রথম অ্যারের সংখ্যাগুলো পড়া (কোড চালানো ছাড়া)"""
    src = path.read_text(encoding="utf-8")

    def nums(name: str) -> list[int]:
        m = re.search(name + r"\s*:\s*\[", src)
        if not m:
            raise SystemExit(f"{name} পাওয়া যায়নি")
        i, depth, j = m.end(), 1, m.end()
        while depth:
            depth += {"[": 1, "]": -1}.get(src[j], 0)
            j += 1
        return [int(x) for x in re.findall(r"-?\d+", src[i:j - 1])]

    return {"pages": nums("PageList"), "juz": nums("JuzList"), "ruku": nums("RukuList"), "sajda": nums("SajdaList")}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--quran-dir", required=True)
    ap.add_argument("--meta-file", required=True)
    a = ap.parse_args()
    quran = json.loads((pathlib.Path(a.quran_dir) / "quran_bn.json").read_text(encoding="utf-8"))
    m = meta_lists(pathlib.Path(a.meta_file))
    fixes = load_bn_fixes()

    # তালিকাগুলোর প্রথমটা ফাঁকা (০) আর শেষটা সীমানা (৬২৩৭) — মাঝেরগুলোই শুরু
    pages, juz, ruku = m["pages"][1:-1], m["juz"][1:-1], m["ruku"][1:-1]
    if (len(pages), len(juz), len(ruku)) != (604, 30, 556):
        print(f"অপ্রত্যাশিত সংখ্যা: {len(pages)} পৃষ্ঠা, {len(juz)} পারা, {len(ruku)} রুকু", file=sys.stderr)
        return 1

    surahs, ar, bn, issues = [], [], [], []
    for s in quran:
        start = len(ar) + 1
        surahs.append([start, s["total_verses"], SURAH_BN[s["id"] - 1], s["name"], nfc(s["translation"]), 1 if s["type"] == "meccan" else 2])
        for v in s["verses"]:
            ar.append(v["text"])
            bn.append(nfc(fix_bn(fixes, s["id"], v["id"], v["translation"])))
            if (s["id"], v["id"]) in KNOWN_BN_ISSUES:
                issues.append(len(ar))
    if any("?" in t and re.search(r"\?\d{3,5};", t) for t in bn):
        print("এখনো ভাঙা অক্ষর আছে — tools/quran_bn_fixes.json দেখুন", file=sys.stderr)
        return 1
    if len(ar) != 6236:
        print(f"আয়াত {len(ar)}টি, ৬২৩৬ হওয়ার কথা", file=sys.stderr)
        return 1
    for lst, name in ((pages, "pages"), (juz, "juz"), (ruku, "ruku")):
        if lst != sorted(lst) or lst[0] != 1 or lst[-1] > 6236:
            print(f"{name} তালিকা ঠিক নেই", file=sys.stderr)
            return 1

    out = {
        "version": 1,
        "attribution": "আরবি ও বাংলা অনুবাদ (মুহিউদ্দীন খান): quran-json, CC BY-SA 4.0 · পৃষ্ঠা/পারা/রুকু: quran-meta (MIT), মাদানি মুসহাফ",
        "surahs": surahs,  # [প্রথম আয়াতের ক্রমিক, আয়াত সংখ্যা, বাংলা নাম, আরবি নাম, অর্থ, ১=মাক্কী ২=মাদানী]
        "pages": pages,    # প্রতি পৃষ্ঠার প্রথম আয়াতের ক্রমিক (১–৬২৩৬)
        "juz": juz,
        "ruku": ruku,
        "sajda": m["sajda"],
        "bn_issues": issues,
        "ar": ar,
        "bn": bn,
    }
    OUT.write_text(json.dumps(out, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(f"১১৪ সূরা, ৬২৩৬ আয়াত, ৬০৪ পৃষ্ঠা → {OUT.relative_to(ROOT)} ({OUT.stat().st_size // 1024} KB)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
