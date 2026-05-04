const fs = require('fs');

const svgContent = fs.readFileSync('sonoma_wiki.svg', 'utf8');

// Match all paths
const pathRegex = /<path[^>]+class="(cls-1|cls-9)"[^>]+d="([^"]+)"[^>]*\/>/g;
let match;
let mainPath = '';
let otherPaths = '';

let i = 0;
while ((match = pathRegex.exec(svgContent)) !== null) {
  const cls = match[1];
  const d = match[2];
  if (cls === 'cls-1' && i === 0) {
    mainPath = d; // the first cls-1 is the longest one we use for telemetry
  } else {
    const opacity = cls === 'cls-9' ? 'class="opacity-50"' : '';
    otherPaths += `    <path stroke-width="30" stroke-linecap="round" stroke-linejoin="round" ${opacity} d="${d}" />\n`;
  }
  i++;
}

let template = `<template>
  <svg viewBox="0 0 3000 1440" class="w-full h-full fill-none" :class="strokeClass || 'stroke-slate'">
    <!-- Alternate Routes and Missing Segments -->
${otherPaths}
    <!-- Sonoma Track Path Main -->
    <path 
      ref="trackPath" 
      stroke-width="30"
      stroke-linecap="round"
      stroke-linejoin="round"
      d="${mainPath}" />
    
    <!-- Car Dot -->
    <circle 
      v-if="carProgress !== undefined && carProgress !== null"
      :cx="carCx" 
      :cy="carCy" 
      r="60" 
      class="fill-ui-warn animate-pulse stroke-none transition-all duration-75" 
    />
    <slot></slot>
  </svg>
</template>`;

const targetFile = 'src/shared/ui/core/TrackMap.vue';
let content = fs.readFileSync(targetFile, 'utf8');
content = content.replace(/<template>[\s\S]*<\/template>/, template);
fs.writeFileSync(targetFile, content);

console.log('Fixed SVG!');
