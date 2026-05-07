const fs = require('fs');

const vueContent = fs.readFileSync('src/shared/ui/core/TrackMap.vue', 'utf8');

// Extract the telemetry path
const pathMatch = vueContent.match(/<path \s*ref="trackPath" \s*d="([^"]+)"/);
const pathData = pathMatch[1];

// Extract the circles
const circleRegex = /<circle class="cls-7" cx="([^"]+)" cy="([^"]+)"/g;
let match;
const circles = [];
while ((match = circleRegex.exec(vueContent)) !== null) {
  circles.push({ cx: parseFloat(match[1]), cy: parseFloat(match[2]) });
}

console.log("Found circles:", circles.length);
console.log(circles);
