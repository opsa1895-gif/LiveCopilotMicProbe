from pathlib import Path

p = Path('.github/scripts/v061_patch.py')
s = p.read_text()
start = s.index('s = one(\n    s,\n    "    private void resetLatencyMetrics()')
end = s.index('p.write_text(s)', start)
replacement = '''s = one(\n    s,\n    "    private void resetLatencyMetrics() {\\n"\n    "        lastVoiceEndAtMs = 0L;\\n"\n    "        thinkingStartedAtMs = 0L;\\n"\n    "        lastSttLatencyMs = -1L;\\n"\n    "        lastFirstReplyLatencyMs = -1L;\\n"\n    "        lastStylesLatencyMs = -1L;\\n"\n    "        lastSemanticLatencyMs = -1L;\\n"\n    "    }",\n    "    private void resetLatencyMetrics() {\\n"\n    "        lastVoiceEndAtMs = 0L;\\n"\n    "        thinkingStartedAtMs = 0L;\\n"\n    "        lastSttLatencyMs = -1L;\\n"\n    "        lastFirstReplyLatencyMs = -1L;\\n"\n    "        lastStylesLatencyMs = -1L;\\n"\n    "        lastSemanticLatencyMs = -1L;\\n"\n    "        lastFileDrainLatencyMs = -1L;\\n"\n    "    }",\n    "reset metric",\n)\n'''
p.write_text(s[:start] + replacement + s[end:])
