#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Sinh SQLite luu thong tin gia cho AI Motor Claim Assessor.
Chay:  python3 db/build-price-db.py
Ra:    db/price-data.sql  (schema + INSERT, de xem/sua bang tay)
       db/price-data.db   (file SQLite dung ngay, mo bang DB Browser for SQLite)

Nguon du lieu:
  - src/main/resources/price-table.json : bang gia 30 muc + 12 nguon + 4 moc tong
  - Bang CASES ben duoi               : 14 case thuc te MoMo (42 anh, 3 anh/case)
"""
import json
import os
import sqlite3
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DB_DIR = os.path.join(ROOT, "db")
SQL_PATH = os.path.join(DB_DIR, "price-data.sql")
DB_PATH = os.path.join(DB_DIR, "price-data.db")

# ---------------------------------------------------------------- 14 case MoMo
# ref = ma anh chinh; insurer = hang bao hiem (suy tu ten file tren CDN MoMo);
# compensation_vnd = TONG tien ca vu (gan cho vung hu hai chinh, KHONG phai gia theo mon);
# premium_vnd = phi bao hiem than vo / nam.
CASES = [
    ("REF_05", "PVI", "Kia Seltos 2024", "Dong co (thuy kich)", 3, "Muc 3: Vo/Bien dang nang",
     "Di qua doan duong ngap nuoc, dong co bi thuy kich.", 42_000_000, 7_110_000, "ref-05-seltos-thuy-kich"),
    ("REF_06", "PVI", "Kia Carnival 2024", "Duoi xe (can sau)", 3, "Muc 3: Vo/Bien dang nang",
     "Lui xe trong bai do dong, va cham xe phia sau, hu hong duoi xe.", 32_000_000, 11_300_000, "ref-06-carnival-duoi-xe"),
    ("REF_07", "PVI", "Honda HR-V 2024", "Than xe (tray xuoc, mop nhe)", 2, "Muc 2: Mop vua",
     "Va cham nhe voi xe may trong hem, than xe tray xuoc, mop nhe.", 14_000_000, 7_100_000, "ref-07-hrv-tray-xuoc"),
    ("REF_08", "PJICO", "Mitsubishi Xpander 2024", "Dau xe + Den pha phai", 3, "Muc 3: Vo/Bien dang nang",
     "Va cham voi xe may ban dem, dau xe mop + vo den pha phai.", 13_200_000, 5_006_000, "ref-08-xpander-den-pha"),
    ("REF_09", "PJICO", "Toyota Fortuner 2022", "Toan than + Guong chieu hau", 2, "Muc 2: Mop vua",
     "Bi pha hoai qua dem, tray xuoc toan than + vo guong chieu hau.", 22_500_000, 7_790_000, "ref-09-fortuner-toan-than"),
    ("REF_10", "PJICO", "Toyota Camry 2023", "Cua xe", 2, "Muc 2: Mop vua",
     "Va cham khi chuyen lan, hu hong cua xe.", 25_000_000, 18_200_000, "ref-10-camry-cua-xe"),
    ("REF_11", "Bao Viet", "Honda CR-V 2023", "Can sau + Den hau", 2, "Muc 2: Mop vua",
     "Lui xe trong bai do, va cham xe phia sau, can sau + den hau.", 28_000_000, 11_400_000, "ref-11-crv-can-sau"),
    ("REF_12", "Bao Viet", "VinFast VF5 2024", "Dau xe (tray xuoc, mop)", 2, "Muc 2: Mop vua",
     "Va cham cot be tong trong bai do xe, dau xe tray xuoc, mop.", 8_500_000, 6_050_000, "ref-12-vf5-dau-xe"),
    ("REF_13", "Bao Viet", "VinFast VF9 2024", "Dau xe + Than xe", 3, "Muc 3: Vo/Bien dang nang",
     "Va cham manh tren cao toc, hu hong nang dau xe + than xe.", 58_000_000, 18_600_000, "ref-13-vf9-dau-than"),
    ("REF_14", "GIC", "Toyota Innova 2021", "Nap capo", 2, "Muc 2: Mop vua",
     "Vat roi tu tren cao, nap capo mop + tray xuoc nhe.", 12_000_000, 7_400_000, "ref-14-innova-capo"),
    ("REF_15", "DBV", "Mazda 2 2022", "Than xe (tray xuoc nhe)", 1, "Muc 1: Xuoc nhe",
     "Tray xuoc nhe khi do xe tren via he.", 4_500_000, 5_094_000, "ref-15-mazda2-tray-nhe"),
    ("REF_16", "DBV", "VinFast VF3 2024", "Can truoc (tray xuoc, bong son)", 2, "Muc 2: Mop vua",
     "MoMo khong co mo ta cho case nay - mo ta theo anh: can truoc tray xuoc, bong son.", 6_800_000, 3_300_000, "ref-16-vf3"),
    ("REF_17", "Liberty", "Kia Cerato 2022", "Than xe (tray xuoc)", 1, "Muc 1: Xuoc nhe",
     "Tray xuoc than xe trong bai do xe trung tam thuong mai.", 6_800_000, 5_800_000, "ref-17-cerato-tray-xuoc"),
    ("REF_18", "Bao Long", "Ford Everest 2022", "Dau xe (hu hong nghiem trong)", 3, "Muc 3: Vo/Bien dang nang",
     "Va cham manh tren cao toc voi xe phia truoc, dau xe hu hong nghiem trong.", 75_000_000, 7_079_000, "ref-18-everest-dau-xe"),
]

SCHEMA = """
CREATE TABLE IF NOT EXISTS meta (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

-- 12 nguon uy tin dung de tong hop bang gia
CREATE TABLE IF NOT EXISTS price_sources (
    id       INTEGER PRIMARY KEY AUTOINCREMENT,
    name     TEXT NOT NULL,
    url      TEXT NOT NULL,
    used_for TEXT
);

-- 30 muc gia: 11 bo phan x 3 muc do (son dam / go son / thay moi)
-- Gia xe pho thong, KHONG phan biet hang/model xe. Chua gom VAT.
CREATE TABLE IF NOT EXISTS price_items (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    part           TEXT NOT NULL,
    severity       INTEGER NOT NULL CHECK (severity IN (1, 2, 3)),
    severity_label TEXT NOT NULL,
    action         TEXT NOT NULL,
    min_vnd        INTEGER NOT NULL CHECK (min_vnd > 0),
    max_vnd        INTEGER NOT NULL CHECK (max_vnd > min_vnd),
    confidence     TEXT NOT NULL CHECK (confidence IN ('cao', 'trung bình')),
    note           TEXT
);

-- 4 moc kiem tra tong chi phi ca vu
CREATE TABLE IF NOT EXISTS case_level_anchors (
    id      INTEGER PRIMARY KEY AUTOINCREMENT,
    label   TEXT NOT NULL,
    min_vnd INTEGER NOT NULL,
    max_vnd INTEGER NOT NULL,
    source  TEXT NOT NULL
);

-- 14 case thuc te MoMo. compensation_vnd = TONG tien ca vu (gan cho vung
-- hu hai chinh), KHONG phai bang gia theo mon.
CREATE TABLE IF NOT EXISTS reference_cases (
    ref_id           TEXT PRIMARY KEY,
    insurer          TEXT NOT NULL,
    vehicle          TEXT NOT NULL,
    part             TEXT NOT NULL,
    severity         INTEGER NOT NULL CHECK (severity IN (1, 2, 3)),
    severity_label   TEXT NOT NULL,
    description      TEXT NOT NULL,
    compensation_vnd INTEGER NOT NULL,
    premium_vnd      INTEGER NOT NULL
);

-- 42 anh mau: 3 anh/case (goc khac nhau, cung gia). ref_id = REF_xx / REF_xxb / REF_xxc
CREATE TABLE IF NOT EXISTS reference_photos (
    ref_id   TEXT PRIMARY KEY,
    case_ref TEXT NOT NULL REFERENCES reference_cases (ref_id),
    file_name TEXT NOT NULL,
    angle_no INTEGER NOT NULL CHECK (angle_no IN (1, 2, 3))
);
"""


def main():
    with open(os.path.join(ROOT, "src/main/resources/price-table.json"), encoding="utf-8") as fh:
        table = json.load(fh)

    if os.path.exists(DB_PATH):
        os.remove(DB_PATH)
    con = sqlite3.connect(DB_PATH)
    cur = con.cursor()
    cur.executescript(SCHEMA)

    cur.execute("INSERT INTO meta (key, value) VALUES (?, ?)", ("version", table["version"]))
    cur.execute("INSERT INTO meta (key, value) VALUES (?, ?)", ("updated", table["updated"]))
    cur.execute("INSERT INTO meta (key, value) VALUES (?, ?)", ("currency", table["currency"]))
    cur.execute("INSERT INTO meta (key, value) VALUES (?, ?)", ("scope", table["scope"]))

    for s in table["sources"]:
        cur.execute("INSERT INTO price_sources (name, url, used_for) VALUES (?, ?, ?)",
                    (s["name"], s["url"], s["usedFor"]))
    for it in table["items"]:
        cur.execute(
            "INSERT INTO price_items (part, severity, severity_label, action, min_vnd, max_vnd, confidence, note)"
            " VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            (it["part"], it["severity"], it["severityLabel"], it["action"],
             it["minVnd"], it["maxVnd"], it["confidence"], it.get("note")))
    for a in table["caseLevelAnchors"]:
        cur.execute("INSERT INTO case_level_anchors (label, min_vnd, max_vnd, source) VALUES (?, ?, ?, ?)",
                    (a["label"], a["minVnd"], a["maxVnd"], a["source"]))
    for c in CASES:
        ref, insurer, vehicle, part, sev, sev_label, desc, comp, prem, slug = c
        cur.execute(
            "INSERT INTO reference_cases (ref_id, insurer, vehicle, part, severity, severity_label,"
            " description, compensation_vnd, premium_vnd) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            (ref, insurer, vehicle, part, sev, sev_label, desc, comp, prem))
        for angle, suffix in ((1, ""), (2, "b"), (3, "c")):
            prefix = "ref-" + ref[4:]  # REF_05 -> ref-05
            fname = slug if not suffix else slug.replace(prefix, prefix + suffix, 1)
            cur.execute(
                "INSERT INTO reference_photos (ref_id, case_ref, file_name, angle_no) VALUES (?, ?, ?, ?)",
                (ref + suffix, ref, fname + ".jpg", angle))
    con.commit()

    # Xuat file .sql (schema + INSERT) de xem/sua bang tay
    with open(SQL_PATH, "w", encoding="utf-8") as out:
        out.write("-- SQLite luu thong tin gia - AI Motor Claim Assessor\n")
        out.write("-- Sinh tu: python3 db/build-price-db.py (nguon: price-table.json + 14 case MoMo)\n")
        out.write("-- Dung: sqlite3 price-data.db < price-data.sql\n\n")
        for line in con.iterdump():
            out.write(line + "\n")

    # Kiem tra
    checks = {
        "price_sources": 12, "price_items": 30, "case_level_anchors": 4,
        "reference_cases": 14, "reference_photos": 42,
    }
    ok = True
    for tbl, expect in checks.items():
        got = cur.execute(f"SELECT COUNT(*) FROM {tbl}").fetchone()[0]
        print(f"{tbl}: {got} (mong doi {expect}) {'OK' if got == expect else 'SAI!'}")
        ok = ok and got == expect
    # Moi anh deu co file that trong storage/
    missing = []
    for (ref_id, fname) in cur.execute("SELECT ref_id, file_name FROM reference_photos"):
        if not os.path.exists(os.path.join(ROOT, "storage/reference-damages", fname)):
            missing.append((ref_id, fname))
    print("anh thieu file that:", missing if missing else "khong co")
    ok = ok and not missing
    con.close()
    print("OK" if ok else "CO LOI - kiem tra lai")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
