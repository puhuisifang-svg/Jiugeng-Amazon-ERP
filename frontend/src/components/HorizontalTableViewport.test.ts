import {mount,flushPromises} from '@vue/test-utils'
import {it,expect} from 'vitest'
import HorizontalTableViewport from './HorizontalTableViewport.vue'
it('keeps visible horizontal controls synchronized with table scrolling and reaches both ends',async()=>{
 const w=mount(HorizontalTableViewport,{slots:{default:'<table><tr><td>wide data</td></tr></table>'}});await flushPromises()
 const el=w.get('.table-viewport').element as HTMLElement
 Object.defineProperty(el,'scrollWidth',{value:2580,configurable:true});Object.defineProperty(el,'clientWidth',{value:800,configurable:true});window.dispatchEvent(new Event('resize'));await flushPromises()
 const range=w.get('input[type=range]');expect(range.attributes('max')).toBe('1780');await range.setValue('1780');expect(el.scrollLeft).toBe(1780);expect(w.get('[aria-label="向右滚动列表"]').attributes('disabled')).toBeDefined()
 el.scrollLeft=400;el.dispatchEvent(new Event('scroll'));await flushPromises();expect((range.element as HTMLInputElement).value).toBe('400')
 await range.setValue('0');expect(el.scrollLeft).toBe(0);expect(w.get('[aria-label="向左滚动列表"]').attributes('disabled')).toBeDefined();w.unmount()
})
