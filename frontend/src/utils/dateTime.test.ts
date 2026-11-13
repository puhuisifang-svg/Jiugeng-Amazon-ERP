import { describe, expect, it } from 'vitest'
import { beijingDate, beijingDateTime, beijingDateTimeInput, beijingInputToIso } from './dateTime'
describe('Beijing business time', () => {
  it('converts timestamps across year and day boundaries without browser timezone dependence', () => {
    expect(beijingDateTime('2026-12-31T16:00:00Z')).toBe('2027-01-01 00:00:00')
    expect(beijingDate('2026-10-31T23:30:00-07:00')).toBe('2026-11-01')
    expect(beijingDateTime('2026-10-15T16:00:00+08:00')).toBe('2026-10-15 16:00:00')
  })
  it('preserves pure dates and treats unzoned datetime inputs as Beijing time', () => {
    expect(beijingDate('2026-11-13')).toBe('2026-11-13')
    expect(beijingInputToIso('2026-11-13T00:00')).toBe('2026-11-12T16:00:00.000Z')
    expect(beijingDateTimeInput('2026-11-12T16:00:00Z')).toBe('2026-11-13T00:00:00')
    expect(beijingDateTime('2026-11-13T00:00:00')).toBe('2026-11-13 00:00:00')
  })
  it('handles missing or invalid values without displaying Invalid Date', () => {
    for (const value of [undefined, null, '', 'invalid']) expect(beijingDateTime(value)).toBe('—')
    expect(beijingDateTimeInput(null)).toBe('')
    expect(beijingInputToIso('invalid')).toBe('')
  })
})
