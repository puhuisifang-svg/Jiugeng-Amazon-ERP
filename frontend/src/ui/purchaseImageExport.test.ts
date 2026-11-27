import { afterEach,describe,it,expect,vi } from 'vitest'
import { purchaseExportRows,exportPages,renderPurchasePage } from './purchaseImageExport'
import type { PurchaseOrder } from '@/api/purchases'
afterEach(()=>vi.restoreAllMocks())
describe('purchase image rows',()=>{
  it('preserves multiple items and quantities, orders dates and splits long exports',()=>{
    const orders=[{createdAt:'2026-11-09T00:00:00Z',lines:[{itemName:'旧',orderedQuantity:3,unit:'根'}]},{createdAt:'2026-11-10T20:00:00Z',lines:[{itemName:'新1',orderedQuantity:25,unit:'米'},{itemName:'新2',orderedQuantity:15,unit:'根'}]}] as PurchaseOrder[]
    const rows=purchaseExportRows(orders)
    expect(rows.map(r=>r.date)).toEqual(['2026-11-11','2026-11-11','2026-11-09'])
    expect(rows.map(r=>r.line.orderedQuantity)).toEqual([25,15,3])
    expect(exportPages(rows,2).map(p=>p.length)).toEqual([2,1])
    expect(exportPages([])).toEqual([])
  })
  it('keeps each order remark on its own item rows, including same-day orders',()=>{
    const orders=[{createdAt:'2026-11-10T10:00:00Z',remark:'注意颜色',lines:[{itemName:'甲'},{itemName:'乙'}]},{createdAt:'2026-11-10T09:00:00Z',remark:'分箱包装',lines:[{itemName:'丙'}]},{createdAt:'2026-11-10T08:00:00Z',lines:[{itemName:'丁'}]}] as PurchaseOrder[]
    expect(purchaseExportRows(orders).map(r=>r.remark)).toEqual(['注意颜色','注意颜色','分箱包装',''])
  })
  it('renders wrapped multiline remarks in bold red and expands rows to fit',async()=>{
    const drawn:Array<{text:string;color:string;font:string;y:number}>=[]
    const ctx={font:'',fillStyle:'',fillRect:vi.fn(),fillText(text:string,_x:number,y:number){drawn.push({text,color:this.fillStyle,font:this.font,y})},measureText:(text:string)=>({width:text.length*24}),beginPath:vi.fn(),moveTo:vi.fn(),lineTo:vi.fn(),stroke:vi.fn()}
    vi.spyOn(HTMLCanvasElement.prototype,'getContext').mockReturnValue(ctx as unknown as CanvasRenderingContext2D)
    let height=0
    vi.spyOn(HTMLCanvasElement.prototype,'toBlob').mockImplementation(function(this:HTMLCanvasElement,callback){height=this.height;callback(new Blob(['image'],{type:'image/png'}))})
    const remark='请单独包装\n'+ '红色备注'.repeat(30)
    const rows=purchaseExportRows([{createdAt:'2026-11-10T00:00:00Z',remark,lines:[{itemName:'测试品名',orderedQuantity:2,unit:'件'}]}] as PurchaseOrder[])
    await renderPurchasePage(rows,()=>{})
    expect(drawn.some(d=>d.text==='备注')).toBe(true)
    const red=drawn.filter(d=>d.color==='#b42318')
    expect(red.map(d=>d.text).join('')).toBe(remark.replace('\n',''))
    expect(red.every(d=>d.font.startsWith('bold ')&&d.y<height-10)).toBe(true)
    expect(height).toBeGreaterThan(270)
    expect(drawn.find(d=>d.text==='测试品名')?.color).toBe('#111')
    expect(drawn.find(d=>d.text==='2')?.color).toBe('#111')
  })
})

it('exports each item remark while preserving historical order notes',()=>{
 const rows=purchaseExportRows([{createdAt:'2026-11-27T01:00:00Z',remark:'整单要求',lines:[{remark:'商品甲包装'},{remark:'商品乙标签'},{}]} as PurchaseOrder])
 expect(rows.map(r=>r.remark)).toEqual(['商品甲包装\n整单要求','商品乙标签\n整单要求','整单要求'])
})
