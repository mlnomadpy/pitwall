import re

with open('src/shared/ui/core/TrackMap.vue', 'r') as f:
    content = f.read()

# Extract turns
turns = []
for m in re.finditer(r'<g>.*?<circle class="cls-7" cx="([^"]+)" cy="([^"]+)" r="([^"]+)"/>(.*?)<\/g>\s*(?=<g>|<g class="cls-8">|<\/svg>)', content, re.DOTALL):
    cx, cy, r, inner = m.groups()
    turns.append({'cx': cx, 'cy': cy, 'inner': inner.strip()})

# Extract the weird cls-8 group at the end that contains paths without circles
# Wait, let's just do it manually. I have the output from extract_digits.py!
