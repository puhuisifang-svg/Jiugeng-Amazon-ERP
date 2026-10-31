import { mount } from '@vue/test-utils'
import { defineComponent, nextTick, ref } from 'vue'
import { describe, expect, it } from 'vitest'
import { vSingleSelect } from './singleSelect'
function setup(initial: number | boolean = 1) {
 const value = ref<number | boolean>(initial), disabled = ref(false), extra = ref(false)
 const w = mount(defineComponent({ directives: { singleSelect: vSingleSelect }, setup: () => ({ value, disabled, extra }), template: '<form><label>结果<select v-single-select v-model="value" :disabled="disabled"><option :value="1">全部</option><option :value="false">失败</option><option disabled :value="2">禁用项</option><option v-if="extra" :value="3">新增项</option></select></label></form>' }), { attachTo: document.body })
 return { w, value, disabled, extra }
}
describe('shared single select', () => {
 it('renders the initial typed value after Vue has applied v-model', async () => {
  const { w } = setup(false); await nextTick()
  expect(w.get('[role=combobox]').text()).toBe('失败'); w.unmount()
 })
 it('preserves typed Vue values, emits once, closes and restores focus after selection', async () => {
  const { w, value } = setup(); await w.get('[role=combobox]').trigger('click')
  expect(w.get('[role=combobox]').attributes('aria-expanded')).toBe('true')
  await w.get('[data-index="1"]').trigger('click'); await nextTick()
  expect(value.value).toBe(false); expect(w.get('[role=combobox]').text()).toBe('失败')
  expect(w.get('[role=combobox]').attributes('aria-expanded')).toBe('false')
  expect(document.activeElement).toBe(w.get('[role=combobox]').element); w.unmount()
 })
 it('supports keyboard selection, skips disabled items and Escape cancels navigation', async () => {
  const { w, value } = setup(); const trigger=w.get('[role=combobox]')
  await trigger.trigger('keydown', { key:'ArrowDown' }); await trigger.trigger('keydown', {key:'End'}); await trigger.trigger('keydown',{key:'Escape'})
  expect(value.value).toBe(1)
  await trigger.trigger('keydown', {key:'Enter'}); await trigger.trigger('keydown',{key:'End'}); await trigger.trigger('keydown',{key:'Enter'})
  expect(value.value).toBe(false); w.unmount()
 })
 it('syncs model resets, dynamic options and disabled state, closes outside and cleans up', async () => {
  const { w, value, extra, disabled } = setup(); extra.value=true; await nextTick()
  await w.get('[role=combobox]').trigger('click'); expect(w.findAll('[role=option]')).toHaveLength(4)
  document.body.dispatchEvent(new Event('pointerdown', {bubbles:true})); expect(w.get('[role=combobox]').attributes('aria-expanded')).toBe('false')
  value.value=3; await nextTick(); expect(w.get('[role=combobox]').text()).toBe('新增项')
  disabled.value=true; await nextTick(); expect(w.get('button').attributes('disabled')).toBeDefined()
  w.unmount(); expect(document.querySelector('.single-select-panel')).toBeNull()
 })
})
