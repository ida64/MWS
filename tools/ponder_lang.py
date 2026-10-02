"""
Syncs the Ponder scene text in en_us.json with MwsPonderScenes.java. Ponder looks text up as
mws.ponder.<scene>.header / .text_N, numbered in the order showText runs, so this reads the
scenes in source order and rewrites those keys. Ponder passes the text through String.format,
so a literal % is written as %%. Run from the repo root: python3 tools/ponder_lang.py
"""
import collections, json, re

SCENES = 'src/main/java/dev/paging/mws/client/ponder/MwsPonderScenes.java'
LANG = 'src/main/resources/assets/mws/lang/en_us.json'

src = open(SCENES).read()
entries = collections.OrderedDict()
for match in re.finditer(r'begin\(builder, "([a-z_]+)", "((?:[^"\\]|\\.)*)"\)(.*?)(?=\n    public static void|\Z)', src, re.S):
    scene, title, body = match.groups()
    entries[f'mws.ponder.{scene}.header'] = title.replace('%', '%%')
    for i, text in enumerate(re.findall(r'\.text\("((?:[^"\\]|\\.)*)"\)', body), 1):
        entries[f'mws.ponder.{scene}.text_{i}'] = json.loads(f'"{text}"').replace('%', '%%')

lang = json.load(open(LANG), object_pairs_hook=collections.OrderedDict)
for key in [k for k in lang if k.startswith('mws.ponder.') and not k.startswith('mws.ponder.tag.')]:
    del lang[key]
lang.update(entries)
open(LANG, 'w').write(json.dumps(lang, indent=2, ensure_ascii=False) + '\n')
print(f'{len(entries)} ponder entries')
