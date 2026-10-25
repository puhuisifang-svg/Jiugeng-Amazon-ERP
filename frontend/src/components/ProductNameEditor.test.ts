import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ProductNameEditor from './ProductNameEditor.vue'
import ProductName from './ProductName.vue'

describe('ProductNameEditor', () => {
  it('formats only a selection and restores it after toolbar focus; can reset color and weight', async () => {
    const text = '黑色-沙发网-大号\n610cm*40.5cm'
    const wrapper = mount(ProductNameEditor, { props: { modelValue: text } })
    const field = wrapper.get('textarea').element as HTMLTextAreaElement
    field.setSelectionRange(text.indexOf('\n') + 1, text.length)
    await wrapper.get('textarea').trigger('select')
    await wrapper.findAll('button').find(b => b.text() === '红色')!.trigger('click')
    const spans = wrapper.emitted('update:styles')![0]![0] as import('@/utils/productName').NameStyleSpan[]
    await wrapper.setProps({ styles: spans })
    const runs = wrapper.findAll('.product-display-name span')
    expect(runs[0]!.attributes('style')).toContain('color: rgb(23, 32, 51)')
    expect(runs[1]!.attributes('style')).toContain('color: rgb(198, 40, 40)')
    expect(field.selectionStart).toBe(text.indexOf('\n') + 1)
    await wrapper.findAll('button').find(b => b.text() === '常规')!.trigger('click')
    expect(wrapper.emitted('update:styles')![1]![0]).toEqual(expect.arrayContaining([expect.objectContaining({ color: 'RED', bold: false })]))
    await wrapper.findAll('button').find(b => b.text() === '全选名称')!.trigger('click')
    await wrapper.findAll('button').find(b => b.text() === '黑色')!.trigger('click')
    expect(wrapper.emitted('update:styles')![2]![0]).toEqual([{ start: 0, end: text.length, color: 'BLACK', bold: true }])
    wrapper.unmount()
  })
  it('does not format silently without a selection or submit the form', async () => {
    const wrapper = mount(ProductNameEditor, { props: { modelValue: '名称' } })
    await wrapper.findAll('button').find(b => b.text() === '红色')!.trigger('click')
    expect(wrapper.emitted('update:styles')).toBeUndefined()
    expect(wrapper.get('[role="status"]').text()).toContain('请先选中')
    expect(wrapper.findAll('button').every(b => b.attributes('type') === 'button')).toBe(true)
  })
  it('renders HTML-like names as plain text without introducing markup', () => {
    const wrapper = mount(ProductName, { props: { text: '<img src=x onerror=alert(1)>\n名称', highlighted: true } })
    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.text()).toContain('<img src=x onerror=alert(1)>')
  })
})
