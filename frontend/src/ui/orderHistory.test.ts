import { expect, it } from 'vitest'
import { orderHistoryChanges } from './orderHistory'
it('describes per-line changes without mistaking decimal formatting for edits', () => {
  const result = orderHistoryChanges({ id: 'h', action: 'ORDER_UPDATE', actorLoginName: '', actorDisplayName: '', occurredAt: '', beforeValue: { lines: [{ id: 'a', details: { name: '测试商品', quantity: '3.000', netUnitPrice: '10.00' } }] }, afterValue: { lines: [{ id: 'a', details: { name: '测试商品', quantity: 5, netUnitPrice: 10 } }] } })
  expect(result).toEqual(['测试商品 · 数量：3.000 → 5'])
})
