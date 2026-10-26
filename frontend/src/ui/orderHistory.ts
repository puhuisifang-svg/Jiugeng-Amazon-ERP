import type { DashboardHistoryEvent } from '@/api/dashboard'
const fields: Record<string, string> = { source: '来源', externalNumber: '客户订单号', contractNumber: '合同号', orderDate: '下单日期', deliveryDate: '交期', invoiceRequired: '是否开票', invoiceTitle: '发票抬头', taxNumber: '税号', recipient: '收货人', phone: '电话', address: '地址', remark: '备注', name: '品名', specification: '规格型号', unit: '单位', quantity: '数量', netUnitPrice: '不含税单价', taxRate: '税率' }
function object(value: unknown): Record<string, unknown> {
  if (typeof value === 'string') { try { return object(JSON.parse(value)) } catch { return {} } }
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {}
}
const display = (v: unknown) => v === true ? '是' : v === false ? '否' : v === 'OFFLINE' ? '线下' : String(v ?? '') || '未填'
export function orderHistoryChanges(event: DashboardHistoryEvent): string[] {
  if (!event.beforeValue || !event.afterValue) return []
  const before = object(event.beforeValue), after = object(event.afterValue), result: string[] = []
  function compare(a: Record<string, unknown>, b: Record<string, unknown>, prefix: string) {
    for (const [key, label] of Object.entries(fields)) {
      const numeric = ['quantity', 'netUnitPrice', 'taxRate'].includes(key)
      if (numeric && a[key] != null && b[key] != null && Number(a[key]) === Number(b[key])) continue
      if (display(a[key]) !== display(b[key])) result.push(`${prefix}${label}：${display(a[key])} → ${display(b[key])}`)
    }
  }
  compare(object(before.details), object(after.details), '')
  const oldLines = Array.isArray(before.lines) ? before.lines.map(object) : []
  const newLines = Array.isArray(after.lines) ? after.lines.map(object) : []
  for (const line of newLines) {
    const old = oldLines.find(l => l.id === line.id), details = object(line.details)
    if (!old) result.push(`新增商品：${display(details.name)}`)
    else compare(object(old.details), details, `${display(details.name)} · `)
  }
  for (const line of oldLines) if (!newLines.some(l => l.id === line.id)) result.push(`移除商品：${display(object(line.details).name)}`)
  return result
}
