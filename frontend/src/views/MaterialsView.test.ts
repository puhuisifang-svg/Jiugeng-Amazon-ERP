import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { createMaterial, replaceMaterialSupplierLinks, transact, updateSafetyStock, uploadImage } = vi.hoisted(() => ({
  createMaterial: vi.fn(), replaceMaterialSupplierLinks: vi.fn(), transact: vi.fn(), updateSafetyStock: vi.fn(), uploadImage: vi.fn(),
}))

vi.mock('@/config/runtime', () => ({ runtimeConfig: { authMode: 'password' } }))
vi.mock('@/state/session', () => ({ session: { canAccess: () => true } }))
vi.mock('@/api/catalog', () => ({ catalogApi: { uploadImage, imageBlob: vi.fn() } }))
vi.mock('@/api/domain', () => ({
  domainApi: {
    materials: vi.fn(async () => ({ items: [
      { id: 'm1', materialCode: 'MAT-1', name: '纸箱', materialType: '包装', specification: '30×20', unit: 'PCS', status: 'ACTIVE' },
      { id: 'm2', materialCode: 'MAT-2', name: '胶带', materialType: '耗材', unit: 'ROLL', status: 'ACTIVE' },
    ], mockData: false })),
    balances: vi.fn(async () => ({ items: [{ warehouseCode: 'MAIN', materialId: 'm1', materialCode: 'MAT-1',
      materialName: '纸箱', quantity: 2, safetyStock: 3, unit: 'PCS', lowStock: true }], mockData: false })),
    suppliers: vi.fn(async () => ({ items: [{ id: 's1', name: '包装供应商', status: 'ACTIVE', version: 0 }], totalCount: 1, page: 0, size: 100 })),
    materialSupplierLinks: vi.fn(async () => []),
    createMaterial,
    replaceMaterialSupplierLinks,
    transact,
    updateSafetyStock,
  },
}))

import MaterialsView from './MaterialsView.vue'

const RouterLink = { props: ['to'], template: '<a :href="to"><slot /></a>' }
const AuthenticatedCatalogImage = { props: ['imageId', 'alt'], template: '<span class="image-stub">{{ imageId || alt }}</span>' }
const global = { stubs: { RouterLink, AuthenticatedCatalogImage } }

describe('MaterialsView merged material management', () => {
  beforeEach(() => {
    createMaterial.mockReset(); replaceMaterialSupplierLinks.mockReset(); transact.mockReset(); updateSafetyStock.mockReset(); uploadImage.mockReset()
  })

  it('shows master data and inventory in one filterable table', async () => {
    const wrapper = mount(MaterialsView, { global })
    await flushPromises()

    expect(wrapper.text()).toContain('MAT-1')
    expect(wrapper.text()).toContain('低库存')
    expect(wrapper.text()).toContain('未初始化')
    expect(wrapper.get('a[href="/inventory/ledger"]').text()).toContain('库存流水')

    await wrapper.get('input[placeholder="辅料编码、名称或规格"]').setValue('胶带')
    expect(wrapper.text()).not.toContain('MAT-1')
    expect(wrapper.text()).toContain('MAT-2')
  })

  it('opens an inventory command from a material row and updates the merged balance', async () => {
    transact.mockResolvedValue({ item: { id: 't1' }, balance: { warehouseCode: 'MAIN', materialId: 'm2',
      materialCode: 'MAT-2', materialName: '胶带', quantity: 5, safetyStock: 0, unit: 'ROLL', lowStock: false }, replayed: false })
    const wrapper = mount(MaterialsView, { global })
    await flushPromises()

    const row = wrapper.findAll('tbody tr').find(item => item.text().includes('MAT-2'))!
    await row.findAll('button').find(button => button.text() === '库存')!.trigger('click')
    const form = wrapper.findAll('form').find(item => item.text().includes('提交正式库存命令'))!
    expect((form.get('select').element as HTMLSelectElement).value).toBe('m2')
    await form.get('input[type="number"]').setValue('5')
    await form.trigger('submit')
    await flushPromises()

    expect(transact).toHaveBeenCalledWith(expect.objectContaining({ materialId: 'm2', type: 'INBOUND', quantity: 5, unit: 'ROLL' }))
    expect(wrapper.findAll('tbody tr').find(item => item.text().includes('MAT-2'))!.text()).toContain('5')
  })

  it('keeps material and inventory operations exclusive and offers common units', async () => {
    const wrapper = mount(MaterialsView, { global })
    await flushPromises()

    const toolbar = wrapper.get('.material-toolbar')
    await toolbar.findAll('button').find(button => button.text() === '添加辅料')!.trigger('click')
    expect(wrapper.text()).toContain('新增辅料')
    expect(wrapper.text()).not.toContain('提交正式库存命令')
    const materialForm = wrapper.findAll('form').find(item => item.text().includes('新增辅料'))!
    const unitSelect = materialForm.findAll('select').find(select => select.text().includes('件'))!
    expect(unitSelect.text()).toContain('件')
    expect(unitSelect.text()).toContain('套')

    await toolbar.findAll('button').find(button => button.text() === '库存操作')!.trigger('click')
    expect(wrapper.text()).not.toContain('新增辅料')
    expect(wrapper.text()).toContain('提交正式库存命令')
  })

  it('uploads a material image and saves supplier links for a new material', async () => {
    uploadImage.mockResolvedValue({ id: 'image-1', purpose: 'MATERIAL_IMAGE' })
    createMaterial.mockResolvedValue({ item: { id: 'm3', materialCode: 'MAT-3', name: '标签', materialType: '耗材', unit: '件', status: 'ACTIVE', imageAssetId: 'image-1' }, mockData: false })
    replaceMaterialSupplierLinks.mockResolvedValue([])
    const wrapper = mount(MaterialsView, { global })
    await flushPromises()
    await wrapper.get('.material-toolbar button').trigger('click')
    const form = wrapper.findAll('form').find(item => item.text().includes('新增辅料'))!
    const textInputs = form.findAll('input').filter(input => input.attributes('type') !== 'file' && input.attributes('type') !== 'radio')
    await textInputs[0]!.setValue('MAT-3')
    await textInputs[1]!.setValue('标签')
    await textInputs[2]!.setValue('耗材')
    const file = new File(['image'], 'material.png', { type: 'image/png' })
    Object.defineProperty(form.get('input[type="file"]').element, 'files', { value: [file] })
    await form.get('input[type="file"]').trigger('change')
    await flushPromises()
    await form.get('select[aria-label="添加辅料供应商"]').setValue('s1')
    await form.trigger('submit')
    await flushPromises()

    expect(uploadImage).toHaveBeenCalledWith('MATERIAL_IMAGE', file)
    expect(createMaterial).toHaveBeenCalledWith(expect.objectContaining({ unit: '件', imageAssetId: 'image-1' }))
    expect(replaceMaterialSupplierLinks).toHaveBeenCalledWith('m3', [expect.objectContaining({ supplierId: 's1', primary: true })])
  })
})
