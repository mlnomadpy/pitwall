import { sveltekit } from '@sveltejs/kit/vite';
import { defineConfig } from 'vite';

export default defineConfig({
	plugins: [sveltekit()],
	server: {
		proxy: {
			'/api/cues/stream': {
				target: 'http://127.0.0.1:8765',
				changeOrigin: true,
				rewrite: (path) => path.replace(/^\/api/, ''),
				headers: { 'Accept': 'text/event-stream' },
				configure: (proxy) => {
					proxy.on('proxyReq', (_proxyReq, _req, res) => {
						// Disable buffering for SSE
						(res as any).writeHead = new Proxy((res as any).writeHead, {
							apply(target: any, thisArg: any, args: any[]) {
								if (!thisArg.headersSent) {
									thisArg.setHeader('Cache-Control', 'no-cache');
									thisArg.setHeader('X-Accel-Buffering', 'no');
								}
								return target.apply(thisArg, args);
							}
						});
					});
				}
			},
			'/api': {
				target: 'http://127.0.0.1:8765',
				changeOrigin: true,
				rewrite: (path) => path.replace(/^\/api/, '')
			}
		}
	}
});
