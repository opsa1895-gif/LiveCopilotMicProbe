from pathlib import Path

p = Path('.github/scripts/v064_patch.py')
s = p.read_text()
old = '''if s.count(needle) != 3:\n    raise SystemExit(f'turn reset sites: expected 3 occurrences, found {s.count(needle)}')\ns = s.replace(needle, replacement)\n'''
new = '''if s.count(needle) != 2:\n    raise SystemExit(f'turn reset sites: expected 2 direct occurrences, found {s.count(needle)}')\ns = s.replace(needle, replacement, 2)\ns = replace_once(s,\n''' + '"""' + '''            fileTurnSerial = -1L;\\n            fileTurnFocus = \\\"\\\";\\n            fileTurnHadSttTimeout = false;\\n            audioExecutor.cancelPending();\\n''' + '"""' + ''',\n''' + '"""' + '''            fileTurnSerial = -1L;\\n            fileTurnFocus = \\\"\\\";\\n            fileTurnHadSttTimeout = false;\\n            fileTurnSubmittedChunks = 0;\\n            fileTurnFailedChunks = 0;\\n            fileTurnLastFailedChunkIndex = 0;\\n            audioExecutor.cancelPending();\\n''' + '"""' + ''', 'realtime transcript reset counters')\n'''
if old not in s:
    raise SystemExit('original reset guard not found')
s = s.replace(old, new, 1)
p.write_text(s)
