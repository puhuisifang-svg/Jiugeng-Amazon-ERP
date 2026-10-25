import { mount, flushPromises } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import DashboardShipmentRow from './DashboardShipmentRow.vue'
import type { TodayShipment } from '@/api/dashboard'
vi.mock('@/api/domain', () => ({ domainApi: { product: vi.fn(async () => ({ variants: [] })) } }))
describe('shipment name presentation', () => {
  it('renders API-supplied partial name formatting without formatting customer metadata', async () => {
    const item = { id: 'synthetic', productId: 'p', variantId: 's', productCode: 'S', productName: '名称\n规格', quantity: 3, boxCount: 2, namePresentation: { highlighted: false, styles: [{ start: 0, end: 3, color: 'BLACK', bold: true }, { start: 3, end: 5, color: 'RED', bold: false }] } } as TodayShipment
    const wrapper = mount(DashboardShipmentRow, { props: { item, scope: 'today-shipments' }, global: { stubs: { ShipmentProductImage: true, ShipmentFiles: true, ShipmentManuals: true } } })
    await flushPromises()
    const runs = wrapper.findAll('.shipment-product-cell .product-display-name span')
    expect(runs[0]!.element.textContent).toBe('名称\n')
    expect(runs[1]!.text()).toBe('规格')
    expect(runs[1]!.attributes('style')).toContain('color: rgb(198, 40, 40)')
    expect(runs[1]!.attributes('style')).toContain('font-weight: 400')
    expect(wrapper.get('.shipment-product-cell').text()).toContain('SKU：S')
    wrapper.unmount()
  })
})
