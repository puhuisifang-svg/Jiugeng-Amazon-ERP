const actions: Record<string, string> = {
  PRODUCT_NAME_STYLE_UPDATE: '修改 SKU 名称格式', PRODUCT_CREATE: '创建产品', PRODUCT_UPDATE: '修改产品资料', PRODUCT_DISABLE: '停用产品',
  PRODUCT_DELETE: '删除产品', PRODUCT_IMPORT_CREATE: '导入创建产品', PRODUCT_IMPORT_UPDATE: '导入更新产品',
  VARIANT_IMPORT_CREATE: '导入创建 SKU', VARIANT_IMPORT_UPDATE: '导入更新 SKU',
  PRODUCT_CUSTOMS_UPDATE: '修改报关资料', SKU_DOCUMENT_UPLOAD: '上传 SKU 文件',
  CREATE: '创建记录', UPDATE: '修改资料', DELETE: '删除记录', DISABLE: '停用记录',
}
export function productActionLabel(action: string) { return actions[action] ?? '其他操作' }
