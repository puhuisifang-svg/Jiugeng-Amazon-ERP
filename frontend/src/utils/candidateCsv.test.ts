import { afterEach, describe, expect, it, vi } from 'vitest'

import { buildCandidatesCsv, candidateCsvHeaders, downloadCandidatesCsv, escapeCsvValue } from './candidateCsv'

describe('candidate CSV export', () => {
  afterEach(() => vi.restoreAllMocks())

  it('uses a UTF-8 BOM, stable headers, and standard CSV escaping', () => {
    const csv = buildCandidatesCsv([{
      id: 'candidate-1',
      asin: 'B0CSV123456',
      title: '标题含,逗号与"引号"\n换行',
      status: 'REVIEWING',
      score: 88,
      tags: ['户外', '新品'],
    }])

    expect(csv.startsWith('\uFEFF')).toBe(true)
    expect(csv.slice(1).split('\r\n')[0]).toBe(candidateCsvHeaders.join(','))
    expect(csv).toContain('"标题含,逗号与""引号""\n换行"')
    expect(csv).toContain('户外、新品')
  })

  it.each(['=1+1', '+SUM(A1:A2)', '-2+3', '@command', '  =hidden'])('protects formula-like text %s', (value) => {
    expect(escapeCsvValue(value)).toBe(`'${value}`)
  })

  it('downloads the generated CSV and revokes its object URL', () => {
    const createObjectURL = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:candidate-export')
    const revokeObjectURL = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined)
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined)

    downloadCandidatesCsv([{ id: 'candidate-1', asin: 'B0CSV123456', title: '候选', status: 'NEW' }])

    expect(createObjectURL).toHaveBeenCalledOnce()
    expect(click).toHaveBeenCalledOnce()
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:candidate-export')
  })
})
