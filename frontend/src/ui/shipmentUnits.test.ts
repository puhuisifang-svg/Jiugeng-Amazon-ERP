import { describe, expect, it } from 'vitest'
import { shipmentQuantitySummary, shipmentUnit } from './shipmentUnits'
describe('发货单位', () => {
  it('keeps legacy sets and never combines quantities of different units', () => {
    expect(shipmentUnit('SET')).toBe('套')
    expect(shipmentQuantitySummary([{ quantity: 0.1, quantityUnit: '米' }, { quantity: 0.2, quantityUnit: '米' }, { quantity: 2, quantityUnit: '件' }])).toBe('0.3 米 / 2 件')
  })
})
