import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
import PurchaseLogisticsDialog from './PurchaseLogisticsDialog.vue'
import { purchaseApi, type PurchaseOrder } from '@/api/purchases'
vi.mock('@/api/purchases', () => ({ purchaseApi: { logistics: vi.fn() } }))
const order = { id: 'syn-order', orderNo: 'SYN-PO', supplierName: '合成供应商', version: 2, trackingNumber: 'SYN1', carrier: '合成物流' } as PurchaseOrder
beforeEach(() => { vi.clearAllMocks(); HTMLDialogElement.prototype.showModal = function () { this.open = true }; HTMLDialogElement.prototype.close = function () { this.open = false } })
it('preserves failed edits and sends version with logistics data', async () => {
 const w = mount(PurchaseLogisticsDialog, { props: { order, writable: true } })
 await w.get('input[placeholder="填写完整运单号"]').setValue('SYN2')
 vi.mocked(purchaseApi.logistics).mockRejectedValueOnce(new Error('版本冲突'))
 await w.get('form').trigger('submit'); await flushPromises()
 expect(w.text()).toContain('版本冲突'); expect((w.get('input[placeholder="填写完整运单号"]').element as HTMLInputElement).value).toBe('SYN2')
 vi.mocked(purchaseApi.logistics).mockResolvedValue({ ...order, trackingNumber: 'SYN2', version: 3 })
 await w.get('form').trigger('submit'); await flushPromises()
 expect(purchaseApi.logistics).toHaveBeenLastCalledWith('syn-order', 2, '合成物流', 'SYN2', null); expect(w.emitted('saved')).toHaveLength(1); w.unmount()
})
it('does not permit read-only users to save', async () => {
 const w = mount(PurchaseLogisticsDialog, { props: { order, writable: false } })
 expect(w.find('button[type=submit]').exists()).toBe(false)
 await w.get('form').trigger('submit'); expect(purchaseApi.logistics).not.toHaveBeenCalled(); w.unmount()
})

it('allows a waybill image without a tracking number',async()=>{
 const w=mount(PurchaseLogisticsDialog,{props:{order:{...order,trackingNumber:'',waybillImageId:'synthetic-image'},writable:true}})
 await w.get('form').trigger('submit');await flushPromises()
 expect(purchaseApi.logistics).toHaveBeenLastCalledWith('syn-order',2,'合成物流','','synthetic-image')
 w.unmount()
})
