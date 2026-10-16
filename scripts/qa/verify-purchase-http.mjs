// Run only against the local assigned MySQL environment. Synthetic rows are removed after verification.
import { execFileSync } from 'node:child_process'
import { randomBytes, randomUUID } from 'node:crypto'
import { readdirSync } from 'node:fs'
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
  return execFileSync('docker', ['run', '--rm', '-i', '--network', `container:${container}`, '-v', `${config}:/run/client.cnf:ro`, image, 'mysql', '--defaults-extra-file=/run/client.cnf', '--batch', '--skip-column-names', database], { input: statement, encoding: 'utf8', stdio: ['pipe', 'pipe', 'pipe'] }).trim()
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
const userIds = []
let orderId

async function api(path, token, method = 'GET', body, expected = 200) {
  const response = await fetch(`${url}/api/v1${path}`, { method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) }, ...(body ? { body: JSON.stringify(body) } : {}) })
  assert.equal(response.status, expected, `${method} ${path} returned ${response.status}`)
  return response.json().catch(() => null)
}
async function login(role) {
  const id = randomUUID(), loginName = `purchase-qa-${id.slice(0, 8)}`
  userIds.push(id)
  sql(`INSERT INTO iam_user(id,login_name,display_name,password_hash,role,status,must_change_password) VALUES('${id}','${loginName}','临时采购验收','${hash}','${role}','ENABLED',FALSE);`)
  return (await api('/auth/login', null, 'POST', { loginName, password })).token
}

try {
  await api('/purchases/orders', null, 'GET', undefined, 401)
  const selectionToken = await login('SELECTION')
  await api('/purchases/orders', selectionToken, 'GET', undefined, 403)

  const linked = sql(`SELECT pvs.supplier_id,pv.id FROM product_variant_supplier pvs JOIN supplier s ON s.id=pvs.supplier_id AND s.status='ACTIVE' JOIN product_variant pv ON pv.id=pvs.variant_id AND pv.status='ACTIVE' JOIN product p ON p.id=pv.product_id AND p.status='ACTIVE' LIMIT 1`).split('\t')
  assert.equal(linked.length, 2, 'An active supplier-linked SKU is required')
  const [supplierId, itemId] = linked
  const token = await login('PRODUCT_MATERIAL')
  const references = await api(`/purchases/references?orderType=PRODUCT&supplierId=${supplierId}`, token)
  assert(references.some(item => item.id === itemId), 'Selected supplier must expose its linked SKU')

  const created = await api('/purchases/orders', token, 'POST', { orderType: 'PRODUCT', supplierId, currency: 'CNY', remark: '临时采购动态验收', lines: [{ itemId, quantity: 2, unitPrice: 1 }] })
  orderId = created.id
  assert.equal(created.status, 'DRAFT')
  const submitted = await api(`/purchases/orders/${orderId}/submit`, token, 'POST', { version: created.version })
  assert.equal(submitted.status, 'SUBMITTED')
  const lineId = submitted.lines[0].id
  const requestId = `PURCHASE-QA-${randomUUID()}`
  const receipt = { requestId, receivedAt: new Date().toISOString(), remark: '临时分批收货验收', lines: [{ orderLineId: lineId, quantity: 1 }] }
  assert.equal((await api(`/purchases/orders/${orderId}/receipts`, token, 'POST', receipt)).replayed, false)
  assert.equal((await api(`/purchases/orders/${orderId}/receipts`, token, 'POST', receipt)).replayed, true)
  const detail = await api(`/purchases/orders/${orderId}/detail`, token)
  assert.equal(detail.order.status, 'PARTIALLY_RECEIVED')
  assert.equal(detail.receipts.length, 1)
  assert(detail.history.some(event => event.action === 'PURCHASE_RECEIPT_CREATE'))
  assert.equal(detail.receipts[0].lines[0].inventoryTransactionId, null)
  await api(`/purchases/orders/${orderId}/receipts`, token, 'POST', { ...receipt, requestId: `PURCHASE-QA-${randomUUID()}` })
  assert.equal((await api(`/purchases/orders/${orderId}/detail`, token)).order.status, 'COMPLETED')
  console.log('PASS: real MySQL purchase supplier scope, create, submit, partial receipt, replay, detail timeline and completion')
} finally {
  if (orderId) sql(`DELETE FROM purchase_receipt_line WHERE receipt_id IN (SELECT id FROM purchase_receipt WHERE order_id='${orderId}'); DELETE FROM purchase_receipt WHERE order_id='${orderId}'; DELETE FROM purchase_order_line WHERE order_id='${orderId}'; DELETE FROM purchase_order WHERE id='${orderId}'; DELETE FROM audit_event WHERE resource_type='PURCHASE_ORDER' AND resource_id='${orderId}';`)
  for (const id of userIds) sql(`DELETE FROM iam_session WHERE user_id='${id}'; UPDATE iam_user SET status='DISABLED' WHERE id='${id}';`)
  console.log('Cleaned synthetic purchase rows and disabled temporary QA accounts')
}
