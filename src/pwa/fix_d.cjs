const fs = require('fs');

const pathContent = fs.readFileSync('full_path.txt', 'utf8').trim();
const targetFile = 'src/shared/ui/core/TrackMap.vue';
let content = fs.readFileSync(targetFile, 'utf8');

// Replace d="" with d="pathContent"
content = content.replace('d="" />', `d="${pathContent}" />`);

// Also we need to remove the duplicate static one that the previous script inserted!
// It looks like: <path stroke-width="30" stroke-linecap="round" stroke-linejoin="round"  d="M2898...h0Z" />
content = content.replace(/<path stroke-width="30" stroke-linecap="round" stroke-linejoin="round"\s*d="M2898[^>]*\/>/, '');

fs.writeFileSync(targetFile, content);
console.log('Fixed d attribute');
