const fs = require('fs');

const svg = fs.readFileSync('./sonoma_gp.svg', 'utf8');
const regex = /<path([^>]*)>/g;
let match;
const paths = [];

while ((match = regex.exec(svg)) !== null) {
  const attrs = match[1];
  const dMatch = attrs.match(/d="([^"]+)"/);
  const classMatch = attrs.match(/class="([^"]+)"/);
  if (dMatch) {
    paths.push({
      class: classMatch ? classMatch[1] : null,
      length: dMatch[1].length,
      d: dMatch[1]
    });
  }
}

paths.sort((a, b) => b.length - a.length);

console.log(JSON.stringify(paths.slice(0, 10).map(p => ({ class: p.class, length: p.length, dStart: p.d.substring(0, 30) })), null, 2));

// find paths matching the ones in TrackMap.vue
const trackMapD = [
  "M342.3,299.9",
  "M1246.4,173.9",
  "M360.6,995.7",
  "M2576.7,417.5",
  "M2898,624.8"
]

const trackMapPaths = trackMapD.map(dPrefix => {
  return paths.find(p => p.d.startsWith(dPrefix)) || { dStart: dPrefix, found: false };
});

console.log("TrackMap Paths found in SVG:");
console.log(trackMapPaths.map(p => ({ class: p.class, length: p.length, dStart: p.dStart, found: p.found !== false })));
