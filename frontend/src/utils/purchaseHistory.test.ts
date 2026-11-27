import {expect,it} from 'vitest'
import {purchaseHistoryChanges} from './purchaseHistory'
import type {ProductHistoryDto} from '@/api/domain'
import type {PurchaseLine} from '@/api/purchases'
const event=(action:string,beforeValue?:Record<string,unknown>,afterValue?:Record<string,unknown>)=>({id:'synthetic',action,occurredAt:'',actorLoginName:'',actorDisplayName:'',beforeValue,afterValue}) as ProductHistoryDto
it('shows nonempty business fields for creation without technical identifiers or null transitions',()=>{
 const changes=purchaseHistoryChanges(event('PURCHASE_ORDER_CREATE',undefined,{id:'internal',supplierId:'supplier-id',version:1,orderNo:'TEST-PO',supplierName:'测试供应商',remark:'',waybillImageId:null,status:'DRAFT',lines:[{itemName:'测试商品',quantity:2,unitPrice:5,taxIncluded:false,taxRate:0}]}))
 expect(changes.every(c=>c.initial)).toBe(true);expect(changes.map(c=>c.label)).toEqual(['供应商','状态','采购商品'])
 expect(JSON.stringify(changes)).not.toMatch(/internal|supplier-id|TEST-PO|未填写|税率/)
 expect(changes.find(c=>c.label==='状态')?.after).toBe('草稿')
})
it('isolates an actual item quantity change and ignores numeric formatting and versions',()=>{
 const old={version:1,lines:[{itemName:'测试商品',quantity:2,unitPrice:'5.00'}]}
 const next={version:2,lines:[{itemName:'测试商品',quantity:3,unitPrice:5}]}
 expect(purchaseHistoryChanges(event('PURCHASE_ORDER_UPDATE',old,next))).toEqual([{key:'line:0:quantity',label:'测试商品 · 数量',before:'2',after:'3',initial:false}])
})
it('formats shipment line references with names and never exposes raw JSON or IDs',()=>{
 const changes=purchaseHistoryChanges(event('PURCHASE_SHIPMENT_CREATE',undefined,{shipmentId:'secret-shipment',trackingNumber:'TEST123',packageCount:1,lines:[{orderLineId:'secret-line',quantity:3}]}),[{id:'secret-line',itemName:'测试辅料'} as PurchaseLine])
 expect(JSON.stringify(changes)).toContain('测试辅料');expect(JSON.stringify(changes)).not.toContain('secret-')
 expect(purchaseHistoryChanges(event('PURCHASE_SHIPMENT_CREATE',undefined,{lines:[{orderLineId:'missing',quantity:2}]}))[0]?.after).toContain('历史商品 1')
})
it('preserves clearing values, image replacements and removed items as readable changes',()=>{
 const changes=purchaseHistoryChanges(event('PURCHASE_ORDER_UPDATE',{remark:'旧备注',waybillImageId:'old-image',lines:[{itemName:'移除商品',quantity:1}]},{remark:'',waybillImageId:'new-image',lines:[]}))
 expect(changes.map(c=>c.after)).toEqual(['未填写','已更换图片','已移除']);expect(changes.every(c=>!c.initial)).toBe(true)
})
it('ignores technical-only changes and keeps receipts understandable',()=>{
 expect(purchaseHistoryChanges(event('PURCHASE_ORDER_UPDATE',{version:1},{version:2}))).toEqual([])
 expect(purchaseHistoryChanges(event('PURCHASE_RECEIPT_CREATE',undefined,{receiptId:'private',requestId:'private',orderId:'private',lineCount:2,completed:false})).map(c=>[c.label,c.after])).toEqual([['收货项数','2'],['是否收齐','否']])
})
