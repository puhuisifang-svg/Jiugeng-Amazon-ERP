import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
import { costsApi } from '@/api/costs'
import MaterialCostPriceDialog from './MaterialCostPriceDialog.vue'
vi.mock('@/api/costs', () => ({ costsApi: { price: vi.fn(), purchases: vi.fn(), savePrice: vi.fn() } }))
vi.mock('@/state/session', () => ({ session: { canAccess: () => true } }))
beforeEach(() => { vi.clearAllMocks(); vi.mocked(costsApi.price).mockResolvedValue(null); vi.mocked(costsApi.purchases).mockResolvedValue([]) })
it('requires explicit net confirmation, keeps backdrop input and emits success feedback', async () => {
  const w = mount(MaterialCostPriceDialog, { props: { material: { id: 'm1', name: '合成包装箱', unit: '个' } } }); await flushPromises()
  await w.get('input[type="number"]').setValue('7'); await w.get('[role="dialog"]').trigger('click'); expect(w.emitted('close')).toBeUndefined()
  expect(w.get('input[type="checkbox"]').attributes('required')).toBeDefined()
  await w.get('input[type="checkbox"]').setValue(true); await w.get('form').trigger('submit'); await flushPromises()
  expect(costsApi.savePrice).toHaveBeenCalledWith('m1', expect.objectContaining({ confirmedNet: true, netPrice: 7, version: -1 })); expect(w.emitted('saved')).toHaveLength(1); w.unmount()
})
