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
const userIds = []
let orderId
const fixtureSupplier = randomUUID(), fixtureProduct = randomUUID(), fixtureSku = randomUUID(), fixtureMaterial = randomUUID()
const suffix = randomBytes(6).toString('hex').toUpperCase()
const orderIds = []

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

  sql(`INSERT INTO supplier(id,name,phone,status,created_by,updated_by) VALUES('${fixtureSupplier}','合成采购验收供应商','000','ACTIVE','test','test');
    INSERT INTO product(id,product_code,product_code_normalized,name_zh,status,created_by,updated_by) VALUES('${fixtureProduct}','QA-${suffix}','QA-${suffix}','合成采购验收产品','ACTIVE','test','test');
    INSERT INTO product_variant(id,product_id,sku_code,sku_code_normalized,variant_name,status,attribute_signature) VALUES('${fixtureSku}','${fixtureProduct}','QA-SKU-${suffix}','QA-SKU-${suffix}','合成采购验收SKU','ACTIVE','${'a'.repeat(64)}');
    INSERT INTO product_variant_supplier(id,variant_id,supplier_id,primary_slot,created_by,updated_by) VALUES('${randomUUID()}','${fixtureSku}','${fixtureSupplier}','PRIMARY','test','test');
    INSERT INTO auxiliary_material(id,material_code,material_code_normalized,name,material_type,unit,status,created_by,updated_by) VALUES('${fixtureMaterial}','QA-MAT-${suffix}','QA-MAT-${suffix}','合成采购验收辅料','包装','PCS','ACTIVE','test','test');
    INSERT INTO auxiliary_material_supplier(id,auxiliary_material_id,supplier_id,primary_slot,created_by,updated_by) VALUES('${randomUUID()}','${fixtureMaterial}','${fixtureSupplier}','PRIMARY','test','test');`)
  const supplierId = fixtureSupplier, itemId = fixtureSku
  const token = await login('PRODUCT_MATERIAL')
  const references = await api(`/purchases/references?orderType=PRODUCT&supplierId=${supplierId}`, token)
  assert(references.some(item => item.id === itemId), 'Selected supplier must expose its linked SKU')

  const created = await api('/purchases/orders', token, 'POST', { orderType: 'PRODUCT', supplierId, currency: 'CNY', remark: '临时采购动态验收', expectedDeliveryDate: '2026-11-20', deliveryAddress: '合成验收仓', lines: [{ itemId, quantity: 2, unitPrice: 1 }] })
  orderId = created.id
  orderIds.push(orderId)
  assert.equal(created.expectedDeliveryDate, '2026-11-20'); assert.equal(created.deliveryAddress, '合成验收仓')
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
  const material = await api('/purchases/orders', token, 'POST', { orderType: 'MATERIAL', supplierId, currency: 'CNY', lines: [{ itemId: fixtureMaterial, quantity: 10, unitPrice: 2 }] })
  orderIds.push(material.id)
  await api(`/purchases/orders/${material.id}/submit`, token, 'POST', { version: 0 })
  await api(`/purchases/orders/${material.id}/receipts`, token, 'POST', { requestId: `QA-${randomUUID()}`, receivedAt: new Date().toISOString(), lines: [{ orderLineId: material.lines[0].id, quantity: 4 }] })
  await api(`/purchases/orders/${material.id}/cancel`, token, 'POST', { version: 2, reason: '' }, 400)
  const closed = await api(`/purchases/orders/${material.id}/cancel`, token, 'POST', { version: 2, reason: '合成验收关闭余量' })
  assert.equal(closed.cancellationReason, '合成验收关闭余量'); assert.equal(closed.lines[0].receivedQuantity, 4)
  assert.equal(Number(sql(`SELECT quantity FROM inventory_balance WHERE auxiliary_material_id='${fixtureMaterial}'`)), 4)
  await api(`/purchases/orders/${material.id}/receipts`, token, 'POST', { requestId: `QA-${randomUUID()}`, receivedAt: new Date().toISOString(), lines: [{ orderLineId: material.lines[0].id, quantity: 1 }] }, 409)
  console.log('PASS: material receipt updates inventory; closing remaining quantity preserves received stock and requires reason')
} finally {
  for (const id of orderIds) sql(`DELETE FROM purchase_receipt_line WHERE receipt_id IN (SELECT id FROM purchase_receipt WHERE order_id='${id}'); DELETE FROM purchase_receipt WHERE order_id='${id}'; DELETE FROM purchase_order_line WHERE order_id='${id}'; DELETE FROM purchase_order WHERE id='${id}';`)
  sql(`DELETE FROM inventory_transaction WHERE auxiliary_material_id='${fixtureMaterial}'; DELETE FROM inventory_balance WHERE auxiliary_material_id='${fixtureMaterial}'; DELETE FROM auxiliary_material_supplier WHERE auxiliary_material_id='${fixtureMaterial}'; DELETE FROM auxiliary_material WHERE id='${fixtureMaterial}'; DELETE FROM product_variant_supplier WHERE variant_id='${fixtureSku}'; DELETE FROM product_variant WHERE id='${fixtureSku}'; DELETE FROM product WHERE id='${fixtureProduct}'; DELETE FROM supplier WHERE id='${fixtureSupplier}';`)
  for (const id of userIds) sql(`DELETE FROM iam_session WHERE user_id='${id}'; UPDATE iam_user SET status='DISABLED' WHERE id='${id}';`)
  console.log('Cleaned synthetic purchase rows and disabled temporary QA accounts')
}
