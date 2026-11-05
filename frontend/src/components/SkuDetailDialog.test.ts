import { mount, flushPromises } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import SkuDetailDialog from './SkuDetailDialog.vue'
import { blankCustoms } from '@/api/customs'
import { domainApi, type SkuListItemDto } from '@/api/domain'

const row = (): SkuListItemDto => ({ productId: 'synthetic', productCode: 'SYN', productName: '测试父体', productStatus: 'ACTIVE', businessAvailable: true, variant: { skuCode: 'SYN-S', variantName: '测试子体', status: 'ACTIVE', attributes: [], suppliers: [], materials: [], imageAssetId: 'synthetic-image', customs: { ...blankCustoms(), chinaCode: '6307909000', nameZh: '测试申报品名', measurementUnit: '千克/个', state: 'REVIEWED', elementFields: [{ key: 'compositionContent', label: '成分含量' }], elements: { compositionContent: '测试成分' } } } })
const options = { stubs: { RouterLink: true, AuthenticatedCatalogImage: true } }
describe('SKU detail information', () => {
  it('shows images before basic fields and saved dynamic customs without mutation', async () => {
    vi.spyOn(domainApi, 'productHistory').mockResolvedValue([])
    const input = row(), snapshot = JSON.stringify(input)
    const w = mount(SkuDetailDialog, { props: { detailRow: input, writable: true }, global: options })
    expect(w.findAll('.product-editor-tabs button').map(b => b.text())).not.toContain('图片管理')
    expect(w.get('.sku-basic-panel').element.firstElementChild?.className).toContain('sku-basic-image')
    await w.findAll('button').find(b => b.text() === '报关资料')!.trigger('click')
    expect(w.get('.sku-customs-panel').text()).toContain('6307909000')
    expect(w.get('.sku-customs-panel').text()).toContain('成分含量测试成分')
    expect(w.get('.sku-customs-panel').text()).toContain('千克/个')
    expect(w.get('.sku-customs-panel').text()).not.toContain('资料已核对')
    expect(w.get('.sku-customs-panel').find('input').exists()).toBe(false)
    expect(JSON.stringify(input)).toBe(snapshot)
    await w.findAll('button').find(b => b.text() === '编辑 SKU')!.trigger('click')
    expect(w.emitted('edit')?.[0]?.[0]).toEqual(input)
    w.unmount(); vi.restoreAllMocks()
  })
  it('handles missing customs and resets the tab when another SKU is opened', async () => {
    vi.spyOn(domainApi, 'productHistory').mockResolvedValue([])
    const input = row(); input.variant.customs = null
    const w = mount(SkuDetailDialog, { props: { detailRow: input, writable: false }, global: options })
    await w.findAll('button').find(b => b.text() === '报关资料')!.trigger('click')
    expect(w.text()).toContain('暂无报关资料。')
    expect(w.text()).not.toContain('编辑 SKU')
    await w.setProps({ detailRow: row() }); await flushPromises()
    expect(w.find('.sku-basic-panel').exists()).toBe(true)
    w.unmount(); vi.restoreAllMocks()
  })
})
