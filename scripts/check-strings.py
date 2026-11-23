#!/usr/bin/env python3
"""Nearo string check for #29. Usage: python3 scripts/check-strings.py <repo_root>
Source of truth: designer copy in project files design/i18n/check_strings.py (glossary in glossary.csv).
Checks every values-{ja,es,pt-rBR,de,ko}/strings.xml against values/ (EN source):
  missing/extra keys, placeholder mismatch, glossary terms, banned words, brand spelling, length risk.
"""
import sys, re, glob, os, xml.etree.ElementTree as ET

LANGS = {'ko': 'ko', 'ja': 'ja', 'es': 'es', 'pt': 'pt-rBR', 'de': 'de'}
# key -> required term per language (substring must appear)
GLOSSARY = {
    'tab_nearby':        dict(ko='주변', ja='周り', es='Cerca', pt='Por perto', de='In der Nähe'),
    'tab_my_devices':    dict(ko='내 기기', ja='マイデバイス', es='Mis dispositivos', pt='Meus dispositivos', de='Meine Geräte'),
    'devices_title':     dict(ko='내 기기', ja='マイデバイス', es='Mis dispositivos', pt='Meus dispositivos', de='Meine Geräte'),
    'finder_band_right_here': dict(ko='바로 여기', ja='ここ', es='Aquí', pt='Bem aqui', de='Genau hier'),
    'finder_found':      dict(ko='찾았어요', ja='見つけた', es='Lo encontré', pt='Achei', de='Gefunden'),
    'finder_distance_label': dict(ko='대략적인 거리', ja='おおよその距離', es='aproximada', pt='aproximada', de='ungefähr'),
    'finder_direction_label': dict(ko='대략적인 방향', ja='おおよその方向', es='aproximada', pt='aproximada', de='ungefähr'),
    'category_tracker':  dict(ko='트래커', ja='トラッカー', es='Rastreador', pt='Rastreador', de='Tracker'),
    'category_group_tracker': dict(ko='트래커', ja='トラッカー', es='Rastreadores', pt='Rastreadores', de='Tracker'),
    'badge_paired':      dict(ko='내 기기', ja='マイ', es='Mío', pt='Meu', de='Meins'),
    'device_generic':    dict(ko='블루투스 기기', ja='Bluetooth', es='Bluetooth', pt='Bluetooth', de='Bluetooth'),
    'devices_others':    dict(ko='다른 페어링 기기', ja='ペアリング', es='vinculados', pt='pareados', de='gekoppelte'),
}
BANNED = {  # never show "Unknown" (#46), never promise precision
    'all': [r'\bUnknown\b', r'\bexact', r'\bprecise', r'\bprecision'],
    'ko': ['알 수 없는', '정확한'], 'ja': ['不明', '正確'], 'es': ['Desconocido', r'\bexact', r'\bprecis[oa]', r'precisión'],
    # PT: "precisa/preciso" also means "needs", so only the noun/adjective forms are banned
    'pt': ['Desconhecido', r'\bexat', r'precisão', r'\bprecis[oa]s? (de )?(dist|loc|dire)'],
    # DE: "Genau hier" (Right here) is fine; only ban precision claims
    'de': ['Unbekannt', r'genaue[nrs]? (Entfernung|Richtung|Position|Standort)', 'exakt', 'präzis'],
}
# keys shown in tight spots: max ratio of target length vs EN
TIGHT = {'tab_nearby': 1.6, 'tab_my_devices': 1.6, 'badge_paired': 1.5, 'badge_new': 1.8, 'finder_found': 1.8,
         'finder_vibration': 1.6, 'finder_band_right_here': 1.8, 'finder_band_very_close': 1.8, 'finder_band_near': 2.2,
         'finder_band_far': 2.5, 'finder_trend_closer': 1.8, 'finder_trend_farther': 1.8, 'finder_trend_steady': 1.6,
         'filter_named_only': 1.5, 'filter_hide_gone': 1.6, 'category_group_earbuds': 1.6, 'category_group_watch': 1.6,
         'category_group_tracker': 1.6, 'category_group_other': 2.0, 'device_distance_within_1': 1.6}
# keys where ES/PT must reorder maker/type
REORDER = {'device_generic_maker_type': ['es', 'pt'], 'device_generic_maker': ['es', 'pt']}
PH = re.compile(r'%(\d+\$)?[sdf]')

def load(path):
    out = {}
    for e in ET.parse(path).getroot():
        if e.get('translatable') == 'false': continue
        n = e.get('name')
        if e.tag == 'string': out[n] = ''.join(e.itertext())
        elif e.tag == 'plurals': out[n] = ' | '.join(''.join(i.itertext()) for i in e)
    return out

def main(root):
    issues = 0
    for src in sorted(glob.glob(f'{root}/**/src/main/res/values/strings.xml', recursive=True)):
        en = load(src); base = os.path.dirname(os.path.dirname(src)); mod = os.path.relpath(base, root)
        for lang, q in LANGS.items():
            p = f'{base}/values-{q}/strings.xml'
            if not os.path.exists(p):
                print(f'[{lang}] {mod}: MISSING FILE values-{q}/strings.xml'); issues += 1; continue
            tr = load(p)
            for k, v in en.items():
                t = tr.get(k)
                def bad(msg):
                    nonlocal issues; issues += 1; print(f'[{lang}] {mod} {k}: {msg}\n      EN: {v}\n      {lang.upper()}: {t}')
                if t is None: bad('missing'); continue
                if set(PH.findall(v)) != set(PH.findall(t)): bad('placeholder mismatch')
                if 'Nearo' in v and 'Nearo' not in t: bad('brand must stay "Nearo" in Latin letters')
                g = GLOSSARY.get(k, {}).get(lang)
                if g and g.lower() not in t.lower(): bad(f'glossary: expected "{g}"')
                for b in BANNED['all'] + BANNED.get(lang, []):
                    if re.search(b, t, re.I): bad(f'banned wording "{b}"')
                if k in TIGHT and lang != 'ko' and len(t) > len(v) * TIGHT[k] + 2: bad(f'too long for its slot ({len(t)} vs EN {len(v)})')
                if k in REORDER and lang in REORDER[k] and t.strip().startswith('%1$s'): bad('word order: put the type before the maker (e.g. "Rastreador %1$s" -> use %2$s %1$s)')
            for k in tr.keys() - en.keys(): print(f'[{lang}] {mod} {k}: extra key not in EN'); issues += 1
    print(f'\n{issues} issue(s)')
    return 1 if issues else 0

if __name__ == '__main__':
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else '.'))
