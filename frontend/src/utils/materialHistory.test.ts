import { describe, expect, it } from 'vitest'
import { materialHistoryChanges } from './materialHistory'
import { productActionLabel } from './productHistory'
import type { ProductHistoryDto } from '@/api/domain'
const event:ProductHistoryDto={id:'test-event',action:'MATERIAL_CREATE',actorLoginName:'test',occurredAt:'2026-11-26T00:00:00Z'}
describe('readable material history',()=>{
  it('shows initial business fields without technical ids, versions or blank values',()=>{
    const changes=materialHistoryChanges({...event,afterValue:{id:'internal-id',version:1,materialCode:'M01',name:'测试辅料',status:'ACTIVE',remark:'',imageAssetId:'asset-id'}})
    expect(changes.map(c=>c.key)).toEqual(['materialCode','name','status','imageAssetId'])
    expect(changes.every(c=>c.initial)).toBe(true)
    expect(changes.at(-1)?.after).toBe('已设置图片')
  })
  it('preserves cleared fields in edits and limits safety changes to the threshold',()=>{
    expect(materialHistoryChanges({...event,action:'MATERIAL_UPDATE',beforeValue:{remark:'旧备注',version:1},afterValue:{remark:'',version:2}})).toEqual([{key:'remark',label:'备注',before:'旧备注',after:'未填写',initial:false}])
    const changes=materialHistoryChanges({...event,action:'INVENTORY_SAFETY_STOCK_UPDATE',beforeValue:{materialId:'m',safetyStock:3,lowStock:false},afterValue:{materialId:'m',safetyStock:8,lowStock:true}})
    expect(changes).toEqual([{key:'safetyStock',label:'安全库存',before:'3',after:'8',initial:false}])
    expect(productActionLabel('INVENTORY_SAFETY_STOCK_UPDATE')).toBe('修改安全库存')
  })
})
