import { describe, expect, it } from 'vitest'
import { productActionLabel, productHistoryChanges } from './productHistory'
describe('product history operation labels', () => {
  it.each([['PRODUCT_CREATE', '创建产品'], ['PRODUCT_UPDATE', '修改产品资料'], ['PRODUCT_DISABLE', '停用产品'], ['PRODUCT_IMPORT_UPDATE', '导入更新产品'], ['SKU_DOCUMENT_UPLOAD', '上传 SKU 文件'], ['unknown', '其他操作']])('%s is readable', (action, label) => {
    expect(productActionLabel(action)).toBe(label)
  })
})
describe('product history comparisons', () => {
  it('shows removed values and preserves zero with translated statuses', () => {
    expect(productHistoryChanges({ beforeValue: { status: 'ACTIVE', description: '旧说明', skuCount: 3 }, afterValue: { status: 'DISABLED', skuCount: 0 } })).toEqual([
      { key: 'status', label: '状态', before: '启用', after: '停用' },
      { key: 'description', label: '描述', before: '旧说明', after: '未填写' },
      { key: 'skuCount', label: '子体数', before: '3', after: '0' },
    ])
  })
  it('omits unchanged fields and displays missing dimensions without inventing values', () => {
    expect(productHistoryChanges({ beforeValue: { 品名: '合成子体', 产品规格: [1, null, 3] }, afterValue: { 品名: '合成子体', 产品规格: [1, 2, 3] } })).toEqual([
      { key: '产品规格', label: '产品规格', before: '1 × — × 3', after: '1 × 2 × 3' },
    ])
  })
})
