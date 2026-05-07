const fs = require('fs');
const pathContent = fs.readFileSync('full_path.txt', 'utf8').trim();
const targetFile = 'src/shared/ui/core/TrackMap.vue';
let content = fs.readFileSync(targetFile, 'utf8');

content = content.replace(/d="M2898[^"]*"/, `d="${pathContent}"`);

fs.writeFileSync(targetFile, content);
console.log('Done replacing SVG path');
