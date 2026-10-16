import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/api/client'

const { deleteMaterials } = vi.hoisted(() => ({ deleteMaterials: vi.fn() }))

const { createMaterial, disableMaterial, enableMaterial, materialSupplierLinks, replaceMaterialSupplierLinks, supplier, transact, updateSafetyStock, uploadImage } = vi.hoisted(() => ({
  createMaterial: vi.fn(), disableMaterial: vi.fn(), enableMaterial: vi.fn(), materialSupplierLinks: vi.fn(), replaceMaterialSupplierLinks: vi.fn(), supplier: vi.fn(), transact: vi.fn(), updateSafetyStock: vi.fn(), uploadImage: vi.fn(),
}))

vi.mock('@/config/runtime', () => ({ runtimeConfig: { authMode: 'password' } }))
vi.mock('@/state/session', () => ({ session: { canAccess: () => true } }))
vi.mock('@/api/catalog', () => ({ catalogApi: { uploadImage, imageBlob: vi.fn() } }))
vi.mock('@/api/domain', () => ({
  domainApi: {
    deleteMaterials,
    materials: vi.fn(async () => ({ items: [
      { id: 'm1', materialCode: 'MAT-1', name: '纸箱', materialType: '包装', specification: '30×20', unit: 'PCS', status: 'ACTIVE', imageUrl: 'https://images.example.com/box.jpg' },
      { id: 'm2', materialCode: 'MAT-2', name: '胶带', materialType: '耗材', unit: 'ROLL', status: 'ACTIVE' },
      { id: 'm3', materialCode: 'MAT-3', name: '标签', materialType: '耗材', unit: '件', status: 'DISABLED' },
    ], mockData: false })),
    balances: vi.fn(async () => ({ items: [{ warehouseCode: 'MAIN', materialId: 'm1', materialCode: 'MAT-1',
      materialName: '纸箱', quantity: 2, safetyStock: 3, unit: 'PCS', lowStock: true }], mockData: false })),
    suppliers: vi.fn(async () => ({ items: [{ id: 's1', name: '包装供应商', status: 'ACTIVE', version: 0 }], totalCount: 1, page: 0, size: 100 })),
    materialSupplierLinks,
    supplier,
    createMaterial,
    disableMaterial,
    enableMaterial,
    replaceMaterialSupplierLinks,
    transact,
    updateSafetyStock,
  },
}))

import MaterialsView from './MaterialsView.vue'
import { domainApi } from '@/api/domain'

const RouterLink = { props: ['to'], template: '<a :href="to"><slot /></a>' }
const AuthenticatedCatalogImage = { props: ['imageId', 'alt'], template: '<span class="image-stub">{{ imageId || alt }}</span>' }
const global = { stubs: { RouterLink, AuthenticatedCatalogImage } }

