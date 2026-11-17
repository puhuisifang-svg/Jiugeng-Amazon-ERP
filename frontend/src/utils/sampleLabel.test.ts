import { describe, expect, it } from 'vitest'
import { sampleLabelDocument } from './sampleLabel'
describe('sample label', () => {
  it('uses 70x30mm, shows all fields and renders location bold without executing supplied markup', () => {
    const doc = new DOMParser().parseFromString(sampleLabelDocument({skuName:'测试样品<img src=x>',specification:'30×20cm\n黑色',locationCode:'A-01'}),'text/html')
    expect(doc.querySelector('.name')?.textContent).toBe('测试样品<img src=x>')
    expect(doc.querySelector('img')).toBeNull()
    expect(doc.querySelector('.spec')?.textContent).toBe('30×20cm\n黑色')
    expect(doc.querySelector('.location')?.textContent).toBe('库位：A-01')
    expect(doc.querySelector('style')?.textContent).toContain('size:70mm 30mm')
    expect(doc.querySelector('style')?.textContent).toContain('font-weight:800')
  })
})
