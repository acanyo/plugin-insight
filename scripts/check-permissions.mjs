// Local integration test: temporary users only, no notification sending or plugin stopping.
import { randomBytes } from 'node:crypto';
import { setTimeout as delay } from 'node:timers/promises';

const base = process.env.HALO_TEST_URL || 'http://127.0.0.1:8091';
if (!['127.0.0.1', 'localhost', '[::1]'].includes(new URL(base).hostname)) {
  throw new Error('Permission fixtures may only run against a local development Halo');
}
if (!process.env.HALO_TEST_PASSWORD) throw new Error('Set HALO_TEST_PASSWORD');
const basic = (user, password) => 'Basic ' + Buffer.from(`${user}:${password}`).toString('base64');
const admin = basic(process.env.HALO_TEST_USER || 'admin', process.env.HALO_TEST_PASSWORD);
const group = '/apis/console.api.insight.halo.run/v1alpha1/';
const created = [];
const results = [];

async function request(path, authorization, method = 'GET', body) {
  const response = await fetch(base + path, {
    method, redirect: 'manual', signal: AbortSignal.timeout(15000),
    headers: { ...(authorization ? { Authorization: authorization } : {}),
      ...(body ? { 'Content-Type': 'application/json' } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  await response.arrayBuffer(); // Never print passwords, raw logs, or API response payloads.
  return response.status;
}

try {
  for (const [profile, role] of [['guest', 'guest'], ['view', 'insight-role-view-monitoring'],
    ['manage', 'insight-role-manage-monitoring'], ['diagnose', 'insight-role-diagnose']]) {
    const name = `insight-check-${profile}-${randomBytes(5).toString('hex')}`;
    const password = randomBytes(24).toString('base64url');
    const status = await request('/apis/api.console.halo.run/v1alpha1/users', admin, 'POST',
      { name, password, email: `${name}@example.invalid`, displayName: 'Insight temporary regression user', roles: [role] });
    if (status < 200 || status >= 300) throw new Error(`Fixture creation failed: ${profile} HTTP ${status}`);
    created.push(name);
    const auth = basic(name, password);
    let ready = false;
    for (let attempt = 0; attempt < 20; attempt++) {
      if (await request('/apis/api.console.halo.run/v1alpha1/users/-', auth) === 200) { ready = true; break; }
      await delay(500);
    }
    if (!ready) throw new Error(`Fixture login did not become ready: ${profile}`);
    const viewer = profile !== 'guest';
    const manager = ['manage', 'diagnose'].includes(profile);
    const diagnose = profile === 'diagnose';
    const cases = [
      ['GET', 'overview', viewer ? 200 : 403],
      ['GET', 'alerts/history?status=open&limit=1', viewer ? 200 : 403],
      ['GET', 'diagnostics', viewer ? 200 : 403],
      ['GET', 'business-tasks', viewer ? 200 : 403],
      ['GET', 'observations', viewer ? 200 : 403],
      ['GET', 'deliveries', manager ? 200 : 403],
      ['GET', 'diagnostic-logs?keyword=insight-regression-no-match', diagnose ? 200 : 403],
      ['GET', 'diagnostic-threads', diagnose ? 200 : 403],
      ['GET', 'recordings', diagnose ? 200 : 403],
      ['GET', 'recordings/00000000-0000-0000-0000-000000000000/download', diagnose ? 400 : 403],
      ['POST', 'recordings?seconds=1', diagnose ? 400 : 403],
      ['POST', 'observations?pluginId=insight', manager ? 400 : 403],
      ['POST', 'observations/missing/confirm', manager ? 400 : 403],
    ];
    for (const [method, path, expected] of cases) {
      const actual = await request(group + path, auth, method);
      results.push({ profile, method, path, expected, actual, passed: actual === expected });
    }
    console.log(JSON.stringify({ profile, cases: cases.length,
      failures: results.filter(r => r.profile === profile && !r.passed) }));
  }
  for (const path of ['diagnostics', 'diagnostic-logs', 'diagnostic-threads', 'recordings', 'deliveries']) {
    const actual = await request(group + path);
    results.push({ profile: 'anonymous', path, actual, passed: [302, 401, 403].includes(actual) });
  }
} finally {
  for (const name of created.reverse()) {
    const status = await request(`/api/v1alpha1/users/${name}`, admin, 'DELETE');
    if (![200, 202, 204, 404].includes(status)) throw new Error(`Unable to delete owned fixture ${name}: HTTP ${status}`);
    let removed = false;
    for (let attempt = 0; attempt < 20; attempt++) {
      if (await request(`/api/v1alpha1/users/${name}`, admin) === 404) { removed = true; break; }
      await delay(500);
    }
    if (!removed) throw new Error(`Fixture deletion pending: ${name}`);
  }
  console.log(JSON.stringify({ temporaryUsersRemoved: created.length }));
}
console.log(JSON.stringify({ checks: results.length, passed: results.filter(r => r.passed).length }));
if (results.some(r => !r.passed)) process.exitCode = 1;
