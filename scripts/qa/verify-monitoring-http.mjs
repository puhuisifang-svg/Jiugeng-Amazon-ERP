// Run only against the local assigned MySQL environment. All generated rows are scoped to this run.
import { execFileSync } from 'node:child_process'
import { randomBytes, randomUUID } from 'node:crypto'
import { readdirSync, readFileSync, writeFileSync } from 'node:fs'
import { homedir } from 'node:os'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import assert from 'node:assert/strict'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..')
const url = process.env.ERP_BACKEND_URL || 'http://127.0.0.1:18080'
assert(['localhost', '127.0.0.1'].includes(new URL(url).hostname), 'Local test host required')
const container = process.env.ERP_TEST_MYSQL_CONTAINER
const database = process.env.ERP_TEST_MYSQL_DATABASE
const config = process.env.ERP_TEST_MYSQL_CONFIG
assert(container && database?.match(/^[a-zA-Z0-9_]+$/) && config, 'Explicit database and protected client config required')
const image = 'mysql@sha256:64756cc92f707eb504496d774353990bcb0f6999ddf598b6ad188f2da66bd000'
function sql(statement) {
  // The config file is mounted read-only; neither passwords nor session tokens go to output.
  return execFileSync('docker', ['run', '--rm', '-i', '--network', `container:${container}`, '-v', `${config}:/run/client.cnf:ro`, image, 'mysql', '--defaults-extra-file=/run/client.cnf', '--default-character-set=utf8mb4', '--batch', '--skip-column-names', database], { input: statement, encoding: 'utf8', stdio: ['pipe', 'pipe', 'pipe'] }).trim()
}
function jar(group, name) {
  const base = resolve(homedir(), '.m2/repository', group, name)
  for (const version of readdirSync(base).sort().reverse()) {
    const files = readdirSync(resolve(base, version))
    const file = files.find(f => f === `${name}-${version}.jar`)
    if (file) return resolve(base, version, file)
  }
  throw new Error(`Missing test dependency ${name}`)
}
const cp = [jar('org/springframework/security', 'spring-security-crypto'), jar('org/springframework', 'spring-jcl')].join(':')
const password = `T-${randomBytes(24).toString('hex')}`
const hash = execFileSync('java', ['--class-path', cp, resolve(root, 'scripts/qa/MonitoringPasswordHash.java')], { input: password, encoding: 'utf8', stdio: ['pipe', 'pipe', 'pipe'] })
const userIds = [], targetIds = [], requestIds = []
const stateFile = process.env.ERP_MONITOR_STATE
const verification = process.argv.includes('--verify-restart')
async function api(path, token, method = 'GET', body, expected = 200) {
  const response = await fetch(`${url}/api/v1${path}`, { method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) }, ...(body ? { body: JSON.stringify(body) } : {}) })
  assert.equal(response.status, expected, `${method} ${path} returned ${response.status}`)
  return response.json().catch(() => null)
}
async function login(role) {
  const id = randomUUID(), loginName = `monitor-qa-${id.slice(0, 8)}`
  userIds.push(id)
  sql(`INSERT INTO iam_user(id,login_name,display_name,password_hash,role,status,must_change_password) VALUES('${id}','${loginName}','临时监控验收','${hash}','${role}','ENABLED',FALSE);`)
  return (await api('/auth/login', null, 'POST', { loginName, password })).token
}
const base = '/product-monitoring'
try {
  await api(base, null, 'GET', undefined, 401)
  if (verification) {
    assert(stateFile, 'State file required')
    const saved = JSON.parse(readFileSync(stateFile, 'utf8'))
    assert([...saved.targetIds, ...saved.requestIds].every(id => /^[0-9a-f-]{36}$/.test(id)), 'Invalid QA record IDs')
    targetIds.push(...saved.targetIds); requestIds.push(...saved.requestIds)
    const token = await login('SELECTION')
    for (const id of targetIds) {
      const detail = await api(`${base}/${id}`, token)
      assert.equal(detail.target.fields.title, '合成验收商品 B')
      assert.equal((await api(`${base}/${id}/snapshots`, token)).total, 4)
    }
    console.log('PASS: monitoring HTTP reads after restart preserved targets, snapshots and baselines')
  } else {
    for (const role of ['ADMIN', 'PRODUCT_MATERIAL', 'SELECTION']) {
      const token = await login(role), asin = `Z${randomBytes(5).toString('hex').slice(0, 9).toUpperCase()}`
      const requestId = randomUUID(); requestIds.push(requestId)
      const input = { requestId, asins: [asin], tags: ['合成验收'], remark: '临时动态验收数据，脚本结束清理' }
      const created = await api(base, token, 'POST', input), id = created[0].id; targetIds.push(id)
      assert.deepEqual(await api(base, token, 'POST', input), created)
      const field = { title: '合成验收商品 A', brand: null, imageUrl: null, bullets: ['描述一'], price: 10, currency: 'USD', availability: 'AVAILABLE' }
      const first = { requestId: randomUUID(), version: 0, observedAt: '2026-01-01T00:00:00Z', source: 'SYNTHETIC', quality: 'COMPLETE', fields: field }
      assert.equal((await api(`${base}/${id}/snapshots`, token, 'POST', first)).decision, 'BASELINE')
      const second = { ...first, requestId: randomUUID(), version: 1, observedAt: '2026-01-02T00:00:00Z', fields: { ...field, title: '合成验收商品 B', price: 12 } }
      const result = await api(`${base}/${id}/snapshots`, token, 'POST', second)
      assert.equal(result.decision, 'CHANGED'); assert.equal(result.changes.length, 2)
      assert.deepEqual(await api(`${base}/${id}/snapshots`, token, 'POST', second), result)
      assert.equal((await api(`${base}/${id}/snapshots`, token, 'POST', { ...second, requestId: randomUUID(), version: 2, observedAt: '2026-01-03T00:00:00Z' })).decision, 'UNCHANGED')
      assert.equal((await api(`${base}/${id}/snapshots`, token, 'POST', { ...first, requestId: randomUUID(), version: 3 })).decision, 'OUT_OF_ORDER')
      assert.equal((await api(`${base}/batch`, token, 'POST', { targets: [{ id, version: 4 }], action: 'PAUSE' }))[0].status, 'UPDATED')
      await api(`${base}/${id}/snapshots`, token, 'POST', { ...second, requestId: randomUUID(), version: 5 }, 409)
      assert.equal((await api(`${base}/batch`, token, 'POST', { targets: [{ id, version: 5 }], action: 'ENABLE' }))[0].status, 'UPDATED')
      await api(`${base}/${id}/follow`, token, 'PUT', { followed: true })
      await api(`${base}/preferences`, token, 'PUT', { columns: ['price'] })
      const other = await login('SELECTION')
      assert.equal((await api(`${base}/${id}`, other)).target.followed, false)
      assert.equal((await api(`${base}/preferences`, other)).columns.length, 7)
      console.log(`PASS: ${role} create/baseline/change/replay/unchanged/old/pause/resume/preferences over HTTP`)
    }
    if (stateFile) writeFileSync(stateFile, JSON.stringify({ targetIds, requestIds }), { mode: 0o600 })
  }
} finally {
  // Preserve application audit events. Retire only the ephemeral QA identities and their sessions.
  for (const id of userIds) {
    sql(`DELETE FROM monitor_preference WHERE user_id='${id}'; DELETE FROM iam_session WHERE user_id='${id}'; UPDATE iam_user SET status='DISABLED' WHERE id='${id}';`)
  }
  if (!stateFile || verification) {
    for (const id of targetIds) sql(`DELETE FROM monitor_snapshot WHERE target_id='${id}'; DELETE FROM monitor_target WHERE id='${id}';`)
    for (const id of requestIds) sql(`DELETE FROM monitor_request WHERE request_id='${id}';`)
    console.log('Cleaned this run’s synthetic monitoring rows; retained security records and disabled QA accounts')
  }
}
