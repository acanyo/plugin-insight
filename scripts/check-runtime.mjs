// Bounded read-only stability check. No monitoring data is deleted or modified.
import { setTimeout as delay } from 'node:timers/promises';
const base = process.env.HALO_TEST_URL || 'http://127.0.0.1:8091';
if (!process.env.HALO_TEST_PASSWORD) throw new Error('Set HALO_TEST_PASSWORD');
const duration = Number(process.env.HALO_TEST_SECONDS || 120);
if (!Number.isInteger(duration) || duration < 1 || duration > 600) throw new Error('Use 1–600 seconds');
const headers = { Authorization: 'Basic ' + Buffer.from(
  `${process.env.HALO_TEST_USER || 'admin'}:${process.env.HALO_TEST_PASSWORD}`).toString('base64') };
const started = Date.now();
const paths = ['overview', 'alerts/history?status=open&limit=10', 'diagnostics',
  'business-task-history', 'observations', 'deliveries', 'recordings', 'trends?range=15m',
  'monitor-overhead', 'plugin-detail?pluginId=insight&range=15m',
  'plugin-baseline?pluginId=insight&range=15m&reference=previous'];
let checks = 0, failures = 0, reported = 0;
do {
  await Promise.all(paths.map(async path => {
    checks++;
    try {
      const response = await fetch(`${base}/apis/console.api.insight.halo.run/v1alpha1/${path}`,
        { headers, signal: AbortSignal.timeout(12000) });
      const data = await response.json();
      if (response.status !== 200) throw new Error(`HTTP ${response.status}`);
      if (path === 'overview' && data.storage?.available === false) throw new Error('storage unavailable');
    } catch (error) {
      failures++;
      console.log(JSON.stringify({ path, failure: error.message }));
    }
  }));
  const elapsed = Date.now() - started;
  if (elapsed - reported >= 30000) {
    console.log(JSON.stringify({ elapsedSeconds: Math.round(elapsed / 1000), checks, failures }));
    reported = elapsed;
  }
  await delay(Math.min(2000, Math.max(0, duration * 1000 - elapsed)));
} while (Date.now() - started < duration * 1000);
console.log(JSON.stringify({ durationSeconds: duration, checks, failures }));
if (failures) process.exitCode = 1;
