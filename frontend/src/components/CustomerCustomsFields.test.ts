import { mount, flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import CustomerCustomsFields from './CustomerCustomsFields.vue'
import CustomsFields from './CustomsFields.vue'
import { blankCustoms, type CustomsProfile } from '@/api/customs'
import { customersApi, type CustomsHistoryItem } from '@/api/customers'
vi.mock('@/directives/singleSelect', () => ({ vSingleSelect: {} }))
const profile = (): CustomsProfile => ({ ...blankCustoms(), chinaCode: '6307909000', schemaCode: '6307909000', elementFields: [{ key: 'brandType', label: '品牌类型' }], elements: { brandType: '当前值' }, nameZh: '当前品名', measurementUnit: '千克', version: 7 })
const source = (): CustomsHistoryItem => ({ id: 'history', customerName: '合成来源客户', productName: '合成商品', skuCode: 'SYN', status: 'ACTIVE', updatedAt: '2026-11-24T00:00:00Z', customs: { ...profile(), nameZh: '历史品名', elements: { brandType: '历史品牌' }, measurementUnit: '千克/个', state: 'REVIEWED', version: 99 } })
const wrappers: ReturnType<typeof mount>[] = []
function setup() {
  const w = mount(CustomerCustomsFields, { props: { modelValue: profile(), excludeId: 'current' }, global: { stubs: {
    Teleport: true, AccessDialog: { template: '<div role="dialog"><button aria-label="关闭历史" @click="$emit(\'close\')">关闭</button><slot/><slot name="footer"/></div>' }, ListPagination: true,
  } } }); wrappers.push(w); return w
}
async function enter(w: ReturnType<typeof mount>) { w.findComponent(CustomsFields).vm.$emit('hs-input', '6307909000'); await vi.advanceTimersByTimeAsync(400); await flushPromises() }
beforeEach(() => { vi.useFakeTimers(); vi.spyOn(customersApi, 'customsHistory').mockResolvedValue({ items: [source()], totalCount: 1, page: 0, size: 20 }) })
afterEach(() => { wrappers.splice(0).forEach(w => w.unmount()); vi.restoreAllMocks(); vi.useRealTimers() })
it('opens on entered HS, stages edits and requires explicit confirmation before replacing the form', async () => {
  const historical = source(), original = structuredClone(historical)
  vi.mocked(customersApi.customsHistory).mockResolvedValueOnce({ items: [historical], totalCount: 1, page: 0, size: 20 })
  const save = vi.spyOn(customersApi, 'saveProduct')
  const w = setup(); expect(customersApi.customsHistory).not.toHaveBeenCalled()
  await enter(w)
  expect(customersApi.customsHistory).toHaveBeenCalledWith('6307909000', 'current', 0, 20)
  expect(w.find('[role=dialog]').exists()).toBe(true)
  expect(w.emitted('update:modelValue')).toBeUndefined()
  await w.get('#customs-history-review').trigger('submit')
  expect(w.emitted('update:modelValue')).toBeUndefined()
  await w.get('#customs-history-review input').setValue('核对后的新品名')
  expect(w.emitted('update:modelValue')).toBeUndefined()
  await w.get('.history-confirm input').setValue(true)
  await w.get('#customs-history-review').trigger('submit')
  const result = w.emitted('update:modelValue')?.at(-1)?.[0] as CustomsProfile
  expect(result).toMatchObject({ nameZh: '核对后的新品名', elements: { brandType: '历史品牌' }, state: 'DRAFT', version: 7, measurementUnit: '千克/个' })
  expect(result).not.toBe(historical.customs)
  expect(result.elements).not.toBe(historical.customs.elements)
  expect(historical).toEqual(original)
  expect(save).not.toHaveBeenCalled()
  expect(w.find('[role=dialog]').exists()).toBe(false)
})
it('leaves fields unchanged when cancelled and ignores responses after switching away', async () => {
  const w = setup(); await enter(w)
  await w.get('[aria-label="关闭历史"]').trigger('click')
  expect(w.emitted('update:modelValue')).toBeUndefined()
  let resolve!: (value: Awaited<ReturnType<typeof customersApi.customsHistory>>) => void
  vi.mocked(customersApi.customsHistory).mockImplementationOnce(() => new Promise(done => { resolve = done }))
  await enter(w); await w.setProps({ active: false })
  resolve({ items: [source()], totalCount: 1, page: 0, size: 20 }); await flushPromises()
  expect(w.find('[role=dialog]').exists()).toBe(false)
  expect(w.emitted('update:modelValue')).toBeUndefined()
})
it('retains user input on lookup failure and permits a manual retry', async () => {
  vi.mocked(customersApi.customsHistory).mockRejectedValueOnce(new Error('历史查询失败'))
  const w = setup(); await enter(w)
  expect(w.get('[role=alert]').text()).toBe('历史查询失败')
  expect(w.emitted('update:modelValue')).toBeUndefined()
  await w.get('.customs-history-status button').trigger('click'); await flushPromises()
  expect(w.find('[role=dialog]').exists()).toBe(true)
})
it('clears confirmation after an edit and does not search in read-only mode', async () => {
  const w = setup(); await w.setProps({ disabled: true }); await enter(w)
  expect(customersApi.customsHistory).not.toHaveBeenCalled()
  await w.setProps({ disabled: false }); await enter(w)
  await w.get('.history-confirm input').setValue(true)
  await w.get('#customs-history-review input').setValue('再修改')
  expect((w.get('.history-confirm input').element as HTMLInputElement).checked).toBe(false)
})
it('keeps the teleported reference dialog inside the application control theme', async () => {
  const app = document.createElement('div'); app.id = 'app'; document.body.append(app)
  const host = document.createElement('div'); app.append(host)
  const w = mount(CustomerCustomsFields, { attachTo: host, props: { modelValue: profile() }, global: { stubs: {
    AccessDialog: { template: '<div role="dialog"><slot/><slot name="footer"/></div>' }, ListPagination: true,
  } } })
  try {
    await enter(w)
    const dialog = document.querySelector('[role=dialog]')
    expect(dialog).not.toBeNull()
    expect(dialog?.closest('#app')).toBe(app)
    expect(w.element.contains(dialog)).toBe(false)
  } finally { w.unmount(); app.remove() }
})
