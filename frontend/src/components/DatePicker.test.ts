import { mount, flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import DatePicker from './DatePicker.vue'
beforeEach(() => { vi.useFakeTimers(); vi.setSystemTime(new Date('2026-10-30T04:00:00Z')) })
afterEach(() => vi.useRealTimers())
const open = async (w: ReturnType<typeof mount>) => { await w.get('button[aria-label="打开日期选择器"]').trigger('click'); await flushPromises() }
describe('shared date picker', () => {
  it('closes on confirmation inside a label, restores trigger focus and can reopen', async () => {
    const host = document.createElement('label')
    document.body.appendChild(host)
    const w = mount(DatePicker, { attachTo: host, props: { range: true, type: 'datetime-local', modelValue: '2026-10-30T10:00', end: '2026-10-30T11:00' } })
    try {
      await open(w)
      await w.findAll('.date-picker-time input')[1]!.setValue('13:30')
      const event = new MouseEvent('click', { bubbles: true, cancelable: true })
      w.get('button.confirm').element.dispatchEvent(event)
      await flushPromises()
      expect(event.defaultPrevented).toBe(true)
      expect(w.emitted('update:end')).toEqual([['2026-10-30T13:30']])
      expect(w.find('[role="dialog"]').exists()).toBe(false)
      expect(document.activeElement).toBe(w.get('.date-picker-icon').element)
      await open(w)
      expect(w.find('[role="dialog"]').exists()).toBe(true)
      await w.findAll('button').find(b => b.text() === '取消')!.trigger('click')
      expect(w.find('[role="dialog"]').exists()).toBe(false)
      expect(w.emitted('change')).toHaveLength(1)
    }
    finally { w.unmount(); host.remove() }
  })

  it('selects and sorts a cross-month range without emitting an incomplete range', async () => {
    const w = mount(DatePicker, { props: { range: true } }); await open(w)
    await w.get('button[data-day="2026-10-30"]').trigger('click')
    expect(w.emitted('update:modelValue')).toBeUndefined()
    await w.findAll('button[data-day="2026-11-03"]')[0]!.trigger('click')
    expect(w.emitted('update:modelValue')).toEqual([['2026-10-30']]); expect(w.emitted('update:end')).toEqual([['2026-11-03']]); w.unmount()
  })
  it('supports presets and clears both bounds', async () => {
    const w = mount(DatePicker, { props: { range: true, type: 'datetime-local', modelValue: '2026-01-01T12:00', end: '2026-02-01T12:00' } }); await open(w)
    await w.findAll('button').find(b => b.text() === '最近7天')!.trigger('click')
    expect(w.emitted('update:modelValue')?.[0]).toEqual(['2026-10-24T00:00'])
    expect(w.emitted('update:end')?.[0]).toEqual(['2026-10-30T23:59:59'])
    await w.get('[aria-label="清空日期"]').trigger('click')
    expect(w.emitted('update:end')?.[1]).toEqual(['']); w.unmount()
  })
  it('does not commit cancelled drafts and respects bounds/disabled state', async () => {
    const w = mount(DatePicker, { props: { modelValue: '2026-10-15', range: true, min: '2026-10-10', max: '2026-10-20' } }); await open(w)
    expect(w.get('button[data-day="2026-10-09"]').attributes('disabled')).toBeDefined()
    await w.get('button[data-day="2026-10-18"]').trigger('click')
    await w.get('[role="dialog"]').trigger('keydown', { key: 'Escape' })
    expect(w.emitted('update:modelValue')).toBeUndefined(); expect(w.find('[role="dialog"]').exists()).toBe(false)
    await w.setProps({ disabled: true }); await open(w); expect(w.find('[role="dialog"]').exists()).toBe(false); w.unmount()
  })
  it('requires confirmation for datetime ranges and rejects reversed times', async () => {
    const w = mount(DatePicker, { props: { range: true, type: 'datetime-local', modelValue: '2026-10-30T10:00', end: '2026-10-30T11:00' } }); await open(w)
    const times = w.findAll('.date-picker-time input')
    await times[0]!.setValue('12:00'); expect(w.findAll('button').find(b => b.text() === '确定')!.attributes('disabled')).toBeDefined()
    expect(w.emitted('update:modelValue')).toBeUndefined()
    await times[1]!.setValue('13:30'); await w.findAll('button').find(b => b.text() === '确定')!.trigger('click')
    expect(w.emitted('update:end')?.[0]).toEqual(['2026-10-30T13:30']); w.unmount()
  })
  it('keeps time-only values without adding a date or seconds', async () => {
    const w = mount(DatePicker, { props: { type: 'time', modelValue: '03:00' } })
    await w.get('[aria-label="打开时间选择器"]').trigger('click'); await flushPromises()
    await w.get('.date-picker-time input').setValue('09:30')
    await w.findAll('button').find(b => b.text() === '确定')!.trigger('click')
    expect(w.emitted('update:modelValue')?.[0]).toEqual(['09:30']); w.unmount()
  })
  it('preserves native form constraints and manual date updates', async () => {
    const w = mount(DatePicker, { props: { modelValue: '', required: true }, attrs: { id: 'delivery', 'aria-label': '交期' } })
    expect(w.get('input').attributes('required')).toBeDefined(); expect(w.get('input').attributes('id')).toBe('delivery')
    await w.get('input').setValue('2028-02-29'); expect(w.emitted('update:modelValue')).toEqual([['2028-02-29']]); w.unmount()
  })
})

it('uses Beijing today across a UTC month boundary for range shortcuts', async () => {
  vi.setSystemTime(new Date('2026-10-31T16:01:00Z'))
  const w = mount(DatePicker, { props: { range: true, type: 'datetime-local' } })
  await open(w)
  await w.findAll('button').find(b => b.text() === '今天')!.trigger('click')
  expect(w.emitted('update:modelValue')?.[0]).toEqual(['2026-11-01T00:00'])
  expect(w.emitted('update:end')?.[0]).toEqual(['2026-11-01T23:59:59'])
  w.unmount()
})
