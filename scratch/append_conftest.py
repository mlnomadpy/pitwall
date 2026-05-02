import re
with open("tests/conftest.py", "r") as f:
    content = f.read()

# Fix sys.path
content = content.replace('sys.path.insert(0, str(ROOT / "src" / "simulator"))', '')

# Append isolated_bridge etc
with open("scratch/old_test.py", "r") as f:
    lines = f.readlines()

append_text = "".join(lines[11:68]) # from import pitwall as br to end of _start_session

# Update isolated_bridge to clear bursts
append_text = append_text.replace(
    'monkeypatch.setattr(br.state, "session_bundles", {})',
    'monkeypatch.setattr(br.state, "session_bundles", {})\n    monkeypatch.setattr(br.state, "session_bursts", [])'
)

with open("tests/conftest.py", "w") as f:
    f.write(content + "\n" + append_text)
