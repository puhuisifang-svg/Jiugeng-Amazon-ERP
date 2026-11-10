import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
import type { Customer, CustomerProduct } from '@/api/customers'
import type { CustomerOrder } from '@/api/orders'
vi.mock('vue-router', () => ({ onBeforeRouteLeave: vi.fn() }))
vi.mock('@/api/customers', async (importOriginal) => ({ ...await importOriginal<typeof import('@/api/customers')>(), customersApi: { list: vi.fn(), defaultCustomer: vi.fn(), products: vi.fn() } }))
import { customersApi } from '@/api/customers'
import CustomerOrderEditor from './CustomerOrderEditor.vue'
const customer = { id: 'c1', name: '合成客户', status: 'ACTIVE', contactPerson: '测试', phone: 'SYN', address: '测试地址', invoiceTitles: [{ invoiceTitle: '测试抬头', taxNumber: 'SYN-TAX' }] } as Customer
const product = { id: 'cp1', productName: '测试商品\n红色规格', priceUnit: '米', specification:'合成规格 20 × 30 cm', supplyPrice:'12.50', skuCode: 'SYN-SKU', customerProductCode: 'CLIENT-001' } as CustomerProduct
beforeEach(() => {
  HTMLDialogElement.prototype.showModal = function () { this.open = true }
  HTMLDialogElement.prototype.close = function () { this.open = false }
})
beforeEach(() => { vi.clearAllMocks(); vi.mocked(customersApi.list).mockResolvedValue({ items: [customer], totalCount: 1, page: 0, size: 100 }); vi.mocked(customersApi.defaultCustomer).mockResolvedValue(customer); vi.mocked(customersApi.products).mockResolvedValue({ items: [product], totalCount: 1, page: 0, size: 100 }) })
it('retains input on backdrop, confirms discard, defaults unique title and emits decimal lines', async () => {
  const w = mount(CustomerOrderEditor, { props: { busy: false, error: '' } }); await flushPromises()
  const labels = w.findAll('label'), byLabel = (name: string) => labels.find(l => l.text().startsWith(name))!
  await w.findAll('button').find(b=>b.text()==='选择客户商品')!.trigger('click');await flushPromises();await w.get('input[type=checkbox]').setValue(true);await w.findAll('button').find(b=>b.text()==='添加所选（1）')!.trigger('click')
  expect(w.get('[aria-label="规格型号"]').element).toHaveProperty('value','合成规格 20 × 30 cm')
  expect(w.get('[aria-label="不含税单价"]').element).toHaveProperty('value','12.50')
  expect(w.get('.order-customer-code').text()).toBe('CLIENT-001')
  expect(w.get('.order-line-sku').text()).toContain('SYN-SKU')
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
  const initial = reactive({ id: 'o1', customerName: customer.name, version: 3, details: { customerId: 'c1', lines: [line], invoiceTitle: '历史抬头', taxNumber: 'SYN-OLD', recipient:'历史收货人',phone:'OLD',address:'历史地址' }, invoiceTitle: { invoiceTitle: '历史抬头', taxNumber: 'SYN-OLD' }, lines: [{ id: 'l1', details: line, product: { customerProductCode: 'HISTORICAL-001' }, skuCode: 'SYN-OLD' }] }) as unknown as CustomerOrder
  const w = mount(CustomerOrderEditor, { props: { initial, busy: false, error: '' } }); await flushPromises()
  expect(w.get('.order-customer-code').text()).toBe('HISTORICAL-001')
  expect(customersApi.products).not.toHaveBeenCalled()
  expect(w.text()).toContain('编辑客户订单'); await w.get('form').trigger('submit')
  expect(w.emitted('save')?.[0]?.[0]).toMatchObject({ version: 3, invoiceTitle: '历史抬头', address:'历史地址',recipient:'历史收货人', lines: [expect.objectContaining({ id: 'l1' })] }); w.unmount()
})

it('uses one order reference and keeps shipping fields together without customer search',async()=>{
 const w=mount(CustomerOrderEditor,{props:{busy:false,error:''}});await flushPromises()
 expect(w.find('input[placeholder="客户名称或1688账号"]').exists()).toBe(false)
 const reference=w.findAll('label').find(l=>l.text().startsWith('客户订单号 / 合同号'))!
 await reference.get('input').setValue('SYN-REF')
 await w.findAll('button').find(b=>b.text()==='选择客户商品')!.trigger('click');await flushPromises()
 await w.get('input[type=checkbox]').setValue(true);await w.findAll('button').find(b=>b.text()==='添加所选（1）')!.trigger('click')
 await w.get('[aria-label="不含税单价"]').setValue('10');await w.get('form').trigger('submit')
 expect(w.emitted('save')?.[0]?.[0]).toMatchObject({externalNumber:'SYN-REF',contractNumber:''})
 expect(w.findAll('button').some(b=>b.text()==='保存')).toBe(true);expect(w.text()).not.toContain('保存草稿');w.unmount()
})

it('clears codes with customer changes and never substitutes SKU for a missing customer code', async () => {
 const other={...customer,id:'c2',name:'另一个合成客户'}
 vi.mocked(customersApi.list).mockResolvedValue({items:[customer,other],totalCount:2,page:0,size:100})
 const w=mount(CustomerOrderEditor,{props:{busy:false,error:''}});await flushPromises()
 const add=async(p:CustomerProduct)=>{await w.findAll('button').find(b=>b.text()==='选择客户商品')!.trigger('click');await flushPromises();w.findComponent({name:'CustomerOrderProductPicker'}).vm.$emit('confirm',[p]);await flushPromises()}
 await add(product);expect(w.get('.order-customer-code').text()).toBe('CLIENT-001')
 await w.findAll('label').find(l=>l.text().startsWith('客户 *'))!.get('select').setValue('c2')
 expect(w.find('.order-customer-code').exists()).toBe(false)
 await add({...product,id:'cp2',customerProductCode:'',skuCode:'OTHER-SKU'})
 expect(w.get('.order-customer-code').text()).toBe('未填写')
 expect(w.get('.order-line-sku').text()).toContain('OTHER-SKU');w.unmount()
})

it('selects the default address and allows another address or manual edits without changing the directory', async()=>{
 const addresses=[{contactPerson:'甲',phone:'SYN-A',address:'合成地址甲',defaultAddress:false},{contactPerson:'乙',phone:'SYN-B',address:'合成地址乙',defaultAddress:true}]
 const c={...customer,addresses};vi.mocked(customersApi.list).mockResolvedValue({items:[c],totalCount:1,page:0,size:100});vi.mocked(customersApi.defaultCustomer).mockResolvedValue(c)
 const w=mount(CustomerOrderEditor,{props:{busy:false,error:''}});await flushPromises()
 const label=(name:string)=>w.findAll('label').find(l=>l.text().startsWith(name))!
 expect(label('收货人').get('input').element.value).toBe('乙')
 await label('选择收货地址').get('select').setValue('0')
 expect(label('收货地址').get('input').element.value).toBe('合成地址甲')
 await label('收货地址').get('input').setValue('本单临时地址')
 expect(addresses[0]?.address).toBe('合成地址甲');expect(w.text()).not.toContain('发货信息');w.unmount()
})
