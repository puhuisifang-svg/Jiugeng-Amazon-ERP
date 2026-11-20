import { vi, it, expect } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { domainApi, type ProductInput } from '@/api/domain'
import { session } from '@/state/session'
import ProductForm from './ProductForm.vue'
import ProductMaterialPicker from './ProductMaterialPicker.vue'
vi.mock('@/config/runtime',()=>({runtimeConfig:{authMode:'password',apiBaseUrl:'/api'}}))
it('requests only enabled product suppliers for SKU assignment',async()=>{
 const access=vi.spyOn(session,'canAccess').mockReturnValue(true)
 const suppliers=vi.spyOn(domainApi,'suppliers').mockResolvedValue({items:[],totalCount:0,page:0,size:100})
 const materials=vi.spyOn(domainApi,'materials').mockResolvedValue({items:[]} as never)
 const balances=vi.spyOn(domainApi,'balances').mockResolvedValue({items:[]} as never)
 const wrapper=mount(ProductForm,{props:{submitLabel:'保存'},global:{stubs:{RouterLink:true}}});await flushPromises()
 expect(suppliers).not.toHaveBeenCalled()
 await wrapper.findAll('.product-editor-tabs button').find(button=>button.text()==='采购信息')!.trigger('click');await flushPromises()
 expect(suppliers).toHaveBeenCalledWith(expect.objectContaining({status:'ACTIVE',category:'PRODUCT'}))
 wrapper.unmount();access.mockRestore();suppliers.mockRestore();materials.mockRestore();balances.mockRestore()
})


it('edits compact relation rows, retains collapsed notes and submits the original numeric units', async () => {
 vi.spyOn(session, 'canAccess').mockReturnValue(true)
 vi.spyOn(domainApi, 'suppliers').mockResolvedValue({ items: [
  { id: 'supplier-a', name: '合成供应商甲', status: 'ACTIVE', version: 0 },
  { id: 'supplier-b', name: '合成供应商乙', status: 'ACTIVE', version: 0 },
 ], totalCount: 2, page: 0, size: 100 })
 vi.spyOn(domainApi, 'materials').mockResolvedValue({ items: [{ id: 'material-a', materialCode: 'MAT-SYN', name: '合成纸箱', materialType: '包装', unit: '个', status: 'ACTIVE' }], mockData: true })
 vi.spyOn(domainApi, 'balances').mockResolvedValue({ items: [] } as never)
 const initial: ProductInput = { productCode: 'SYN', productName: '合成父体', status: 'ACTIVE', variants: [{ skuCode: 'SYN-S', variantName: '合成子体', status: 'ACTIVE', suppliers: [], materials: [] }] }
 const wrapper = mount(ProductForm, { props: { submitLabel: '保存', initial }, global: { stubs: { RouterLink: true, ProductMaterialPicker: true, TableViewport: { template: '<div><slot /></div>' } } } })
 try {
  await flushPromises()
  await wrapper.findAll('.product-editor-tabs button').find(button => button.text() === '采购信息')!.trigger('click'); await flushPromises()
  await wrapper.get('select[aria-label="添加供应商"]').setValue('supplier-a')
  await wrapper.get('select[aria-label="添加供应商"]').setValue('supplier-b')
  const purchase = wrapper.get('[data-editor-tab="purchase"]')
  expect(purchase.findAll('tbody > tr:not(.relation-edit-note)')).toHaveLength(2)
  await purchase.findAll('input[type="radio"]')[1]!.setValue(true)
  expect(purchase.findAll('input[type="radio"]')[0]!.element).toHaveProperty('checked', false)
  expect(purchase.findAll('.relation-edit-note')[0]!.isVisible()).toBe(false)
  await purchase.findAll('.relation-note-toggle[aria-expanded]')[0]!.trigger('click')
  expect(purchase.findAll('.relation-note-toggle[aria-expanded]')[0]!.attributes('aria-expanded')).toBe('true')
  await purchase.findAll('input[aria-label="供应商关系备注"]')[0]!.setValue('合成采购备注')
  await purchase.findAll('.relation-note-toggle[aria-expanded]')[0]!.trigger('click')
  expect(purchase.findAll('.relation-edit-note')[0]!.isVisible()).toBe(false)
  expect(purchase.findAll('.relation-note-toggle[aria-expanded]')[0]!.text()).toBe('已填备注')
  await wrapper.findAll('.product-editor-tabs button').find(button => button.text() === '关联辅料')!.trigger('click'); await flushPromises()
  await wrapper.get('button[aria-label="添加辅料"]').trigger('click')
  wrapper.getComponent(ProductMaterialPicker).vm.$emit('select', ['material-a'])
  await flushPromises()
  const material = wrapper.get('[data-editor-tab="materials"]')
  await material.get('input[aria-label="合成纸箱标准用量"]').setValue(0)
  await wrapper.findAll('.product-editor-tabs button').find(button => button.text() === '采购信息')!.trigger('click'); await flushPromises()
  await wrapper.get('form').trigger('submit')
  expect(wrapper.emitted('submit')).toBeUndefined()
  expect(wrapper.get('.product-editor-tabs .active').text()).toBe('关联辅料')
  await material.get('input[aria-label="合成纸箱标准用量"]').setValue(3)
  await material.get('input[aria-label="合成纸箱损耗率（百分比）"]').setValue(2.5)
  await material.get('.relation-note-toggle').trigger('click')
  await material.get('input[aria-label="辅料关系备注"]').setValue('合成包装备注')
  await material.get('.relation-note-toggle').trigger('click')
  await wrapper.get('form').trigger('submit')
  expect(wrapper.emitted('submit')?.[0]?.[0]).toMatchObject({ variants: [{
   suppliers: [{ supplierId: 'supplier-a', primary: false, remark: '合成采购备注' }, { supplierId: 'supplier-b', primary: true }],
   materials: [{ materialId: 'material-a', standardQuantity: 3, unit: '个', lossRate: 0.025, enabled: true, remark: '合成包装备注' }],
  }] })
  expect(initial.variants[0]?.suppliers).toHaveLength(0)
  await wrapper.setProps({ busy: true })
  expect(wrapper.get('select[aria-label="添加供应商"]').attributes('disabled')).toBeDefined()
  expect(material.get('input[type="number"]').attributes('disabled')).toBeDefined()
  expect(material.get('.danger-link').attributes('disabled')).toBeDefined()
  await wrapper.setProps({ busy: false })
  await material.get('.danger-link').trigger('click')
  expect(material.find('table').exists()).toBe(false)
  expect(material.text()).toContain('暂未关联辅料')
 } finally { wrapper.unmount(); vi.restoreAllMocks() }
})
