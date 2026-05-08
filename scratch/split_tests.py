import re

narrative = """
[EMOTION:drill]
Settle in, Taha. The track is cool and crisp today, but beware of Turn 2 — the grip drops off sharply there. Your focus today is breaking the 1:48 barrier, but your weakest corner recently has been T7.

FOCUS:
- Brake later into T7 to carry more speed
- Keep it smooth through the esses
- Trust the grip in the carousel
"""

focus = []
if "FOCUS:" in narrative:
    parts = narrative.split("FOCUS:")
    narrative_part = parts[0].strip()
    focus_text = parts[1].strip()
    bullets = [re.sub(r"^[-*0-9.]+\s*", "", line).strip() for line in focus_text.split("\n") if line.strip() and re.match(r"^[-*0-9.]+\s*", line.strip())]
    if bullets:
        focus = bullets[:3]
    narrative = narrative_part

print("Narr:", narrative)
print("Focus:", focus)
