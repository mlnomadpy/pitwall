const fs = require('fs');

const targetFile = 'src/shared/ui/core/TrackMap.vue';
let content = fs.readFileSync(targetFile, 'utf8');

// Find the main path d attribute
const pathMatch = content.match(/<path\s+ref="trackPath"\s+d="([^"]+)"/);
if (!pathMatch) {
  console.error("Could not find trackPath");
  process.exit(1);
}

const fullD = pathMatch[1];

// The first subpath ends at the first 'Z' (case insensitive, usually 'Z' or 'z')
// Actually, it ends at 'h0Z'. We can just split by 'ZM' and add 'Z' back.
let firstSubpath = fullD.split('ZM')[0];
if (!firstSubpath.endsWith('Z')) {
  firstSubpath += 'Z';
}

// Now replace the trackPath element with two elements:
// 1. The visual path (fullD)
// 2. The invisible telemetry path (firstSubpath)
const newElements = `<!-- Visual Sonoma Track Path Main -->
    <path d="${fullD}" />
    
    <!-- Telemetry Path (invisible, used for car position) -->
    <path 
      ref="trackPath" 
      d="${firstSubpath}" 
      class="opacity-0 stroke-none fill-none"
    />`;

content = content.replace(/<!-- Sonoma Track Path Main -->[\s\S]*?<path\s+ref="trackPath"\s+d="[^"]+"\s*\/>/, newElements);

fs.writeFileSync(targetFile, content);
console.log('Fixed telemetry path');
