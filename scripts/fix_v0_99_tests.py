from pathlib import Path

path = Path(__file__).resolve().parents[1] / "app/src/test/java/com/livecopilot/micprobe/RouteReleaseTransitionConfirmationRateTest.java"
text = path.read_text()
old = 'rel s/r/x rtslow 6/4/0 rev50%@8 sig=stable>risk×1/2 tr=1/2 cancel=1/1 conf50%@2'
new = 'rel s/r/x rtslow 6/4/0 rev50%@8 sig=stable>risk×1/2 tr=1/2 cancel=1/1 conf50%@2/mixed'
if text.count(old) != 1:
    raise RuntimeError(f"expected one old diagnostics assertion, found {text.count(old)}")
path.write_text(text.replace(old, new, 1))
