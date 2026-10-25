import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({ list: vi.fn(), products: vi.fn(), save: vi.fn(), saveProduct: vi.fn(), deleteProduct: vi.fn(), skus: vi.fn(), canAccess: vi.fn(), leave: vi.fn() }))
vi.mock('@/api/customers', () => ({ customersApi: mocks }))
vi.mock('@/api/domain', () => ({ domainApi: { skus: mocks.skus } }))
vi.mock('@/state/session', () => ({ session: { canAccess: mocks.canAccess } }))
vi.mock('vue-router', () => ({ useRoute: () => ({ query: {} }), onBeforeRouteLeave: mocks.leave, onBeforeRouteUpdate: vi.fn(), RouterLink: { template: '<a><slot /></a>' } }))
import CustomerManagementView from './CustomerManagementView.vue'

const customer = { id: 'c1', name: '合成客户', account1688: 'synthetic-buyer', contactPerson: '', phone: '', wechatId: '', address: '', remark: '', status: 'ACTIVE', version: 2 }
const sku = { productId: 'p1', variant: { id: 'v1', skuCode: 'SYN-BLUE', variantName: '蓝色SKU' } }
const product = { id: 'cp1', customerId: 'c1', customerName: '合成客户', customerStatus: 'ACTIVE', productId: 'p1', variantId: 'v1', productName: '蓝色SKU', skuCode: 'SYN-BLUE', supplyPrice: 12.5, priceUnit: '套', customerProductCode: 'CLIENT-01', requirements: '合成贴标需求', status: 'ACTIVE', version: 3 }
function render(kind: 'info' | 'products' = 'info') {
  return mount(CustomerManagementView, { props: { kind }, global: { stubs: { ShipmentProductImage: true } } })
}
function page<T>(items: T[], totalCount = items.length) { return { items, totalCount, page: 0, size: 20 } }
beforeEach(() => {
  vi.clearAllMocks()
  mocks.canAccess.mockReturnValue(true)
  mocks.list.mockResolvedValue(page([customer])); mocks.products.mockResolvedValue(page([product])); mocks.skus.mockResolvedValue(page([sku]))
  mocks.save.mockResolvedValue(customer); mocks.saveProduct.mockResolvedValue(product)
  HTMLDialogElement.prototype.showModal = function () { this.open = true }
  HTMLDialogElement.prototype.close = function () { this.open = false }
})

