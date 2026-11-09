import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
import CustomerSkuPicker from './CustomerSkuPicker.vue'
import { domainApi } from '@/api/domain'
vi.mock('@/api/domain', () => ({ domainApi: { skus: vi.fn() } }))
const sku = { productId: 'p1', productName: '父体', productCode: 'P1', variant: { id: 'v1', skuCode: 'S1', variantName: '子体' } }
beforeEach(() => {
  vi.clearAllMocks()
  HTMLDialogElement.prototype.showModal = function () { this.open = true }
  HTMLDialogElement.prototype.close = function () { this.open = false }
  vi.mocked(domainApi.skus).mockResolvedValue({ items: [sku], totalCount: 21, page: 0, size: 20 } as Awaited<ReturnType<typeof domainApi.skus>>)
})
it('applies server search on submit and confirms a selected SKU', async () => {
  const wrapper = mount(CustomerSkuPicker); await flushPromises()
  await wrapper.get('input[type=search]').setValue('S1')
  expect(domainApi.skus).toHaveBeenCalledTimes(1)
  await wrapper.get('form').trigger('submit'); await flushPromises()
  expect(domainApi.skus).toHaveBeenLastCalledWith(expect.objectContaining({ q: 'S1', page: 0, status: 'ACTIVE', productStatus: 'ACTIVE' }))
  await wrapper.get('input[type=radio]').setValue(true)
  expect(wrapper.emitted('select')).toBeUndefined()
  await wrapper.findAll('button').find(b => b.text() === '确认选择')!.trigger('click')
  expect(wrapper.emitted('select')?.[0]).toEqual([sku]); wrapper.unmount()
})
it('shows errors, retries and never applies a cancelled choice', async () => {
  vi.mocked(domainApi.skus).mockRejectedValueOnce(new Error('模拟请求失败'))
  const wrapper = mount(CustomerSkuPicker); await flushPromises()
  expect(wrapper.get('[role=alert]').text()).toContain('模拟请求失败')
  await wrapper.get('[role=alert] button').trigger('click'); await flushPromises()
  await wrapper.get('input[type=radio]').setValue(true)
  await wrapper.findAll('button').find(b => b.text() === '取消')!.trigger('click')
  expect(wrapper.emitted('select')).toBeUndefined(); expect(wrapper.emitted('close')).toHaveLength(1); wrapper.unmount()
})
