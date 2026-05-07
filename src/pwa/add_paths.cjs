const fs = require('fs');

// The file with cls-1 and cls-9 paths
const lines = fs.readFileSync('track_paths.txt', 'utf8').split('\n').filter(l => l.trim().length > 0);
const cls9Paths = lines.filter(l => l.includes('cls-9'));

// Map to new tags
const newTags = cls9Paths.map(p => {
  const dMatch = p.match(/d="([^"]+)"/);
  if (!dMatch) return '';
  return `    <path stroke-width="30" stroke-linecap="round" stroke-linejoin="round" class="opacity-50" d="${dMatch[1]}" />`;
}).join('\n');

const targetFile = 'src/shared/ui/core/TrackMap.vue';
let content = fs.readFileSync(targetFile, 'utf8');

// Insert newTags before <!-- Sonoma Track Path -->
content = content.replace('<!-- Sonoma Track Path -->', `<!-- Alternate Routes -->\n${newTags}\n    <!-- Sonoma Track Path -->`);

fs.writeFileSync(targetFile, content);
console.log('Added cls-9 paths');
