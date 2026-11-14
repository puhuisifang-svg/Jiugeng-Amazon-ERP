import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
import CustomerSkuPicker from './CustomerSkuPicker.vue'
import { domainApi } from '@/api/domain'
vi.mock('@/api/domain', () => ({ domainApi: { skus: vi.fn(), products:vi.fn().mockResolvedValue({items:[{id:'p1',productName:'父体'}]}) } }))
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
it('hides SKU and parent codes in sample selection without losing the selected identity', async () => {
  const wrapper = mount(CustomerSkuPicker, { props: { showCodes: false } }); await flushPromises()
  expect(wrapper.text()).not.toContain('P1')
  expect(wrapper.text()).not.toContain('S1')
  expect(wrapper.text()).toContain('父体')
  await wrapper.get('input[aria-label="选择 子体"]').setValue(true)
  expect(wrapper.get('.sku-picker-footer').text()).toContain('已选：子体')
  expect(wrapper.get('.sku-picker-footer').text()).not.toContain('S1')
  await wrapper.findAll('button').find(b => b.text() === '确认选择')!.trigger('click')
  expect(wrapper.emitted('select')?.[0]).toEqual([sku])
  wrapper.unmount()
})

it('filters by parent on the server and preserves multi-selection across pages', async()=>{
  const wrapper=mount(CustomerSkuPicker,{props:{multiple:true}});await flushPromises()
  await wrapper.get('input[type=checkbox]').setValue(true)
  await wrapper.get('select[aria-label="所属父体筛选"]').setValue('p1');await flushPromises()
  expect(domainApi.skus).toHaveBeenLastCalledWith(expect.objectContaining({productId:'p1',page:0}))
  expect(wrapper.get('input[type=checkbox]').element).toHaveProperty('checked',true)
  await wrapper.findAll('button').find(b=>b.text()==='确认选择')!.trigger('click')
  expect(wrapper.emitted('confirm')?.[0]).toEqual([[sku]])
  wrapper.unmount()
})
