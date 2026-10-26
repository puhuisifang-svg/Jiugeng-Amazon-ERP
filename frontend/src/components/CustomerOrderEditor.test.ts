import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
import type { Customer, CustomerProduct } from '@/api/customers'
import type { CustomerOrder } from '@/api/orders'
vi.mock('vue-router', () => ({ onBeforeRouteLeave: vi.fn() }))
vi.mock('@/api/customers', () => ({ customersApi: { list: vi.fn(), defaultCustomer: vi.fn(), products: vi.fn() } }))
import { customersApi } from '@/api/customers'
import CustomerOrderEditor from './CustomerOrderEditor.vue'
const customer = { id: 'c1', name: '合成客户', status: 'ACTIVE', contactPerson: '测试', phone: 'SYN', address: '测试地址', invoiceTitles: [{ invoiceTitle: '测试抬头', taxNumber: 'SYN-TAX' }] } as Customer
const product = { id: 'cp1', productName: '测试商品\n红色规格', priceUnit: '米' } as CustomerProduct
beforeEach(() => {
  HTMLDialogElement.prototype.showModal = function () { this.open = true }
  HTMLDialogElement.prototype.close = function () { this.open = false }
})
beforeEach(() => { vi.clearAllMocks(); vi.mocked(customersApi.list).mockResolvedValue({ items: [customer], totalCount: 1, page: 0, size: 100 }); vi.mocked(customersApi.defaultCustomer).mockResolvedValue(customer); vi.mocked(customersApi.products).mockResolvedValue({ items: [product], totalCount: 1, page: 0, size: 100 }) })
it('retains input on backdrop, confirms discard, defaults unique title and emits decimal lines', async () => {
  const w = mount(CustomerOrderEditor, { props: { busy: false, error: '' } }); await flushPromises()
  const labels = w.findAll('label'), byLabel = (name: string) => labels.find(l => l.text().startsWith(name))!
  await byLabel('添加商品').get('select').setValue('cp1'); await byLabel('添加商品').get('button').trigger('click')
  await w.get('[aria-label="数量"]').setValue('1.25'); await w.get('[aria-label="不含税单价"]').setValue('10')
  await byLabel('是否需要开票').get('select').setValue('true')
  await w.get('[aria-label="客户订单表单"]').trigger('click'); expect(w.emitted('close')).toBeUndefined()
  await w.get('form').trigger('submit')
  expect(w.emitted('save')?.[0]?.[0]).toMatchObject({ invoiceTitle: '测试抬头', taxNumber: 'SYN-TAX', lines: [expect.objectContaining({ quantity: 1.25, name: '测试商品\n红色规格' })] })
  await w.findAll('button').find(b => b.text() === '取消')!.trigger('click'); expect(w.text()).toContain('要放弃本次填写吗？')
  await w.findAll('button').find(b => b.text() === '继续填写')!.trigger('click'); expect(w.get('[aria-label="数量"]').element).toHaveProperty('value', '1.25')
  await w.setProps({ busy: true, error: '保存失败，输入保留' }); expect(w.text()).toContain('保存失败，输入保留'); expect(w.get('button[type=submit]').attributes('disabled')).toBeDefined(); w.unmount()
})
it('can edit a reactive order snapshot without DataCloneError or losing line IDs', async () => {
  const { reactive } = await import('vue')
  const line = { id: 'l1', customerProductId: 'cp1', name: '原订单商品', specification: '', quantity: 2, unit: '米', netUnitPrice: '10', taxRate: 1, deliveryDate: '2026-10-26', remark: '' }
  const initial = reactive({ id: 'o1', customerName: customer.name, version: 3, details: { customerId: 'c1', lines: [line], invoiceTitle: '历史抬头', taxNumber: 'SYN-OLD' }, invoiceTitle: { invoiceTitle: '历史抬头', taxNumber: 'SYN-OLD' }, lines: [{ id: 'l1', details: line }] }) as unknown as CustomerOrder
  const w = mount(CustomerOrderEditor, { props: { initial, busy: false, error: '' } }); await flushPromises()
  expect(w.text()).toContain('编辑客户订单'); await w.get('form').trigger('submit')
  expect(w.emitted('save')?.[0]?.[0]).toMatchObject({ version: 3, invoiceTitle: '历史抬头', lines: [expect.objectContaining({ id: 'l1' })] }); w.unmount()
})
