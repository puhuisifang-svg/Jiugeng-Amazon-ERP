import { describe, expect, it } from 'vitest'
import { sampleLabelDocument, sampleLabelsDocument, sampleLabelFilename } from './sampleLabel'
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

it('builds separate printable labels without interpreting replacement characters or markup',()=>{
 const rows=[{skuName:'$& <b>name</b>',locationCode:'A-01'},{skuName:'Second',locationCode:'B-01'}]
 const doc=new DOMParser().parseFromString(sampleLabelsDocument(rows),'text/html')
 expect(doc.querySelectorAll('.label')).toHaveLength(2);expect(doc.querySelector('.name')?.textContent).toBe(rows[0]!.skuName);expect(doc.querySelector('b')).toBeNull()
 expect(()=>sampleLabelsDocument([])).toThrow();expect(()=>sampleLabelsDocument(Array.from({length:501},()=>rows[0]!))).toThrow()
})

it('includes the sample name and location in safe single-label filenames', () => {
  expect(sampleLabelFilename([{skuName:'合成样品',locationCode:'A-01'}])).toBe('样品标签-合成样品-A-01.pdf')
  expect(sampleLabelFilename([{skuName:'样品/名称',locationCode:'A:01'}])).toBe('样品标签-样品-名称-A-01.pdf')
  expect(sampleLabelFilename([{skuName:'合成样品',locationCode:'A-01'},{skuName:'另一款',locationCode:'B-01'}])).toBe('样品标签-合成样品等2款.pdf')
})
