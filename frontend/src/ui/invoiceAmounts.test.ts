import { describe, expect, it } from 'vitest'
import { invoiceAmounts } from './invoiceAmounts'

describe('开票金额精确到分', () => {
  it('calculates default one percent and half-up rounding without floating point', () => {
    expect(invoiceAmounts('3', '10', '1')).toEqual({ net: '30.00', tax: '0.30', total: '30.30' })
    expect(invoiceAmounts('1.005', '1', '100')).toEqual({ net: '1.01', tax: '1.01', total: '2.02' })
    expect(invoiceAmounts('1', '0.5', '1')).toEqual({ net: '0.50', tax: '0.01', total: '0.51' })
    expect(invoiceAmounts('999999999.999', '99999999.999999', '100')?.total).toBe('199999999999798000.00')
    expect(invoiceAmounts(1, 0, 0)?.total).toBe('0.00')
  })
  it('rejects missing, negative, overprecision and out-of-range amounts', () => {
    for (const [q, p, r] of [['0', '1', '1'], ['1', '', '1'], ['1', '-1', '1'], ['1.0001', '1', '1'], ['1', '1.0000001', '1'], ['1', '1', '101'], ['1', '1', '1.001'], ['1e3', '1', '1'], ['1', '100000000', '1']]) {
      expect(invoiceAmounts(q!, p!, r!)).toBeNull()
    }
  })
})
