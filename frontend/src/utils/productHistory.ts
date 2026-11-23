const actions: Record<string, string> = {
 PURCHASE_ORDER_CREATE:"创建采购单",PURCHASE_ORDER_UPDATE:"修改采购单",PURCHASE_ORDER_SUBMIT:"确认下单",PURCHASE_ORDER_CANCEL:"关闭未收余量",PURCHASE_ORDER_DELETE:"删除采购单",PURCHASE_RECEIPT_CREATE:"登记收货",PURCHASE_SHIPMENT_CREATE:"登记发货批次",PURCHASE_LOGISTICS_UPDATE:"更新物流",
  SUPPLIER_CREATE:'创建供应商',SUPPLIER_UPDATE:'修改供应商',SUPPLIER_DELETE:'删除供应商',SUPPLIER_ARCHIVED_LINK_REMOVE:'解除已删除产品的历史关联',
  MATERIAL_IMAGE_COPY:'复制辅料图片', MATERIAL_IMPORT_CREATE:'导入辅料', MATERIAL_IMPORT_UPDATE:'导入修改辅料', MATERIAL_CREATE:'创建辅料', MATERIAL_UPDATE:'修改辅料', MATERIAL_DISABLE:'停用辅料', MATERIAL_ENABLE:'启用辅料', MATERIAL_DELETE:'删除辅料', MATERIAL_SUPPLIERS_UPDATE:'修改供应商关联',

  PRODUCT_SKU_CREATE: '创建子体', PRODUCT_SKU_UPDATE: '修改子体', PRODUCT_SKU_DELETE: '删除子体', PRODUCT_RESTORE: '恢复父体', PRODUCT_SKU_RESTORE: '恢复子体', PRODUCT_STOCK_INBOUND: '产品入库', PRODUCT_STOCK_OUTBOUND: '产品出库', PRODUCT_STOCK_ADJUST_IN: '产品盘盈', PRODUCT_STOCK_ADJUST_OUT: '产品盘亏',
  PRODUCT_NAME_STYLE_UPDATE: '修改 SKU 名称格式', PRODUCT_CREATE: '创建产品', PRODUCT_UPDATE: '修改产品资料', PRODUCT_DISABLE: '停用产品',
  PRODUCT_DELETE: '删除产品', PRODUCT_IMPORT_CREATE: '导入创建产品', PRODUCT_IMPORT_UPDATE: '导入更新产品',
  VARIANT_IMPORT_CREATE: '导入创建 SKU', VARIANT_IMPORT_UPDATE: '导入更新 SKU',
  PRODUCT_CUSTOMS_UPDATE: '修改报关资料', SKU_DOCUMENT_UPLOAD: '上传 SKU 文件',
  CREATE: '创建记录', UPDATE: '修改资料', DELETE: '删除记录', DISABLE: '停用记录',
}
export function productActionLabel(action: string) { return actions[action] ?? '其他操作' }

const fieldLabels: Record<string, string> = {orderNo:"采购单号",orderType:"采购类型",supplierName:"供应商",expectedDeliveryDate:"预计到货",deliveryAddress:"收货信息",cancellationReason:"关闭原因",carrier:"物流公司",trackingNumber:"运单号",waybillImageId:"运单图片",currency:"币种",lines:"采购明细",shipmentId:"发货批次",packageCount:"包裹数",receiptId:"收货记录",lineCount:"收货项数",completed:"是否收齐", contactPerson:'联系人',phone:'电话（脱敏）',wechatId:'微信（脱敏）',sourceUrl:'采购来源',sourceHost:'采购来源域名',qrImageHost:'联系图片域名',qrImageId:'联系图片',categories:'分类', materialType:'辅料类型',specification:'规格',unit:'基础单位',remark:'备注',imageAssetId:'受控图片',imageUrl:'图片链接',suppliers:'供应商关联',materialCode:'辅料编码',productName: '产品名称', name: '名称', status: '状态', skuCount: '子体数', variantCount: '子体数', description: '描述', productType: '类型', version: '版本', variantName: '子体名称', packageWeightGrams: '包装重量（g）' }
function historyValue(value: unknown): string {
  if (value == null || value === '') return '未填写'
  if (Array.isArray(value)) return value.map(item => item == null ? '—' : historyValue(item)).join(' × ')
  if (typeof value === 'object') { const line=value as {itemName?:string;quantity?:number;received?:number;unitPrice?:number;taxIncluded?:boolean;taxRate?:number;packageCount?:number};if(line.itemName)return `${line.itemName} · 数量 ${line.quantity??'—'} · 已收 ${line.received??0} · 未税单价 ${line.unitPrice??'—'} · 税率 ${line.taxIncluded?line.taxRate??0:0}% · 包裹 ${line.packageCount??'—'}`; const supplier=value as {supplierName?:string;primary?:boolean;remark?:string;purchaseUrl?:string;status?:string}; if(supplier.supplierName)return `${supplier.supplierName}${supplier.primary?'（主供）':''}${supplier.status==='DISABLED'?'（已停用）':''}${supplier.purchaseUrl?' · '+supplier.purchaseUrl:''}${supplier.remark?' · '+supplier.remark:''}`; return JSON.stringify(value) }
  if (typeof value === 'boolean') return value ? '是' : '否'
  return ({ SUBMITTED:'待收货',PARTIALLY_RECEIVED:'部分收货',COMPLETED:'已完成',CANCELLED:'已取消/余量关闭',MANUAL:'日常物品',PRODUCT:'产品', MATERIAL:'辅料', ACTIVE: '启用', DISABLED: '停用', DRAFT: '待定' } as Record<string, string>)[String(value)] ?? String(value)
}
export function productHistoryChanges(event: { beforeValue?: Record<string, unknown>; afterValue?: Record<string, unknown> }) {
  const before = event.beforeValue ?? {}, after = event.afterValue ?? {}
  return [...new Set([...Object.keys(before), ...Object.keys(after)])]
    .filter(key => JSON.stringify(before[key]) !== JSON.stringify(after[key]))
    .map(key => ({ key, label: fieldLabels[key] ?? key, before: historyValue(before[key]), after: historyValue(after[key]) }))
}
