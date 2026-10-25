import { describe, expect, it } from 'vitest'
import { productActionLabel } from './productHistory'
describe('product history operation labels', () => {
  it.each([['PRODUCT_CREATE', '创建产品'], ['PRODUCT_UPDATE', '修改产品资料'], ['PRODUCT_DISABLE', '停用产品'], ['PRODUCT_IMPORT_UPDATE', '导入更新产品'], ['SKU_DOCUMENT_UPLOAD', '上传 SKU 文件'], ['unknown', '其他操作']])('%s is readable', (action, label) => {
    expect(productActionLabel(action)).toBe(label)
  })
})
