const actions: Record<string, string> = {
  PRODUCT_SKU_CREATE: '创建子体', PRODUCT_SKU_UPDATE: '修改子体', PRODUCT_SKU_DELETE: '删除子体', PRODUCT_RESTORE: '恢复父体', PRODUCT_SKU_RESTORE: '恢复子体', PRODUCT_STOCK_INBOUND: '产品入库', PRODUCT_STOCK_OUTBOUND: '产品出库', PRODUCT_STOCK_ADJUST_IN: '产品盘盈', PRODUCT_STOCK_ADJUST_OUT: '产品盘亏',
  PRODUCT_NAME_STYLE_UPDATE: '修改 SKU 名称格式', PRODUCT_CREATE: '创建产品', PRODUCT_UPDATE: '修改产品资料', PRODUCT_DISABLE: '停用产品',
  PRODUCT_DELETE: '删除产品', PRODUCT_IMPORT_CREATE: '导入创建产品', PRODUCT_IMPORT_UPDATE: '导入更新产品',
  VARIANT_IMPORT_CREATE: '导入创建 SKU', VARIANT_IMPORT_UPDATE: '导入更新 SKU',
  PRODUCT_CUSTOMS_UPDATE: '修改报关资料', SKU_DOCUMENT_UPLOAD: '上传 SKU 文件',
  CREATE: '创建记录', UPDATE: '修改资料', DELETE: '删除记录', DISABLE: '停用记录',
}
export function productActionLabel(action: string) { return actions[action] ?? '其他操作' }

const fieldLabels: Record<string, string> = { productName: '产品名称', name: '名称', status: '状态', skuCount: '子体数', variantCount: '子体数', description: '描述', productType: '类型', version: '版本', variantName: '子体名称', packageWeightGrams: '包装重量（g）' }
function historyValue(value: unknown): string {
  if (value == null || value === '') return '未填写'
  if (Array.isArray(value)) return value.map(item => item == null ? '—' : historyValue(item)).join(' × ')
  if (typeof value === 'object') return JSON.stringify(value)
  if (typeof value === 'boolean') return value ? '是' : '否'
  return ({ ACTIVE: '启用', DISABLED: '停用', DRAFT: '待定' } as Record<string, string>)[String(value)] ?? String(value)
}
export function productHistoryChanges(event: { beforeValue?: Record<string, unknown>; afterValue?: Record<string, unknown> }) {
  const before = event.beforeValue ?? {}, after = event.afterValue ?? {}
  return [...new Set([...Object.keys(before), ...Object.keys(after)])]
    .filter(key => JSON.stringify(before[key]) !== JSON.stringify(after[key]))
    .map(key => ({ key, label: fieldLabels[key] ?? key, before: historyValue(before[key]), after: historyValue(after[key]) }))
}
