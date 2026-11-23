#!/usr/bin/env python3
"""Regenerate core/ble/src/main/assets/bt_*.tsv from the Bluetooth SIG Assigned Numbers repo.

Usage: python3 scripts/update_bt_assigned_numbers.py
The SIG adds companies every month; run this before each release.
"""
import pathlib
import re
import urllib.request

BASE = "https://bitbucket.org/bluetooth-SIG/public/raw/main/assigned_numbers/"
OUT = pathlib.Path(__file__).resolve().parent.parent / "core/ble/src/main/assets"
SOURCES = {
    "bt_company_ids.tsv": ("company_identifiers/company_identifiers.yaml", "value"),
    "bt_member_uuids.tsv": ("uuids/member_uuids.yaml", "uuid"),
}


def parse(text: str, key: str) -> dict[int, str]:
    entries = {}
    for m in re.finditer(key + r":\s*(0x[0-9A-Fa-f]+)\s*\n\s*name:\s*(.*)\n", text):
        name = m.group(2).strip()
        if name[:1] in "'\"" and name[-1:] == name[:1]:
            name = name[1:-1]
        entries[int(m.group(1), 16)] = name.replace("''", "'").replace("\t", " ")
    return entries


for out_name, (path, key) in SOURCES.items():
    with urllib.request.urlopen(BASE + path) as resp:
        entries = parse(resp.read().decode("utf-8"), key)
    header = f"# Source: Bluetooth SIG Assigned Numbers (bitbucket.org/bluetooth-SIG/public), {path}. Format: hex id<TAB>name\n"
    body = "".join(f"{k:04X}\t{v}\n" for k, v in sorted(entries.items()))
    (OUT / out_name).write_text(header + body, encoding="utf-8")
    print(f"{out_name}: {len(entries)} entries")
