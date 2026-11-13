import { describe, expect, it } from 'vitest'
import { purchaseLineAmount } from './purchaseAmounts'
describe('purchase payable amount', () => {
  it('adds tax to net prices and rounds each line', () => {
    expect(purchaseLineAmount({quantity:2,unitPrice:100,taxIncluded:true,taxRate:1})).toBe(202)
    expect(purchaseLineAmount({quantity:1,unitPrice:0.5,taxIncluded:true,taxRate:1})).toBe(0.51)
    expect(purchaseLineAmount({orderedQuantity:2,unitPrice:100,taxIncluded:true,taxRate:13})).toBe(226)
    expect(purchaseLineAmount({quantity:2,unitPrice:100,taxIncluded:false,taxRate:13})).toBe(200)
    expect(purchaseLineAmount({quantity:2,unitPrice:100,taxIncluded:true,taxRate:0})).toBe(200)
  })
})
