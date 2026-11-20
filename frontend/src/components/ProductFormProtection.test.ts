import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import ProductForm from './ProductForm.vue'
import { domainApi, type ProductInput } from '@/api/domain'

vi.mock('@/config/runtime', () => ({ runtimeConfig: { authMode: 'password' } }))
vi.mock('@/state/session', () => ({ session: { canAccess: () => true } }))
vi.mock('@/api/domain', () => ({ domainApi: { suppliers: vi.fn(), supplier: vi.fn(), materials: vi.fn(), balances: vi.fn() } }))
enableAutoUnmount(afterEach)
beforeEach(() => {
  vi.clearAllMocks()
  HTMLDialogElement.prototype.showModal = function () { this.open = true }
  HTMLDialogElement.prototype.close = function () { this.open = false }
})
const initial: ProductInput = { productCode: 'SYN', productName: '合成父体', status: 'ACTIVE', variants: [{ id: 'v1', skuCode: 'SYN-SKU', variantName: '合成子体', status: 'ACTIVE', suppliers: [{ supplierId: 's1', primary: true }], materials: [] }] }
it('guards dirty closing, preserves edits when continuing and locks during saving', async () => {
  const wrapper = mount(ProductForm, { props: { initial, submitLabel: '保存' }, global: { stubs: { RouterLink: true } } })
  await wrapper.findAll('input').find(input => (input.element as HTMLInputElement).value === '合成父体')!.setValue('修改父体')
  wrapper.vm.requestClose(); await flushPromises()
  expect(wrapper.find('[role="alertdialog"]').exists()).toBe(true)
  await wrapper.get('.unsaved-keep').trigger('click')
  expect(wrapper.emitted('cancel')).toBeUndefined()
  await wrapper.setProps({ busy: true }); wrapper.vm.requestClose(); await flushPromises()
  expect(wrapper.find('[role="alertdialog"]').exists()).toBe(false)
  await wrapper.setProps({ busy: false }); wrapper.vm.requestClose(); await flushPromises()
  await wrapper.get('.unsaved-discard').trigger('click')
  expect(wrapper.emitted('cancel')).toHaveLength(1)
})
it('loads a historical disabled supplier on demand and retains its association', async () => {
  vi.mocked(domainApi.suppliers).mockResolvedValue({ items: [], totalCount: 0, page: 0, size: 100 })
  vi.mocked(domainApi.supplier).mockResolvedValue({ id: 's1', name: '合成历史供应商', status: 'DISABLED', version: 1 })
  const wrapper = mount(ProductForm, { props: { initial, submitLabel: '保存' }, global: { stubs: { RouterLink: true } } })
  await flushPromises(); expect(domainApi.suppliers).not.toHaveBeenCalled()
  await wrapper.findAll('.product-editor-tabs button').find(button => button.text() === '采购信息')!.trigger('click'); await flushPromises()
  expect(wrapper.text()).toContain('合成历史供应商')
  expect(wrapper.text()).toContain('已停用')
  await wrapper.get('form').trigger('submit')
  expect(wrapper.emitted('submit')?.[0]?.[0]).toMatchObject({ variants: [{ suppliers: [{ supplierId: 's1' }] }] })
})
it('uses a parent summary for a single child and opens the requested missing field without changing the parent', async () => {
  const wrapper = mount(ProductForm, { props: { initial, submitLabel: '保存', focusVariantId: 'v1', focusField: 'weight' }, global: { stubs: { RouterLink: true } } })
  await flushPromises()
  expect(wrapper.find('.product-form__parent').exists()).toBe(false)
  expect(wrapper.get('.product-parent-context').text()).toContain('合成父体')
  expect(wrapper.get('[data-product-field="weight"]').isVisible()).toBe(true)
  expect(wrapper.get('[data-editor-tab="basic"]').isVisible()).toBe(false)
  await wrapper.get('[data-product-field="weight"]').setValue(150)
  await wrapper.get('form').trigger('submit')
  expect(wrapper.emitted('submit')?.[0]?.[0]).toMatchObject({ productName: '合成父体', variants: [{ id: 'v1', packageWeightGrams: 150 }] })
})