describe('客户管理', () => {
  it('edits invoice details and selects the default customer without exposing them in list rows', async () => {
    const wrapper = render(); await flushPromises()
    await wrapper.findAll('tbody button').find(button => button.text() === '设为默认')!.trigger('click'); await flushPromises()
    expect((wrapper.get('dialog input[type="checkbox"]').element as HTMLInputElement).checked).toBe(true)
    const field = (label: string) => wrapper.findAll('dialog label').find(item => item.text().startsWith(label))!.get('input')
    await field('开票抬头').setValue('合成发票抬头'); await field('纳税人识别号').setValue('SYN-TAX')
    await field('开户银行').setValue('合成银行'); await field('银行账号').setValue('SYN-ACCOUNT')
    await wrapper.get('dialog form').trigger('submit'); await flushPromises()
    expect(mocks.save).toHaveBeenCalledWith('c1', expect.objectContaining({ defaultCustomer: true, invoiceTitle: '合成发票抬头', taxNumber: 'SYN-TAX', bankName: '合成银行', bankAccount: 'SYN-ACCOUNT', version: 2 }))
    expect(wrapper.get('tbody').text()).not.toContain('SYN-ACCOUNT'); wrapper.unmount()
  })
  it('adds and removes independent titles without changing the list row before saving', async () => {
    const first = { invoiceTitle: '合成甲', taxNumber: 'A', registeredAddress: '', registeredPhone: '', bankName: '', bankAccount: '' }
    mocks.list.mockResolvedValue(page([{ ...customer, invoiceTitles: [first] }]))
    const wrapper = render(); await flushPromises()
    await wrapper.get('tbody button').trigger('click'); await flushPromises()
    await wrapper.findAll('dialog button').find(b => b.text() === '添加发票抬头')!.trigger('click')
    expect(wrapper.findAll('.customer-title-card')).toHaveLength(2)
    const second = wrapper.findAll('.customer-title-card')[1]!
    await second.findAll('input')[0]!.setValue('合成乙')
    await second.findAll('input')[1]!.setValue('B')
    await wrapper.findAll('.customer-title-card')[0]!.get('button').trigger('click')
    await wrapper.get('dialog form').trigger('submit'); await flushPromises()
    expect(mocks.save).toHaveBeenCalledWith('c1', expect.objectContaining({
      invoiceTitles: [expect.objectContaining({ invoiceTitle: '合成乙', taxNumber: 'B' })],
    }))
    expect(first.invoiceTitle).toBe('合成甲'); wrapper.unmount()
  })
  it('deletes a customer product only after confirmation and preserves the source', async () => {
    mocks.deleteProduct.mockResolvedValue(undefined)
    const wrapper = render('products'); await flushPromises()
    await wrapper.findAll('tbody button').find(b => b.text() === '删除')!.trigger('click')
    expect(mocks.deleteProduct).not.toHaveBeenCalled()
    expect(wrapper.get('dialog').text()).toContain('不删除源产品')
    await wrapper.get('dialog .danger-button').trigger('click'); await flushPromises()
    expect(mocks.deleteProduct).toHaveBeenCalledWith('cp1', 3)
    expect(wrapper.find('dialog').exists()).toBe(false)
    expect(wrapper.text()).toContain('源产品及 SKU 保留'); wrapper.unmount()
  })
  it('renders an unknown price as unpriced and saves blank price as null', async () => {
    mocks.products.mockResolvedValue(page([{ ...product, supplyPrice: null }]))
    const wrapper = render('products'); await flushPromises()
    expect(wrapper.text()).toContain('未定价')
    await wrapper.get('tbody button').trigger('click'); await flushPromises()
    expect((wrapper.get('dialog input[type="number"]').element as HTMLInputElement).value).toBe('')
    await wrapper.get('dialog form').trigger('submit'); await flushPromises()
    expect(mocks.saveProduct).toHaveBeenCalledWith('cp1', expect.objectContaining({ supplyPrice: null }))
    wrapper.unmount()
  })

  it('lists customers with fixed actions, search and pagination; read-only users cannot edit', async () => {
    mocks.list.mockResolvedValue(page([customer], 21)); mocks.canAccess.mockReturnValue(false)
    const wrapper = render(); await flushPromises()
    expect(wrapper.text()).toContain('合成客户'); expect(wrapper.get('thead .customer-actions').text()).toBe('操作')
    expect(wrapper.find('.material-toolbar button').exists()).toBe(false)
    expect(wrapper.findAll('tbody button')).toHaveLength(0)
    await wrapper.get('input[placeholder*="1688"]').setValue('buyer')
    await wrapper.get('form.filter-bar').trigger('submit'); await flushPromises()
    expect(mocks.list).toHaveBeenLastCalledWith(expect.objectContaining({ q: 'buyer', page: 0 }))
    await wrapper.get('[aria-label="下一页"]').trigger('click'); await flushPromises()
    expect(mocks.list).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 }))
    wrapper.unmount()
  })
  it('protects dirty input from backdrop, Escape and navigation and retains failed saves', async () => {
    const wrapper = render(); await flushPromises()
    await wrapper.get('.material-toolbar button').trigger('click'); await flushPromises()
    await wrapper.get('dialog input[required]').setValue('新合成客户')
    await wrapper.get('dialog').trigger('click')
    expect((wrapper.get('dialog input[required]').element as HTMLInputElement).value).toBe('新合成客户')
    expect(mocks.leave.mock.calls[0]![0]()).toBe(false)
    await flushPromises(); expect(wrapper.get('dialog').text()).toContain('放弃未保存')
    await wrapper.get('dialog button').trigger('click'); await flushPromises()
    await wrapper.get('dialog').trigger('cancel'); await flushPromises()
    expect(wrapper.get('dialog').text()).toContain('放弃未保存')
    await wrapper.get('dialog button').trigger('click'); await flushPromises()
    mocks.save.mockRejectedValueOnce(new Error('模拟保存失败'))
    await wrapper.get('dialog form').trigger('submit'); await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toBe('模拟保存失败')
    expect((wrapper.get('dialog input[required]').element as HTMLInputElement).value).toBe('新合成客户')
    await wrapper.get('dialog form').trigger('submit'); await flushPromises()
    expect(mocks.save).toHaveBeenLastCalledWith(undefined, expect.objectContaining({ name: '新合成客户', status: 'ACTIVE' }))
    expect(wrapper.find('dialog').exists()).toBe(false); expect(wrapper.text()).toContain('客户信息已保存')
    wrapper.unmount()
  })
  it('allows deliberate discard and preserves version on editing', async () => {
    const wrapper = render(); await flushPromises()
    await wrapper.get('tbody button').trigger('click'); await flushPromises()
    await wrapper.get('dialog input[required]').setValue('修改合成客户')
    await wrapper.get('dialog [aria-label="关闭表单"]').trigger('click'); await flushPromises()
    await wrapper.get('dialog .danger-button').trigger('click')
    expect(wrapper.find('dialog').exists()).toBe(false); expect(mocks.save).not.toHaveBeenCalled()
    await wrapper.get('tbody button').trigger('click'); await flushPromises()
    await wrapper.get('dialog form').trigger('submit'); await flushPromises()
    expect(mocks.save).toHaveBeenCalledWith('c1', expect.objectContaining({ name: '合成客户', version: 2 }))
    wrapper.unmount()
  })
  it('selects paginated customers and SKUs and saves independent pricing and requirements', async () => {
    mocks.list.mockResolvedValue(page([customer], 21)); mocks.skus.mockResolvedValue(page([sku], 21))
    const wrapper = render('products'); await flushPromises()
    expect(wrapper.text()).toContain('¥12.50 / 套'); expect(wrapper.text()).toContain('合成贴标需求')
    await wrapper.get('.material-toolbar button').trigger('click'); await flushPromises()
    await wrapper.get('select[aria-label="选择客户"]').setValue('c1')
    await wrapper.get('select[aria-label="选择SKU"]').setValue('v1')
    const nextSku = wrapper.findAll('dialog button').find(button => button.text() === '下一页SKU')!
    await nextSku.trigger('click'); await flushPromises()
    expect(mocks.skus).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1, exact: false, status: 'ACTIVE', productStatus: 'ACTIVE' }))
    await wrapper.get('dialog input[type="number"]').setValue('9.80')
    await wrapper.get('input[placeholder="客户内部的产品编号"]').setValue('CLIENT-B')
    await wrapper.get('input[maxlength="2000"]').setValue('合成定制包装')
    await wrapper.get('dialog form').trigger('submit'); await flushPromises()
    expect(mocks.saveProduct).toHaveBeenCalledWith(undefined, expect.objectContaining({ customerId: 'c1', productId: 'p1', variantId: 'v1', supplyPrice: 9.8, customerProductCode: 'CLIENT-B', requirements: '合成定制包装' }))
    wrapper.unmount()
  })
  it('blocks missing selection, invalid prices, repeated saves and closing while saving', async () => {
    const wrapper = render('products'); await flushPromises()
    await wrapper.get('.material-toolbar button').trigger('click'); await flushPromises()
    await wrapper.get('dialog form').trigger('submit'); await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('请选择客户')
    await wrapper.get('select[aria-label="选择客户"]').setValue('c1'); await wrapper.get('select[aria-label="选择SKU"]').setValue('v1')
    await wrapper.get('dialog input[type="number"]').setValue('1.001'); await wrapper.get('dialog form').trigger('submit')
    expect(wrapper.get('[role="alert"]').text()).toContain('最多两位小数'); expect(mocks.saveProduct).not.toHaveBeenCalled()
    let finish!: (value: unknown) => void
    mocks.saveProduct.mockImplementation(() => new Promise(resolve => { finish = resolve }))
    await wrapper.get('dialog input[type="number"]').setValue('1.00'); await wrapper.get('dialog form').trigger('submit')
    await wrapper.get('dialog form').trigger('submit'); await wrapper.get('dialog').trigger('cancel')
    expect(mocks.saveProduct).toHaveBeenCalledTimes(1); expect(wrapper.get('dialog button[aria-label="关闭表单"]').attributes('disabled')).toBeDefined()
    finish(product); await flushPromises(); expect(wrapper.find('dialog').exists()).toBe(false)
    wrapper.unmount()
  })
  it('keeps existing customer/SKU immutable and resets filters when switching pages', async () => {
    const wrapper = render('products'); await flushPromises()
    await wrapper.get('tbody button').trigger('click'); await flushPromises()
    expect(wrapper.find('select[aria-label="选择SKU"]').exists()).toBe(false)
    expect(wrapper.get('dialog').text()).toContain('蓝色SKU')
    await wrapper.get('dialog form').trigger('submit'); await flushPromises()
    expect(mocks.saveProduct).toHaveBeenCalledWith('cp1', expect.objectContaining({ variantId: 'v1', version: 3 }))
    await wrapper.get('form.filter-bar input').setValue('SKU-only')
    await wrapper.setProps({ kind: 'info' }); await flushPromises()
    expect(mocks.list).toHaveBeenLastCalledWith(expect.objectContaining({ q: '', page: 0 }))
    wrapper.unmount()
  })
})
