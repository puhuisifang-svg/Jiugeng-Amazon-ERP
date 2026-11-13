import { describe,it,expect } from 'vitest'
import { purchaseExportRows,exportPages } from './purchaseImageExport'
import type { PurchaseOrder } from '@/api/purchases'
describe('purchase image rows',()=>{
  it('preserves multiple items and quantities, orders dates and splits long exports',()=>{
    const orders=[{createdAt:'2026-11-09T00:00:00Z',lines:[{itemName:'旧',orderedQuantity:3,unit:'根'}]},{createdAt:'2026-11-10T20:00:00Z',lines:[{itemName:'新1',orderedQuantity:25,unit:'米'},{itemName:'新2',orderedQuantity:15,unit:'根'}]}] as PurchaseOrder[]
    const rows=purchaseExportRows(orders)
    expect(rows.map(r=>r.date)).toEqual(['2026-11-11','2026-11-11','2026-11-09'])
    expect(rows.map(r=>r.line.orderedQuantity)).toEqual([25,15,3])
    expect(exportPages(rows,2).map(p=>p.length)).toEqual([2,1])
    expect(exportPages([])).toEqual([])
  })
})
