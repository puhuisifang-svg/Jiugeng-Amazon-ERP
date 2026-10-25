import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
const mocks = vi.hoisted(() => ({ list: vi.fn(), draft: vi.fn(), save: vi.fn(), canAccess: vi.fn(), leave: vi.fn() }))
vi.mock('@/api/invoices', () => ({ invoicesApi: mocks }))
vi.mock('@/state/session', () => ({ session: { canAccess: mocks.canAccess } }))
vi.mock('vue-router', () => ({ onBeforeRouteLeave: mocks.leave }))
import InvoiceManagementView from './InvoiceManagementView.vue'
const draft = { customerProductId: 'cp1', invoiceTitle: '合成抬头', taxNumber: 'SYN-TAX', registeredAddress: '', registeredPhone: '', bankName: '', bankAccount: '', itemName: '蓝色SKU', specification: '', unit: '件', quantity: '1', netUnitPrice: null, taxRate: '1', shipped: false, invoiced: false, logistics: '' }
const row = { id: 'i1', customerId: 'c1', customerName: '合成客户', details: { ...draft, quantity: '3', netUnitPrice: '10', version: 2 }, netAmount: '30.00', taxAmount: '0.30', totalAmount: '30.30' }
const picker = { name: 'ShipmentCustomerPicker', props: ['disabled'], emits: ['select'], template: '<button type="button" class="pick-product" :disabled="disabled" @click="$emit(\'select\', { id: \'cp1\', customerName: \'合成客户\' })">选择合成客户商品</button>' }
function render() { return mount(InvoiceManagementView, { global: { stubs: { ShipmentCustomerPicker: picker } } }) }
async function add(wrapper: ReturnType<typeof render>) { await wrapper.get('.material-toolbar button').trigger('click'); await flushPromises(); await wrapper.get('.pick-product').trigger('click'); await flushPromises() }
function field(wrapper: ReturnType<typeof render>, name: string) { return wrapper.findAll('dialog label').find(item => item.text().startsWith(name))!.get('input') }
beforeEach(() => {
  vi.clearAllMocks(); mocks.canAccess.mockReturnValue(true)
  mocks.list.mockResolvedValue({ items: [row], totalCount: 1, page: 0, size: 20 }); mocks.draft.mockResolvedValue({ ...draft, invoiceTitles: [draft] }); mocks.save.mockResolvedValue(row)
  HTMLDialogElement.prototype.showModal = function () { this.open = true }
  HTMLDialogElement.prototype.close = function () { this.open = false }
})
describe('发票管理', () => {
  it('shows all fields, snapshots, fixed actions and status filters with pagination', async () => {
    mocks.list.mockResolvedValue({ items: [row], totalCount: 21 })
    const wrapper = render(); await flushPromises()
    for (const text of ['客户名称', '发票抬头信息', '品名', '规格及型号', '单位', '数量', '不含税单价', '税率', '总价', '是否已发货', '是否已开票', '物流信息']) expect(wrapper.get('thead').text()).toContain(text)
    expect(wrapper.get('thead .invoice-actions').text()).toBe('操作')
    expect(wrapper.get('tbody').text()).toContain('¥30.30'); expect(wrapper.get('tbody details').text()).toContain('SYN-TAX')
    await wrapper.get('.filter-bar input').setValue('合成')
    await wrapper.findAll('.filter-bar select')[1]!.setValue('false')
    await wrapper.get('.filter-bar').trigger('submit'); await flushPromises()
    expect(mocks.list).toHaveBeenLastCalledWith({ q: '合成', shipped: '', invoiced: 'false', page: 0, size: 20 })
    await wrapper.get('[aria-label="下一页"]').trigger('click'); await flushPromises()
    expect(mocks.list).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 })); wrapper.unmount()
  })
  it('creates from customer billing snapshot at 1 percent with explicit net price and live total', async () => {
    const wrapper = render(); await flushPromises(); await add(wrapper)
    expect(mocks.draft).toHaveBeenCalledWith('cp1')
    expect((field(wrapper, '发票抬头').element as HTMLInputElement).value).toBe('合成抬头')
    expect((field(wrapper, '税率').element as HTMLInputElement).value).toBe('1')
    expect((field(wrapper, '不含税单价').element as HTMLInputElement).value).toBe('')
    await field(wrapper, '数量').setValue('3'); await field(wrapper, '不含税单价').setValue('10')
    expect(wrapper.get('.invoice-totals').text()).toContain('30.30')
    await wrapper.get('dialog form').trigger('submit'); await flushPromises()
    expect(mocks.save).toHaveBeenCalledWith(undefined, expect.objectContaining({ customerProductId: 'cp1', invoiceTitle: '合成抬头', quantity: 3, netUnitPrice: 10, taxRate: '1' }))
    expect(wrapper.find('dialog').exists()).toBe(false); expect(wrapper.text()).toContain('发票信息已保存'); wrapper.unmount()
  })
  it('keeps input on mask, cancel, navigation and failed save, with deliberate discard', async () => {
    const wrapper = render(); await flushPromises(); await add(wrapper)
    await field(wrapper, '品名').setValue('新合成品名'); await wrapper.get('dialog').trigger('click')
    expect((field(wrapper, '品名').element as HTMLInputElement).value).toBe('新合成品名')
    expect(mocks.leave.mock.calls[0]![0]()).toBe(false); await flushPromises()
    expect(wrapper.get('dialog').text()).toContain('放弃未保存的修改')
    await wrapper.get('dialog button').trigger('click'); await flushPromises()
    expect((field(wrapper, '品名').element as HTMLInputElement).value).toBe('新合成品名')
    await field(wrapper, '不含税单价').setValue('10'); mocks.save.mockRejectedValueOnce(new Error('合成保存失败'))
    await wrapper.get('dialog form').trigger('submit'); await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toBe('合成保存失败')
    await wrapper.get('dialog').trigger('cancel'); await flushPromises()
    await wrapper.get('dialog .danger-button').trigger('click'); expect(wrapper.find('dialog').exists()).toBe(false); wrapper.unmount()
  })
  it('prevents invalid and repeated saves, protects a pending save, preserves version on update', async () => {
    const wrapper = render(); await flushPromises()
    await wrapper.get('tbody button').trigger('click'); await flushPromises()
    expect(wrapper.find('.pick-product').exists()).toBe(false)
    await field(wrapper, '税率').setValue('101'); await wrapper.get('dialog form').trigger('submit')
    expect(mocks.save).not.toHaveBeenCalled(); expect(wrapper.get('[role="alert"]').text()).toContain('税率')
    let done!: () => void
    mocks.save.mockImplementation(() => new Promise<void>(resolve => { done = resolve }))
    await field(wrapper, '税率').setValue('1'); await wrapper.get('dialog form').trigger('submit')
    await wrapper.get('dialog form').trigger('submit'); await wrapper.get('dialog').trigger('cancel')
    expect(mocks.save).toHaveBeenCalledTimes(1); expect(mocks.save).toHaveBeenCalledWith('i1', expect.objectContaining({ version: 2 }))
    expect(wrapper.find('dialog').exists()).toBe(true); done(); await flushPromises(); wrapper.unmount()
  })
  it('requires explicit selection for multiple titles and clears old billing when the customer changes', async () => {
    const second = { ...draft, invoiceTitle: '第二合成抬头', taxNumber: 'TWO', bankAccount: 'SECOND' }
    mocks.draft.mockResolvedValue({ ...draft, invoiceTitle: '', invoiceTitles: [draft, second] })
    const wrapper = render(); await flushPromises(); await add(wrapper)
    expect((field(wrapper, '发票抬头').element as HTMLInputElement).value).toBe('')
    await field(wrapper, '不含税单价').setValue('10')
    await wrapper.get('dialog form').trigger('submit'); expect(mocks.save).not.toHaveBeenCalled()
    const select = wrapper.get('dialog select')
    await select.setValue('1')
    expect((field(wrapper, '发票抬头').element as HTMLInputElement).value).toBe('第二合成抬头')
    expect((field(wrapper, '银行账号').element as HTMLInputElement).value).toBe('SECOND')
    expect(field(wrapper, '发票抬头').attributes('readonly')).toBeDefined()
    wrapper.findComponent({ name: 'ShipmentCustomerPicker' }).vm.$emit('select', undefined)
    await flushPromises()
    expect((field(wrapper, '发票抬头').element as HTMLInputElement).value).toBe('')
    expect(wrapper.get('dialog select').findAll('option')).toHaveLength(1)
    mocks.draft.mockResolvedValue({ ...draft, invoiceTitles: [] })
    await wrapper.get('.pick-product').trigger('click'); await flushPromises()
    expect(wrapper.get('dialog select').attributes('disabled')).toBeDefined()
    expect(wrapper.get('dialog').text()).toContain('客户信息中维护')
    wrapper.unmount()
  })
  it('does not expose write actions to read-only users and surfaces load failures', async () => {
    mocks.canAccess.mockReturnValue(false); const wrapper = render(); await flushPromises()
    expect(wrapper.find('.material-toolbar button').exists()).toBe(false); expect(wrapper.find('tbody button').exists()).toBe(false)
    mocks.list.mockRejectedValueOnce(new Error('合成加载失败')); await wrapper.get('.filter-bar').trigger('submit'); await flushPromises()
    expect(wrapper.text()).toContain('合成加载失败'); expect(wrapper.findAll('tbody tr')).toHaveLength(0); wrapper.unmount()
  })
})
