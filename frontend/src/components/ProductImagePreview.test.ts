import {mount} from '@vue/test-utils'
import {describe,it,expect,vi} from 'vitest'
import ProductImagePreview from './ProductImagePreview.vue'
describe('product image preview',()=>{
 it('opens a native modal above a parent dialog and restores the thumbnail on Escape',async()=>{
  HTMLDialogElement.prototype.showModal=vi.fn(function(this:HTMLDialogElement){this.setAttribute('open','')})
  HTMLDialogElement.prototype.close=vi.fn(function(this:HTMLDialogElement){this.removeAttribute('open')})
  const w=mount(ProductImagePreview,{attachTo:document.body,props:{src:'https://example.com/synthetic.jpg',alt:'Synthetic product'},attrs:{class:'pmd-image'},global:{stubs:{Teleport:true}}})
  await w.get('.product-image-trigger').trigger('click');expect(w.get('dialog').attributes('open')).toBeDefined()
  await w.get('dialog').trigger('cancel');expect(w.get('dialog').attributes('open')).toBeUndefined()
  expect(document.activeElement).toBe(w.get('button').element);expect(w.get('button').classes()).toContain('pmd-image')
  w.unmount()
 })
})