describe('MaterialsView merged material management', () => {
  beforeEach(() => {
    deleteMaterials.mockReset().mockResolvedValue(undefined)
    createMaterial.mockReset(); disableMaterial.mockReset(); enableMaterial.mockReset(); materialSupplierLinks.mockReset().mockResolvedValue([]); replaceMaterialSupplierLinks.mockReset(); supplier.mockReset(); transact.mockReset(); updateSafetyStock.mockReset(); uploadImage.mockReset()
  })

  it('paginates materials and clears selection when changing pages', async () => {
    vi.mocked(domainApi.materials).mockResolvedValueOnce({ mockData: false, items: Array.from({ length: 21 }, (_, index) => ({
      id: `page-${index}`, materialCode: `PAGE-${index}`, name: `分页辅料${index}`, materialType: '包装', unit: '件', status: 'ACTIVE',
    })) })
    const wrapper = mount(MaterialsView, { global })
    await flushPromises()
    expect(wrapper.findAll('tbody tr')).toHaveLength(20)
    await wrapper.get('[aria-label="选择本页全部辅料"]').setValue(true)
    expect(wrapper.get('.material-toolbar').text()).toContain('删除（20）')
    await wrapper.get('.list-pagination').findAll('button')[1]!.trigger('click')
    expect(wrapper.findAll('tbody tr')).toHaveLength(1)
    expect(wrapper.get('.material-toolbar').text()).not.toContain('删除')
    await wrapper.get('[aria-label="每页条数"]').setValue(50)
    expect(wrapper.findAll('tbody tr')).toHaveLength(21)
  })

  it('requires selection and confirmation and preserves rows on deletion failure', async () => {
    const wrapper = mount(MaterialsView, { global })
    await flushPromises()
    expect(wrapper.get('.material-toolbar').text()).not.toContain('导入')
    expect(wrapper.get('.material-toolbar').text()).not.toContain('删除')
    await wrapper.get('[aria-label="选择辅料 MAT-2"]').setValue(true)
    await wrapper.get('.material-toolbar .danger-button').trigger('click')
    expect(deleteMaterials).not.toHaveBeenCalled()
    deleteMaterials.mockRejectedValueOnce(new ApiError(409, 'MATERIAL_IN_USE', '该辅料已有流水，不能删除'))
    const dialog = wrapper.get('[aria-labelledby="delete-material-title"]')
    await dialog.get('.danger-button').trigger('click'); await flushPromises()
    expect(dialog.get('[role="alert"]').text()).toContain('不能删除')
    expect(wrapper.text()).toContain('MAT-2')
    await dialog.get('.danger-button').trigger('click'); await flushPromises()
    expect(deleteMaterials).toHaveBeenLastCalledWith(['m2'])
    expect(wrapper.get('.material-table').text()).not.toContain('MAT-2')
    expect(wrapper.get('.material-toolbar').text()).not.toContain('删除')
  })

  it('explains insufficient stock inside the inventory dialog without submitting', async () => {
    const wrapper = mount(MaterialsView, { global })
    await flushPromises()
    const row = wrapper.findAll('tbody tr').find(item => item.text().includes('MAT-1'))!
    await row.findAll('button').find(button => button.text() === '库存')!.trigger('click')
    const dialog = wrapper.get('[aria-labelledby="inventory-dialog-title"]')
    await dialog.findAll('select')[1]!.setValue('OUTBOUND')
    await dialog.get('input[type="number"]').setValue(5)
    await dialog.get('form').trigger('submit'); await flushPromises()
    expect(dialog.get('[role="alert"]').text()).toContain('当前可用 2 PCS')
    expect(dialog.get('[role="alert"]').text()).toContain('本次需要 5 PCS')
    expect(transact).not.toHaveBeenCalled()
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

  it('renders a remote material image and opens an enlarged preview', async () => {
    const wrapper = mount(MaterialsView, { global })
    await flushPromises()

    const previewButton = wrapper.get('button[aria-label="查看纸箱大图"]')
    const thumbnail = previewButton.get('img')
    expect(thumbnail.attributes('src')).toBe('https://images.example.com/box.jpg')
    expect(thumbnail.attributes('referrerpolicy')).toBe('no-referrer')

    await previewButton.trigger('click')
    const dialog = wrapper.get('[role="dialog"][aria-label="纸箱图片预览"]')
    expect(dialog.get('img').attributes('src')).toBe('https://images.example.com/box.jpg')
    await dialog.get('button[aria-label="关闭图片预览"]').trigger('click')
    expect(wrapper.find('[aria-label="纸箱图片预览"]').exists()).toBe(false)
  })

  it('opens purchasing suppliers from a material row', async () => {
    materialSupplierLinks.mockResolvedValue([{ id: 'l1', ownerId: 'm1', supplierId: 's1', supplierName: '包装供应商', supplierStatus: 'ACTIVE', purchaseUrl: 'https://detail.1688.com/offer/1.html', primary: true, remark: '月结' }])
    supplier.mockResolvedValue({ id: 's1', name: '包装供应商', contactPerson: '张三', phone: '13800000000', wechatId: 'packaging-zhang',
      qrImageUrl: 'https://images.example.com/wechat-qr.png', status: 'ACTIVE', version: 0 })
    const wrapper = mount(MaterialsView, { global })
    await flushPromises()

    const row = wrapper.findAll('tbody tr').find(item => item.text().includes('MAT-1'))!
    await row.findAll('button').find(button => button.text() === '采购')!.trigger('click')
    await flushPromises()

    const dialog = wrapper.get('[role="dialog"][aria-labelledby="purchase-dialog-title"]')
    expect(materialSupplierLinks).toHaveBeenCalledWith('m1')
    expect(supplier).toHaveBeenCalledWith('s1')
    expect(dialog.text()).toContain('包装供应商')
    expect(dialog.text()).toContain('张三')
    expect(dialog.text()).toContain('13800000000')
    expect(dialog.text()).toContain('packaging-zhang')
    const qrButton = dialog.get('button[aria-label="查看包装供应商微信二维码"]')
    expect(qrButton.get('img').attributes('src')).toBe('https://images.example.com/wechat-qr.png')
    expect(dialog.get('a').attributes('href')).toBe('https://detail.1688.com/offer/1.html')

    await qrButton.trigger('click')
    const preview = wrapper.get('[role="dialog"][aria-label="包装供应商微信二维码图片预览"]')
    expect(preview.get('img').classes()).toContain('supplier-qr-preview')
    expect(wrapper.find('[aria-labelledby="purchase-dialog-title"]').exists()).toBe(true)
  })

  it('keeps material and inventory operations exclusive and offers common units', async () => {
    const wrapper = mount(MaterialsView, { global })
    await flushPromises()

    const toolbar = wrapper.get('.material-toolbar')
    await toolbar.findAll('button').find(button => button.text() === '添加辅料')!.trigger('click')
    expect(wrapper.findAll('[role="dialog"]')).toHaveLength(1)
    expect(wrapper.get('[role="dialog"]').attributes('aria-labelledby')).toBe('material-dialog-title')
    expect(wrapper.find('.material-table').exists()).toBe(true)
    expect(wrapper.text()).toContain('新增辅料')
    expect(wrapper.text()).not.toContain('提交正式库存命令')
    const materialForm = wrapper.findAll('form').find(item => item.text().includes('新增辅料'))!
    const unitSelect = materialForm.findAll('select').find(select => select.text().includes('件'))!
    expect(unitSelect.text()).toContain('件')
    expect(unitSelect.text()).toContain('套')

    await toolbar.findAll('button').find(button => button.text() === '库存操作')!.trigger('click')
    expect(wrapper.findAll('[role="dialog"]')).toHaveLength(1)
    expect(wrapper.get('[role="dialog"]').attributes('aria-labelledby')).toBe('inventory-dialog-title')
    expect(wrapper.find('.material-table').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('新增辅料')
    expect(wrapper.text()).toContain('提交正式库存命令')
    const inventoryForm = wrapper.findAll('form').find(item => item.text().includes('提交正式库存命令'))!
    expect(inventoryForm.text()).toContain('入库')
    expect(inventoryForm.text()).toContain('出库')
    expect(inventoryForm.text()).not.toContain('INBOUND')
  })

  it('closes each operation independently without hiding the material list', async () => {
    const wrapper = mount(MaterialsView, { global })
    await flushPromises()

    const toolbar = wrapper.get('.material-toolbar')
    await toolbar.findAll('button').find(button => button.text() === '添加辅料')!.trigger('click')
    await wrapper.get('button[aria-label="关闭辅料操作"]').trigger('click')
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    expect(wrapper.find('.material-table').exists()).toBe(true)

    await toolbar.findAll('button').find(button => button.text() === '库存操作')!.trigger('click')
    await wrapper.get('button[aria-label="关闭库存操作"]').trigger('click')
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    expect(wrapper.find('.material-table').exists()).toBe(true)
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

  it('allows a disabled material to be enabled again', async () => {
    enableMaterial.mockResolvedValue({ id: 'm3', materialCode: 'MAT-3', name: '标签', materialType: '耗材', unit: '件', status: 'ACTIVE' })
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const wrapper = mount(MaterialsView, { global })
    await flushPromises()

    const row = wrapper.findAll('tbody tr').find(item => item.text().includes('MAT-3'))!
    const enableButton = row.findAll('button').find(button => button.text() === '启用')!
    expect(enableButton.attributes('disabled')).toBeUndefined()
    await enableButton.trigger('click')
    await flushPromises()

    expect(enableMaterial).toHaveBeenCalledWith('m3')
    expect(row.text()).toContain('启用')
    expect(row.findAll('button').some(button => button.text() === '停用')).toBe(true)
  })
})
