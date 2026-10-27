// No credentials are used: an empty login must reach validation, not CORS rejection.
const base = new URL(process.argv[2]);
if (base.protocol !== 'https:') throw new Error('Expected public HTTPS origin');
const endpoint = new URL('/api/v1/auth/login', base);
for (const [origin, expected] of [[base.origin, 400], ['https://untrusted.invalid', 403]]) {
  const response = await fetch(endpoint, {
    method: 'POST',
    headers: { Origin: origin, 'Content-Type': 'application/json' },
    body: '{}',
    signal: AbortSignal.timeout(15000),
  });
  const body = await response.text();
  if (response.status !== expected || (expected === 400 && !body.includes('INVALID_REQUEST'))) {
    throw new Error(`Login origin check failed: expected ${expected}, received ${response.status}`);
  }
  console.log(`PASS login origin ${origin}: ${expected}`);
}
