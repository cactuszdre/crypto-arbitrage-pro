// Cloudflare Worker - Proxy for Variational API
// Deploy this on Cloudflare Workers to bypass IP blocking

const VARIATIONAL_BASE_URL = 'https://omni.variational.io';

// Optional: Add a secret key to protect your worker
const SECRET_KEY = ''; // Leave empty to disable, or set a value like 'your-secret-key-here'

export default {
    async fetch(request, env, ctx) {
        // Handle CORS preflight
        if (request.method === 'OPTIONS') {
            return new Response(null, {
                headers: {
                    'Access-Control-Allow-Origin': '*',
                    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
                    'Access-Control-Allow-Headers': 'Content-Type, X-Secret-Key',
                },
            });
        }

        // Optional: Check secret key for security
        if (SECRET_KEY && request.headers.get('X-Secret-Key') !== SECRET_KEY) {
            return new Response('Unauthorized', { status: 401 });
        }

        try {
            const url = new URL(request.url);

            // Build the target URL
            const targetUrl = VARIATIONAL_BASE_URL + url.pathname + url.search;

            // Forward the request to Variational
            const response = await fetch(targetUrl, {
                method: request.method,
                headers: {
                    'Accept': 'application/json',
                    'Content-Type': 'application/json',
                    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
                },
                body: request.method !== 'GET' ? await request.text() : undefined,
            });

            // Get the response body
            const body = await response.text();

            // Return the response with CORS headers
            return new Response(body, {
                status: response.status,
                headers: {
                    'Content-Type': 'application/json',
                    'Access-Control-Allow-Origin': '*',
                    'Cache-Control': 'no-cache',
                },
            });
        } catch (error) {
            return new Response(JSON.stringify({ error: error.message }), {
                status: 500,
                headers: { 'Content-Type': 'application/json' },
            });
        }
    },
};
