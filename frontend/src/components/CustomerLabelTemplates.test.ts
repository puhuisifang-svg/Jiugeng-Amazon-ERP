import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
import CustomerLabelTemplates from './CustomerLabelTemplates.vue'
import { cartonLabelsApi, defaultLabelConfig } from '@/api/cartonLabels'
vi.mock('@/api/cartonLabels', async (original) => ({ ...await original<object>(), cartonLabelsApi: { templates: vi.fn(), saveTemplate: vi.fn(), templatePreview: vi.fn() } }))
beforeEach(() => {
  vi.clearAllMocks(); HTMLDialogElement.prototype.showModal = function () { this.open = true }
  URL.createObjectURL = vi.fn(() => 'blob:template'); URL.revokeObjectURL = vi.fn()
  vi.mocked(cartonLabelsApi.templates).mockResolvedValue([])
  vi.mocked(cartonLabelsApi.saveTemplate).mockResolvedValue({ id: 't', customerId: 'c', name: '模板', config: defaultLabelConfig(), enabled: true, defaultTemplate: true, version: 0 })
  vi.mocked(cartonLabelsApi.templatePreview).mockResolvedValue(new Blob(['pdf']))
})
it('saves customer template and protects unsaved edits from closing or switching templates', async () => {
  const wrapper = mount(CustomerLabelTemplates, { props: { customerId: 'c', customerName: '合成客户' } }); await flushPromises()
  await wrapper.get('input').setValue('修改模板')
  await wrapper.get('select[aria-label="导出页数"]').setValue('true')
  await wrapper.findAll('button').find(b => b.text() === '新增模板')!.trigger('click')
  expect(wrapper.get('[role="alert"]').text()).toContain('请先保存')
  await wrapper.get('dialog').trigger('cancel')
  expect(wrapper.emitted('close')).toBeUndefined()
  await wrapper.findAll('button').find(b => b.text() === '继续编辑')!.trigger('click')
  await wrapper.get('form').trigger('submit'); await flushPromises()
  expect(cartonLabelsApi.saveTemplate).toHaveBeenCalledWith('c', undefined, expect.objectContaining({ name: '修改模板', defaultTemplate: true, config: expect.objectContaining({ singlePage: true }) }))
  expect(wrapper.get('.success-toast[role="status"]').text()).toContain('模板已保存')
  wrapper.unmount()
})
it('does not report failure or lose saved identity when refreshing the list fails after save', async () => {
  const wrapper = mount(CustomerLabelTemplates, { props: { customerId: 'c', customerName: '合成客户' } }); await flushPromises()
  vi.mocked(cartonLabelsApi.templates).mockRejectedValueOnce(new Error('network'))
  await wrapper.get('form').trigger('submit'); await flushPromises()
  expect(wrapper.get('.success-toast').text()).toContain('模板已保存')
  expect(wrapper.get('[role="alert"]').text()).toContain('列表刷新失败')
  await wrapper.get('form').trigger('submit'); await flushPromises()
  expect(cartonLabelsApi.saveTemplate).toHaveBeenLastCalledWith('c', 't', expect.anything())
  wrapper.unmount()
})
it('does not show success toast when saving fails', async () => {
  vi.mocked(cartonLabelsApi.saveTemplate).mockRejectedValueOnce(new Error('版本冲突'))
  const wrapper = mount(CustomerLabelTemplates, { props: { customerId: 'c', customerName: '合成客户' } }); await flushPromises()
  await wrapper.get('form').trigger('submit'); await flushPromises()
  expect(wrapper.find('.success-toast').exists()).toBe(false)
  expect(wrapper.get('[role="alert"]').text()).toContain('版本冲突')
  wrapper.unmount()
})
