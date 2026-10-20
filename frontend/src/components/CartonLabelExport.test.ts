import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
import CartonLabelExport from './CartonLabelExport.vue'
import { cartonLabelsApi, defaultLabelConfig, downloadLabelBlob } from '@/api/cartonLabels'
vi.mock('@/api/cartonLabels', async (original) => {
  const actual = await original<typeof import('@/api/cartonLabels')>()
  return { ...actual, downloadLabelBlob: vi.fn(), cartonLabelsApi: { draft: vi.fn(), history: vi.fn(), preview: vi.fn(), generate: vi.fn(), download: vi.fn() } }
})
beforeEach(() => {
  vi.clearAllMocks()
  HTMLDialogElement.prototype.showModal = function () { this.open = true }
  URL.createObjectURL = vi.fn(() => 'blob:synthetic-label')
  URL.revokeObjectURL = vi.fn()
  vi.mocked(cartonLabelsApi.draft).mockResolvedValue({ shipmentId: 's', shipmentVersion: 3, customerId: 'c', customerName: '合成客户', productName: '合成产品', customerCode: 'C001', quantity: 80, boxes: 2, unit: '套', previous: null,
    templates: [{ id: 't', customerId: 'c', name: '模板', config: defaultLabelConfig(), enabled: true, defaultTemplate: true, version: 1 }] })
  vi.mocked(cartonLabelsApi.history).mockResolvedValue([])
  vi.mocked(cartonLabelsApi.preview).mockResolvedValue(new Blob(['pdf']))
  vi.mocked(cartonLabelsApi.generate).mockResolvedValue({ id: 'e', pages: 2, createdAt: '2026-01-01T00:00:00Z' })
  vi.mocked(cartonLabelsApi.download).mockResolvedValue(new Blob(['pdf']))
})
it('requires preview, fills purchaser manually and saves/downloads a versioned batch', async () => {
  const wrapper = mount(CartonLabelExport, { props: { shipmentIds: ['s'] } }); await flushPromises()
  expect(wrapper.text()).toContain('2 张相同标签')
  const generate = () => wrapper.findAll('button').find(b => b.text() === '确认生成并下载')!
  expect(generate().attributes('disabled')).toBeDefined()
  await wrapper.findAll('input')[0]!.setValue('合成采购人')
  await wrapper.findAll('button').find(b => b.text() === '应用到本批全部记录')!.trigger('click')
  await wrapper.get('input[placeholder="默认带出 SKU 名称，可手动修改"]').setValue('手动 SKU 名称')
  await wrapper.get('input[placeholder="手动输入合同号或订单号"]').setValue('SYN-ORDER-001')
  await wrapper.get('form').trigger('submit'); await flushPromises()
  expect(cartonLabelsApi.preview).toHaveBeenCalledWith(expect.objectContaining({ items: [expect.objectContaining({ productName: '手动 SKU 名称', contractNo: 'SYN-ORDER-001', purchaser: '合成采购人', templateId: 't', templateVersion: 1, shipmentVersion: 3 })] }))
  expect(wrapper.find('iframe').exists()).toBe(true)
  await generate().trigger('click'); await flushPromises()
  expect(cartonLabelsApi.generate).toHaveBeenCalledTimes(1); expect(downloadLabelBlob).toHaveBeenCalledTimes(1)
  await wrapper.findAll('button').find(b => b.text() === '关闭')!.trigger('click')
  expect(wrapper.emitted('close')).toHaveLength(1)
  wrapper.unmount()
})
it('does not discard form on backdrop or escape; edits invalidate old preview', async () => {
  const wrapper = mount(CartonLabelExport, { props: { shipmentIds: ['s'] } }); await flushPromises()
  await wrapper.get('dialog').trigger('click'); expect(wrapper.emitted('close')).toBeUndefined()
  await wrapper.get('form').trigger('submit'); await flushPromises()
  await wrapper.findAll('input')[3]!.setValue('采购人变化'); await flushPromises()
  expect(wrapper.find('iframe').exists()).toBe(false)
  await wrapper.get('dialog').trigger('cancel'); expect(wrapper.emitted('close')).toBeUndefined()
  expect(wrapper.text()).toContain('填写的内容尚未保存')
  wrapper.unmount()
})
it('shows failure and prevents saving when preview fails', async () => {
  vi.mocked(cartonLabelsApi.preview).mockRejectedValue(new Error('模板已修改，请重新加载'))
  const wrapper = mount(CartonLabelExport, { props: { shipmentIds: ['s'] } }); await flushPromises()
  await wrapper.get('form').trigger('submit'); await flushPromises()
  expect(wrapper.get('[role="alert"]').text()).toContain('模板已修改')
  expect(cartonLabelsApi.generate).not.toHaveBeenCalled()
  wrapper.unmount()
})
it('recalculates page count when switching between single-page and by-box templates', async () => {
  const draft = await cartonLabelsApi.draft('s')
  draft.boxes = 600
  draft.templates.push({ ...draft.templates[0]!, id: 'one', name: '单页', defaultTemplate: false, config: { ...defaultLabelConfig(), singlePage: true } })
  vi.mocked(cartonLabelsApi.draft).mockResolvedValue(draft)
  const wrapper = mount(CartonLabelExport, { props: { shipmentIds: ['s'] } }); await flushPromises()
  expect(wrapper.text()).toContain('共 600 页')
  await wrapper.get('select').setValue('one'); await flushPromises()
  expect(wrapper.text()).toContain('共 1 页')
  expect(wrapper.text()).toContain('600 箱 → 1 张相同标签')
  await wrapper.get('form').trigger('submit'); await flushPromises()
  expect(cartonLabelsApi.preview).toHaveBeenCalled()
  expect(wrapper.find('iframe').exists()).toBe(true)
  await wrapper.get('select').setValue('t'); await flushPromises()
  expect(wrapper.text()).toContain('共 600 页')
  expect(wrapper.find('iframe').exists()).toBe(false)
  wrapper.unmount()
})
