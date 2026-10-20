"""Пары «входящая строка — ответ старой программы — вердикт сети» для ArchiveAnswerRulesTest.

    python3 -I test-stand/answer-pairs.py <docs/mesplan_messages> <папка входящих (PL_K66…)> <куда писать>

Ответы — Prog/mesplan2016/<год>/…, квитанции — RC<идентификатор ответа> в <год>/<месяц>/<день>/,
входящая строка — по ключу «адм · № заявки · № строки · порядковый №», последняя по времени до ответа.
Пишет triples.tsv: имя ответа, коды ошибок квитанции на строку («-» — принята), входящая строка, ответ.
"""
import collections, os, re, sys

archive, incoming, out = sys.argv[1], sys.argv[2], sys.argv[3]


def key(t):
    z = lambda v: str(int(v)) if v.strip().isdigit() else v.strip()
    return (z(t[0]), t[6].strip(), z(t[7]), z(t[8]) if t[8].strip() else '0')


inc = collections.defaultdict(list)
for root, _, fs in os.walk(incoming):
    for f in fs:
        if not f.startswith('PL') or re.search(r'\.\d*123\d*_NV', f):
            continue
        for line in open(os.path.join(root, f), 'rb').read().decode('cp866', 'replace').splitlines()[1:]:
            t = line.split('^')
            if len(t) > 40:
                inc[key(t)].append((f, line))
answers = {}
for root, _, fs in os.walk(os.path.join(archive, 'Prog', 'mesplan2016')):
    if not re.match(r'\d+$', os.path.basename(root)):
        continue
    for f in fs:
        text = open(os.path.join(root, f), 'rb').read().decode('cp866', 'replace').splitlines()
        if text and text[0].strip().startswith('PL'):
            answers[text[0].strip()] = text[1:]
receipts = {}
for root, _, fs in os.walk(archive):
    if 'Prog' in root:
        continue
    for f in fs:
        if f.startswith('RC'):
            receipts[f[2:]] = os.path.join(root, f)
rows = 0
os.makedirs(out, exist_ok=True)
with open(os.path.join(out, 'triples.tsv'), 'w', encoding='utf-8') as w:
    for name, lines in answers.items():
        if name not in receipts:
            continue
        errors = collections.defaultdict(list)
        for line in open(receipts[name], 'rb').read().decode('cp866', 'replace').splitlines()[1:]:
            m = re.match(r'(\d{4}) (\d{5}) ', line)
            if m:
                errors[int(m.group(2))].append(m.group(1))
        when = name.split('-', 1)[1][:19]
        for i, line in enumerate([x for x in lines if x.strip()], 1):
            t = line.split('^')
            if len(t) < 41:
                continue
            cands = [c for c in inc.get(key(t), []) if c[0].split('-', 1)[1][:19] <= when]
            if cands:
                w.write('\t'.join([name, ','.join(errors.get(i, [])) or '-', sorted(cands)[-1][1], line]) + '\n')
                rows += 1
print('пар:', rows)
