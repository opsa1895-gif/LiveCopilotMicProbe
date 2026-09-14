from pathlib import Path

p = Path('.github/scripts/v064_patch.py')
s = p.read_text()

old_clear = '''# Every clear of pending semantic focus must also clear conservative mode.\nclear = '        pendingSemanticFocus = "";\\n'\nif s.count(clear) < 5:\n    raise SystemExit(f'pendingSemanticFocus clears: expected >=5, found {s.count(clear)}')\ns = s.replace(clear, clear + '        pendingSemanticConservative = false;\\n')\n'''
new_clear = '''# Every clear of pending semantic focus must also clear conservative mode,\n# preserving the original nesting indentation.\nclear_count = sum(1 for line in s.splitlines() if line.strip() == 'pendingSemanticFocus = "";')\nif clear_count < 5:\n    raise SystemExit(f'pendingSemanticFocus clears: expected >=5, found {clear_count}')\nlines = []\nfor line in s.splitlines(keepends=True):\n    lines.append(line)\n    if line.strip() == 'pendingSemanticFocus = "";':\n        indent = line[:len(line) - len(line.lstrip())]\n        lines.append(indent + 'pendingSemanticConservative = false;\\n')\ns = ''.join(lines)\n'''
if old_clear not in s:
    raise SystemExit('clear-mode helper block not found')
s = s.replace(old_clear, new_clear, 1)

old_debug = '''s = replace_once(s,\n''' + "'''" + '''        debugText.setText(app + " • " + mic + rt + echo + heard + partial + latency);\\n''' + "'''" + ''',\n''' + "'''" + '''        debugText.setText(app + " • " + mic + rt + echo + heard + partial + latency + fileConfidence);\\n''' + "'''" + ''', 'debug confidence output')\n'''
new_debug = '''s = replace_once(s,\n''' + "'''" + '''        debugText.setText(app + " • " + mic + rt + echo + heard + partial + latency\\n                + "\\nstt " + SecretStore.loadFileSttModel(this)\\n                + session);\\n''' + "'''" + ''',\n''' + "'''" + '''        debugText.setText(app + " • " + mic + rt + echo + heard + partial + latency\\n                + fileConfidence\\n                + "\\nstt " + SecretStore.loadFileSttModel(this)\\n                + session);\\n''' + "'''" + ''', 'debug confidence output')\n'''
if old_debug not in s:
    raise SystemExit('debug-output helper block not found')
s = s.replace(old_debug, new_debug, 1)

p.write_text(s)
