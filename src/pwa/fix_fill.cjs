const fs = require('fs');

const targetFile = 'src/shared/ui/core/TrackMap.vue';
let content = fs.readFileSync(targetFile, 'utf8');

// Change fill-none to fill-current
content = content.replace('fill-none', 'fill-current');

// Remove stroke-width, stroke-linecap, stroke-linejoin from all paths
content = content.replace(/stroke-width="30"/g, '');
content = content.replace(/stroke-linecap="round"/g, '');
content = content.replace(/stroke-linejoin="round"/g, '');

// Also change strokeClass prop default to text-slate so fill-current uses text color
content = content.replace(/:class="strokeClass \|\| 'stroke-slate'"/, `:class="strokeClass || 'text-slate'"`);

fs.writeFileSync(targetFile, content);
console.log('Fixed fill style');
