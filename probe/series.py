import json, sys
try:
    data = json.load(open(sys.argv[1], encoding='utf-8'))
except Exception as exc:
    print('okunamadi:', exc); sys.exit(0)
if 'data' not in data:
    print(json.dumps(data, ensure_ascii=False)[:300]); sys.exit(0)
print('total:', data.get('total'))
for it in data.get('data', []):
    r = it.get('rating') or {}
    print(' -', it.get('id'), '|', (it.get('name_cn') or it.get('name') or '')[:44],
          '| series=', it.get('series'), '| platform=', it.get('platform'),
          '| rank=', r.get('rank'), 'score=', r.get('score'), 'votes=', r.get('total'),
          '| vols=', it.get('volumes'))
