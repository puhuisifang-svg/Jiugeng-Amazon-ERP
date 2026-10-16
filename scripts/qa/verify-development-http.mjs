// Run only against the local assigned MySQL environment. Synthetic rows are removed after verification.
import { execFileSync } from 'node:child_process'
import { randomBytes, randomUUID } from 'node:crypto'
import { readdirSync, readFileSync, writeFileSync } from 'node:fs'
import { homedir } from 'node:os'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import assert from 'node:assert/strict'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..')
const url = process.env.ERP_BACKEND_URL || 'http://127.0.0.1:8080'
assert(['localhost', '127.0.0.1'].includes(new URL(url).hostname), 'Local test host required')
const container = process.env.ERP_TEST_MYSQL_CONTAINER
const database = process.env.ERP_TEST_MYSQL_DATABASE
const config = process.env.ERP_TEST_MYSQL_CONFIG
assert(container && database?.match(/^[a-zA-Z0-9_]+$/) && config, 'Explicit database and protected client config required')
const image = 'mysql@sha256:64756cc92f707eb504496d774353990bcb0f6999ddf598b6ad188f2da66bd000'

function sql(statement) {
  return execFileSync('docker', ['run', '--rm', '-i', '--network', `container:${container}`, '-v', `${config}:/run/client.cnf:ro`, image, 'mysql', '--defaults-extra-file=/run/client.cnf', '--default-character-set=utf8mb4', '--batch', '--skip-column-names', database], { input: statement, encoding: 'utf8', stdio: ['pipe', 'pipe', 'pipe'] }).trim()
}
function jar(group, name) {
  const base = resolve(homedir(), '.m2/repository', group, name)
  for (const version of readdirSync(base).sort().reverse()) {
    const file = readdirSync(resolve(base, version)).find(value => value === `${name}-${version}.jar`)
    if (file) return resolve(base, version, file)
  }
  throw new Error(`Missing test dependency ${name}`)
}
const cp = [jar('org/springframework/security', 'spring-security-crypto'), jar('org/springframework', 'spring-jcl')].join(':')
const password = `T-${randomBytes(24).toString('hex')}`
const hash = execFileSync('java', ['--class-path', cp, resolve(root, 'scripts/qa/MonitoringPasswordHash.java')], { input: password, encoding: 'utf8', stdio: ['pipe', 'pipe', 'pipe'] })
const userIds = [], candidateIds = []
const stateFile = process.env.ERP_DEVELOPMENT_STATE
const verification = process.argv.includes('--verify-restart')
let productId = randomUUID(), retained = false
async function api(path, token, method = 'GET', body, expected = 200) {
  const response = await fetch(url + '/api/v1' + path, { method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) }, ...(body ? { body: JSON.stringify(body) } : {}) })
  assert.equal(response.status, expected, method + ' ' + path + ' returned ' + response.status)
  return response.json().catch(() => null)
}
async function login(role) {
  const id = randomUUID(), loginName = 'dev-qa-' + id.slice(0, 8)
  userIds.push(id)
  sql("INSERT INTO iam_user(id,login_name,display_name,password_hash,role,status,must_change_password) VALUES('" + id + "','" + loginName + "','临时新品验收','" + hash + "','" + role + "','ENABLED',FALSE);")
  return (await api('/auth/login', null, 'POST', { loginName, password })).token
}
const base = '/product-development'
try {
  await api(base, null, 'GET', undefined, 401)
  const selector = await login('SELECTION'), writer = await login('PRODUCT_MATERIAL'), admin = await login('ADMIN')
  if (verification) {
    assert(stateFile, 'State path required')
    const saved = JSON.parse(readFileSync(stateFile, 'utf8'))
    assert([...saved.candidateIds, saved.productId].every(id => /^[0-9a-f-]{36}$/.test(id)), 'Invalid fixture identifiers')
    candidateIds.push(...saved.candidateIds); productId = saved.productId
    const detail = await api(base + '/' + candidateIds[0], selector)
    assert.equal(detail.item.status, 'COMPLETED')
    assert.equal(detail.item.fields.purchaseCost, 12.3456)
    assert.equal(detail.product.id, productId)
    assert(detail.history.some(event => event.action === 'DEVELOPMENT_COMPLETED'))
    console.log('PASS: development snapshot, fields, linked product and history persist across MySQL/backend restart')
  } else {
    const asin = 'Z' + randomBytes(5).toString('hex').slice(0, 9).toUpperCase()
    const candidate = await api('/selection/candidates', selector, 'POST', { asinOrUrl: asin, title: '合成新品开发验收', score: 85 })
    const id = candidate.id; candidateIds.push(id)
    await api(base + '/' + id, selector, 'GET', undefined, 404)
    await api('/selection/candidates/' + id + '/transition', selector, 'POST', { status: 'REVIEWING' })
    await api('/selection/candidates/' + id + '/transition', selector, 'POST', { status: 'APPROVED' })
    await api('/selection/candidates/' + id + '/transition', selector, 'POST', { status: 'APPROVED' }, 409)
    let detail = await api(base + '/' + id, selector)
    assert.equal(detail.item.status, 'DRAFT'); assert.equal(detail.item.source.score, 85)
    assert.equal((await api(base + '?q=' + asin, selector)).total, 1)
    await api(base + '/' + id, selector, 'PUT', { version: 0, fields: detail.item.fields }, 403)
    await api(base + '/' + id + '/transition', writer, 'POST', { version: 0, target: 'IN_PROGRESS' }, 400)
    const fields = { ...detail.item.fields, developer: '合成开发员', startDate: '2026-10-16', endDate: '2026-11-01', purchaseCost: 12.3456 }
    await api(base + '/' + id, writer, 'PUT', { version: 0, fields })
    await api(base + '/' + id, writer, 'PUT', { version: 0, fields }, 409)
    await api(base + '/' + id + '/transition', writer, 'POST', { version: 1, target: 'IN_PROGRESS' })
    await api(base + '/' + id + '/transition', admin, 'POST', { version: 2, target: 'COMPLETED' }, 400)
    sql("INSERT INTO product(id,product_code,product_code_normalized,name_zh,product_name,status,created_by,updated_by) VALUES('" + productId + "','" + productId + "','" + productId + "','合成开发成果','合成开发成果','ACTIVE','test','test');")
    fields.linkedProductId = productId; fields.sampleNotes = '合成打样验收合格'
    await api(base + '/' + id, writer, 'PUT', { version: 2, fields })
    await api(base + '/' + id + '/transition', admin, 'POST', { version: 3, target: 'COMPLETED' })
    await api(base + '/' + id, admin, 'PUT', { version: 4, fields }, 409)
    detail = await api(base + '/' + id, selector)
    assert.equal(detail.product.id, productId)
    assert.equal(detail.item.source.title, '合成新品开发验收')
    console.log('PASS: real MySQL candidate approval, one draft, role restrictions, validation, optimistic locking and completion')
    if (stateFile) {
      writeFileSync(stateFile, JSON.stringify({ candidateIds, productId }), { mode: 0o600 })
      retained = true
    }
  }
} finally {
  if (!retained) {
    for (const id of candidateIds) sql("DELETE FROM product_development WHERE candidate_id='" + id + "'; DELETE FROM selection_candidate WHERE id='" + id + "';")
    sql("DELETE FROM product WHERE id='" + productId + "';")
  }
  for (const id of userIds) sql("DELETE FROM iam_session WHERE user_id='" + id + "'; UPDATE iam_user SET status='DISABLED' WHERE id='" + id + "';")
  console.log(retained ? 'Retained scoped development fixtures for restart verification; QA accounts disabled' : 'Removed scoped development fixtures; QA accounts disabled; audit evidence retained')
}
