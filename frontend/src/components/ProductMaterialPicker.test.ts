import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import ProductMaterialPicker from './ProductMaterialPicker.vue'
import type { MaterialDetailDto } from '@/api/domain'
const materials = Array.from({ length: 25 }, (_, i) => ({ id: String(i), materialCode: `M-${i}`, name: `辅料${i}`, materialType: i % 2 ? '包装' : '面料', specification: '标准', unit: '米', status: i === 24 ? 'DISABLED' : 'ACTIVE', version: 0 })) as MaterialDetailDto[]
beforeEach(() => { HTMLDialogElement.prototype.showModal = function () { this.open = true }; HTMLDialogElement.prototype.close = function () { this.open = false } })
describe('ProductMaterialPicker', () => {
  it('filters the complete collection, excludes linked/disabled items, preserves selections across filters', async () => {
    const wrapper = mount(ProductMaterialPicker, { props: { materials, selectedIds: ['0'], stockLabel: () => '库存未初始化' } })
    expect(wrapper.find('[aria-label="选择 M-0"]').exists()).toBe(false)
    await wrapper.get('[aria-label="选择 M-1"]').setValue(true)
    await wrapper.get('input[type="search"]').setValue('M-23')
    expect(wrapper.find('[aria-label="选择 M-23"]').exists()).toBe(true)
    await wrapper.get('[aria-label="选择 M-23"]').setValue(true)
    await wrapper.get('input[type="search"]').setValue('M-24')
    expect(wrapper.text()).toContain('没有符合条件')
    await wrapper.get('.primary-button').trigger('click')
    expect(wrapper.emitted('select')?.[0]).toEqual([['1', '23']])
    wrapper.unmount()
  })
  it('does not apply selection on cancel', async () => {
    const wrapper = mount(ProductMaterialPicker, { props: { materials, selectedIds: [], stockLabel: () => '' } })
    await wrapper.get('[aria-label="选择 M-0"]').setValue(true)
    await wrapper.findAll('button').find(b => b.text() === '取消')!.trigger('click')
    expect(wrapper.emitted('close')).toHaveLength(1)
    expect(wrapper.emitted('select')).toBeUndefined()
    wrapper.unmount()
  })
})
