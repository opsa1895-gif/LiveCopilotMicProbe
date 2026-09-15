from pathlib import Path

p = Path('.ci/v0_66_patch.py')
text = p.read_text()
old = '''min_count=5)
replace_once(client,
''' + "'''        int chunkIndex = ++fileTurnSubmittedChunks;"
new = '''min_count=3)
replace_all(client,
''' + "'''            fileTurnSubmittedChunks = 0;\n            fileTurnFailedChunks = 0;\n            fileTurnLastFailedChunkIndex = 0;'''" + ''',
''' + "'''            fileTurnSubmittedChunks = 0;\n            fileTurnFailedChunks = 0;\n            fileTurnLastFailedChunkIndex = 0;\n            fileTurnUsableChunks = 0;\n            fileTurnTimeoutDrops = 0;\n            fileTurnNetworkDrops = 0;\n            fileTurnQualityDrops = 0;\n            fileTurnOtherDrops = 0;'''" + ''',
min_count=2)
replace_once(client,
''' + "'''        int chunkIndex = ++fileTurnSubmittedChunks;"
if old not in text:
    raise SystemExit('v0.66 reset guard marker not found')
p.write_text(text.replace(old, new, 1))
print('v0.66 helper guard repaired')
