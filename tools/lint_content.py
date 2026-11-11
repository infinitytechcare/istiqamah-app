#!/usr/bin/env python3
"""
content.json যাচাই — CI-তে প্রতিটা বিল্ডে চলে।

    python3 tools/lint_content.py            # সমস্যা থাকলে ব্যর্থ; রিভিউ বাকি থাকলে শুধু সতর্কতা
    python3 tools/lint_content.py --strict   # পাবলিক রিলিজের আগে: রিভিউ বাকি থাকলেও ব্যর্থ
"""
import json
import pathlib
import sys
import unicodedata

ROOT = pathlib.Path(__file__).resolve().parent.parent
CONTENT = ROOT / "app" / "src" / "main" / "assets" / "content.json"

# নোটিফিকেশন যেসব ট্যাগ থেকে বাছাই করে — প্রতিটায় যথেষ্ট আইটেম থাকা দরকার
REQUIRED_TAGS = {"salah_invite": 5, "salah_virtue": 5, "salah_warning": 5, "hope_mercy": 5, "fajr": 2, "daily": 20}
TONES = {"gentle", "encourage", "serious", "hope"}
MAX_SHORT = 140  # নোটিফিকেশনে এক নজরে পড়ার মতো


def main() -> int:
    strict = "--strict" in sys.argv
    data = json.loads(CONTENT.read_text(encoding="utf-8"))
    known = set(data["known_tags"])
    items = data["items"]
    errors, warnings = [], []
    ids = set()

    for it in items:
        i = it.get("id", "?")
        for k in ("id", "type", "tags", "ref_bn", "bn", "bn_source", "short_bn", "tone"):
            if not it.get(k):
                errors.append(f"{i}: '{k}' ফাঁকা")
        if i in ids:
            errors.append(f"{i}: আইডি দুবার")
        ids.add(i)
        if it.get("type") not in ("ayah", "hadith"):
            errors.append(f"{i}: type হবে ayah বা hadith")
        if it.get("tone") not in TONES:
            errors.append(f"{i}: অচেনা tone {it.get('tone')}")
        for t in it.get("tags", []):
            if t not in known:
                errors.append(f"{i}: অচেনা ট্যাগ {t}")
        pw = it.get("pair_with")
        if pw and pw not in known:
            errors.append(f"{i}: pair_with অচেনা ট্যাগ {pw}")
        if it.get("tone") == "serious" and "salah_warning" in it.get("tags", []) and not pw:
            errors.append(f"{i}: সতর্কতার সাথে আশার বার্তা (pair_with) বাধ্যতামূলক")
        if len(it.get("short_bn", "")) > MAX_SHORT:
            errors.append(f"{i}: short_bn {len(it['short_bn'])} অক্ষর — {MAX_SHORT}-এর মধ্যে রাখুন")

        if it.get("type") == "ayah":
            r = it.get("ref") or {}
            if not (1 <= r.get("surah", 0) <= 114 and 1 <= r.get("ayah_from", 0) <= r.get("ayah_to", 0)):
                errors.append(f"{i}: সূরা/আয়াত নম্বর ভুল")
            if not it.get("arabic"):
                errors.append(f"{i}: আয়াতের আরবি নেই")
            # অনুবাদের অর্থ যেন না বদলায়: সংক্ষেপ অবশ্যই অনুবাদের হুবহু অংশ
            nfc = lambda t: unicodedata.normalize("NFC", t or "")
            if it.get("short_bn") and nfc(it["short_bn"]) not in nfc(it.get("bn")):
                errors.append(f"{i}: short_bn অনুবাদের হুবহু অংশ নয়")
        else:
            if not it.get("grading"):
                errors.append(f"{i}: হাদিসের মান (grading) নেই — দুর্বল/অজানা হাদিস চলবে না")
            if not it.get("link"):
                warnings.append(f"{i}: যাচাইয়ের লিংক নেই")

        if not it.get("reviewed_by"):
            (errors if strict else warnings).append(f"{i}: আলেমের রিভিউ বাকি")

    for tag, n in REQUIRED_TAGS.items():
        have = sum(1 for it in items if tag in it["tags"])
        if have < n:
            errors.append(f"ট্যাগ '{tag}'-এ {have}টি আইটেম — অন্তত {n}টি লাগবে")

    unreviewed = sum(1 for it in items if not it.get("reviewed_by"))
    for w in warnings:
        if "রিভিউ বাকি" not in w:
            print("সতর্কতা:", w)
    print(f"মোট {len(items)}টি আইটেম, রিভিউ বাকি {unreviewed}টি")
    for e in errors:
        print("ত্রুটি:", e)
    if errors:
        print(f"\n{len(errors)}টি ত্রুটি — ঠিক না করা পর্যন্ত বিল্ড হবে না।")
        return 1
    print("কন্টেন্ট ঠিক আছে ✓" + ("" if strict or unreviewed == 0 else " (রিভিউ বাকি — পাবলিক রিলিজের আগে --strict দিয়ে চালান)"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
