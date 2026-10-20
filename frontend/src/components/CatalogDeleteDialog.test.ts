import { mount } from '@vue/test-utils'
import { beforeEach, expect, it } from 'vitest'
import CatalogDeleteDialog from './CatalogDeleteDialog.vue'
beforeEach(() => { HTMLDialogElement.prototype.showModal = function () { this.open = true } })
it('keeps backdrop clicks harmless, protects pending deletes and displays failure', async () => {
  const wrapper = mount(CatalogDeleteDialog, { props: { title: '删除 SKU', name: '合成 SKU', description: '仅删除指定项', busy: false, error: '' } })
  await wrapper.get('dialog').trigger('click')
  expect(wrapper.emitted('confirm')).toBeUndefined(); expect(wrapper.emitted('cancel')).toBeUndefined()
  await wrapper.get('.danger-button').trigger('click'); expect(wrapper.emitted('confirm')).toHaveLength(1)
  await wrapper.setProps({ busy: true })
  await wrapper.get('.danger-button').trigger('click'); await wrapper.get('dialog').trigger('cancel')
  expect(wrapper.emitted('confirm')).toHaveLength(1); expect(wrapper.emitted('cancel')).toBeUndefined()
  await wrapper.setProps({ busy: false, error: '存在业务引用' })
  expect(wrapper.get('[role="alert"]').text()).toBe('存在业务引用')
  await wrapper.get('dialog').trigger('cancel'); expect(wrapper.emitted('cancel')).toHaveLength(1)
  wrapper.unmount()
})
it('shows the customer-product retention notice instead of the source SKU deletion restriction', () => {
  const wrapper = mount(CatalogDeleteDialog, { props: { title: '删除客户商品', name: '合成商品', description: '不再用于新建单据', notice: '历史单据保留，已有单据可继续处理', busy: false, error: '' } })
  expect(wrapper.text()).toContain('已有单据可继续处理')
  expect(wrapper.text()).not.toContain('存在发货、发票、采购或收货记录时将拒绝删除')
  wrapper.unmount()
})
