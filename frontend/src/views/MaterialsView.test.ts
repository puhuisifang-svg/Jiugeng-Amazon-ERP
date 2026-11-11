import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/api/client'

const { deleteMaterials } = vi.hoisted(() => ({ deleteMaterials: vi.fn() }))

const { createMaterial, disableMaterial, enableMaterial, materialSupplierLinks, replaceMaterialSupplierLinks, supplier, transact, updateSafetyStock, uploadImage } = vi.hoisted(() => ({
  createMaterial: vi.fn(), disableMaterial: vi.fn(), enableMaterial: vi.fn(), materialSupplierLinks: vi.fn(), replaceMaterialSupplierLinks: vi.fn(), supplier: vi.fn(), transact: vi.fn(), updateSafetyStock: vi.fn(), uploadImage: vi.fn(),
}))

vi.mock('@/config/runtime', () => ({ runtimeConfig: { authMode: 'password' } }))
vi.mock('@/state/session', () => ({ session: { canAccess: () => true } }))
vi.mock('@/api/costs', () => ({ costsApi: { prices: vi.fn(async () => [{ materialId: 'm1', netPrice: '2.5', unit: 'PCS', version: 0 }]), price: vi.fn(async () => null) } }))
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
    updateMaterial: vi.fn(),
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

  it('enables batch purchasing only after selecting active materials', async () => {
    const wrapper=mount(MaterialsView,{global});await flushPromises()
    const button=wrapper.findAll('.material-toolbar button').find(b=>b.text()==='批量采购')!
    expect(button.attributes('disabled')).toBeDefined()
    await wrapper.get('[aria-label="选择辅料 MAT-1"]').setValue(true)
    expect(button.attributes('disabled')).toBeUndefined()
    await wrapper.get('[aria-label="选择辅料 MAT-3"]').setValue(true)
    expect(button.attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })
  it('saves decimal cost directly with the material and retries supplier failure without creating twice', async () => {
    createMaterial.mockResolvedValue({ item: { id: 'new-m', materialCode: 'GENERATED', name: '新辅料', materialType: '新类型', unit: '件', status: 'ACTIVE' }, mockData: false })
    vi.mocked(domainApi.updateMaterial).mockResolvedValue({ id: 'new-m', materialCode: 'GENERATED', name: '新辅料', materialType: '新类型', unit: '件', status: 'ACTIVE' })
    replaceMaterialSupplierLinks.mockRejectedValueOnce(new Error('network')).mockResolvedValue([])
    const wrapper = mount(MaterialsView, { global }); await flushPromises()
    expect(wrapper.get('.material-table').text()).toContain('¥ 2.50 / PCS')
    await wrapper.get('.material-toolbar button').trigger('click')
    const form = wrapper.get('.operation-form')
    await form.get('input[maxlength="200"]').setValue('新辅料')
    await form.get('[list="material-type-history"]').setValue('新类型')
    expect(form.findAll('datalist option').map(o => o.attributes('value'))).toContain('包装')
    await form.get('[aria-label="辅料成本价"]').setValue('invalid')
    await form.trigger('submit'); await flushPromises()
    expect(createMaterial).not.toHaveBeenCalled()
    expect(form.get('[role="alert"]').text()).toContain('例如 0.5')
    await form.get('[aria-label="辅料成本价"]').setValue('0.5')
    await form.trigger('submit'); await flushPromises()
    expect(createMaterial).toHaveBeenCalledWith(expect.objectContaining({ materialCode: '', costPrice: expect.objectContaining({ netPrice: '0.5', confirmedNet: true, version: -1 }) }))
    await form.trigger('submit'); await flushPromises()
    expect(createMaterial).toHaveBeenCalledTimes(1)
    expect(domainApi.updateMaterial).toHaveBeenLastCalledWith('new-m', expect.not.objectContaining({ costPrice: expect.anything() }))
    wrapper.unmount()
  })

  it.each(['url', 'asset'])('removes a %s image on save and preserves it when cancelling', async kind => {
    const material = { id: 'm-image', materialCode: 'IMG', name: '图片辅料', materialType: '包装', unit: '件', status: 'ACTIVE' as const, ...(kind === 'url' ? { imageUrl: 'https://images.example.com/box.jpg' } : { imageAssetId: 'asset-test' }) }
    vi.mocked(domainApi.materials).mockResolvedValueOnce({ items: [material], mockData: false })
    vi.mocked(domainApi.updateMaterial).mockResolvedValue({ id: material.id, materialCode: material.materialCode, name: material.name, materialType: material.materialType, unit: material.unit, status: material.status })
    const wrapper = mount(MaterialsView, { global }); await flushPromises()
    await wrapper.findAll('tbody button').find(b => b.text() === '编辑')!.trigger('click'); await flushPromises()
    await wrapper.get('.ui-image-remove').trigger('click')
    expect(wrapper.find('.ui-image-remove').exists()).toBe(false)
    await wrapper.findAll('button').find(b => b.text() === '取消')!.trigger('click')
    await wrapper.findAll('tbody button').find(b => b.text() === '编辑')!.trigger('click'); await flushPromises()
    expect(wrapper.find('.ui-image-remove').exists()).toBe(true)
    await wrapper.get('.ui-image-remove').trigger('click')
    await wrapper.get('.material-image-editor').element.closest('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); await flushPromises()
    expect(domainApi.updateMaterial).toHaveBeenLastCalledWith('m-image', expect.not.objectContaining({ imageUrl: expect.any(String) }))
    const input = vi.mocked(domainApi.updateMaterial).mock.calls.at(-1)![1]
    expect(input.imageAssetId).toBeUndefined(); expect(input.imageUrl).toBeUndefined()
    wrapper.unmount()
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
    await wrapper.get('[aria-label="下一页"]').trigger('click')
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
    expect(wrapper.text()).toContain('胶带')
    await dialog.get('.danger-button').trigger('click'); await flushPromises()
    expect(deleteMaterials).toHaveBeenLastCalledWith(['m2'])
    expect(wrapper.get('.material-table').text()).not.toContain('胶带')
    expect(wrapper.get('.material-toolbar').text()).not.toContain('删除')
  })

  it('explains insufficient stock inside the inventory dialog without submitting', async () => {
    const wrapper = mount(MaterialsView, { global })
    await flushPromises()
    const row = wrapper.findAll('tbody tr').find(item => item.text().includes('纸箱'))!
    await row.findAll('button').find(button => button.text() === '库存')!.trigger('click')
    const dialog = wrapper.get('[aria-labelledby="inventory-dialog-title"]')
    await dialog.findAll('select')[1]!.setValue('OUTBOUND')
    await dialog.get('input[type="number"]').setValue(5)
    await dialog.get('form').trigger('submit'); await flushPromises()
    expect(dialog.get('[role="alert"]').text()).toContain('当前可用 2 PCS')
    expect(dialog.get('[role="alert"]').text()).toContain('本次需要 5 PCS')
    expect(transact).not.toHaveBeenCalled()
  })

  it('only accepts whole numbers for inventory quantity and safety stock', async () => {
    const wrapper = mount(MaterialsView, { global })
    await flushPromises()

    const row = wrapper.findAll('tbody tr').find(item => item.text().includes('纸箱'))!
    await row.findAll('button').find(button => button.text() === '库存')!.trigger('click')
    const dialog = wrapper.get('[aria-labelledby="inventory-dialog-title"]')
    const quantity = dialog.get('input[type="number"]')
    expect(quantity.attributes('min')).toBe('1')
    expect(quantity.attributes('step')).toBe('1')
    await quantity.setValue('1.5')
    await dialog.get('form').trigger('submit')
    expect(dialog.get('[role="alert"]').text()).toContain('数量必须是大于 0 的整数')
    expect(transact).not.toHaveBeenCalled()

    await dialog.get('button[aria-label="关闭库存操作"]').trigger('click')
    const safety = wrapper.get('input[aria-label="MAT-1安全库存"]')
    expect(safety.attributes('min')).toBe('0')
    expect(safety.attributes('step')).toBe('1')
    await safety.setValue('2.5')
    await row.findAll('button').find(button => button.text() === '保存')!.trigger('click')
    expect(wrapper.get('[role="alert"]').text()).toContain('安全库存必须是大于或等于 0 的整数')
    expect(updateSafetyStock).not.toHaveBeenCalled()
  })

  it('shows master data and inventory in one filterable table', async () => {
    const wrapper = mount(MaterialsView, { global })
    await flushPromises()

    expect(wrapper.text()).toContain('纸箱')
    expect(wrapper.text()).toContain('低库存')
    expect(wrapper.text()).toContain('未初始化')
    expect(wrapper.get('a[href="/inventory/ledger"]').text()).toContain('库存流水')

    await wrapper.get('input[placeholder="辅料名称或规格"]').setValue('胶带')
    expect(wrapper.text()).not.toContain('纸箱')
    expect(wrapper.text()).toContain('胶带')
  })

  it('opens an inventory command from a material row and updates the merged balance', async () => {
    transact.mockResolvedValue({ item: { id: 't1' }, balance: { warehouseCode: 'MAIN', materialId: 'm2',
      materialCode: 'MAT-2', materialName: '胶带', quantity: 5, safetyStock: 0, unit: 'ROLL', lowStock: false }, replayed: false })
    const wrapper = mount(MaterialsView, { global })
    await flushPromises()

    const row = wrapper.findAll('tbody tr').find(item => item.text().includes('胶带'))!
    await row.findAll('button').find(button => button.text() === '库存')!.trigger('click')
    const form = wrapper.findAll('form').find(item => item.text().includes('提交正式库存命令'))!
    expect((form.get('select').element as HTMLSelectElement).value).toBe('m2')
    await form.get('input[type="number"]').setValue('5')
    await form.trigger('submit')
    await flushPromises()

    expect(transact).toHaveBeenCalledWith(expect.objectContaining({ materialId: 'm2', type: 'INBOUND', quantity: 5, unit: 'ROLL' }))
    expect(wrapper.findAll('tbody tr').find(item => item.text().includes('胶带'))!.text()).toContain('5')
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

  it('links purchasing to a prefilled purchase form and deletes only the requested row', async () => {
    const wrapper = mount(MaterialsView, { global }); await flushPromises()
    const row = wrapper.findAll('tbody tr').find(item => item.text().includes('纸箱'))!
    expect(row.get('a[href="/purchases/orders?type=MATERIAL&materialId=m1"]').text()).toBe('采购')
    await wrapper.get('[aria-label="选择辅料 MAT-2"]').setValue(true)
    await row.findAll('button').find(button => button.text() === '删除')!.trigger('click')
    expect(deleteMaterials).not.toHaveBeenCalled()
    const dialog=wrapper.get('[aria-labelledby="delete-material-title"]')
    expect(dialog.text()).toContain('纸箱')
    await dialog.get('.danger-button').trigger('click'); await flushPromises()
    expect(deleteMaterials).toHaveBeenCalledWith(['m1'])
    expect(wrapper.get('.material-table').text()).toContain('胶带')
    wrapper.unmount()
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
    await textInputs[0]!.setValue('标签')
    await form.get('[list="material-type-history"]').setValue('新类型')
    const file = new File(['image'], 'material.png', { type: 'image/png' })
    Object.defineProperty(form.get('input[type="file"]').element, 'files', { value: [file] })
    await form.get('input[type="file"]').trigger('change')
    await flushPromises()
    await form.get('select[aria-label="添加辅料供应商"]').setValue('s1')
    await form.trigger('submit')
    await flushPromises()

    expect(form.find('input[placeholder="辅料编码"]').exists()).toBe(false)
    expect(createMaterial).toHaveBeenCalledWith(expect.objectContaining({ materialCode: '', materialType: '新类型' }))
    expect(uploadImage).toHaveBeenCalledWith('MATERIAL_IMAGE', file)
    expect(createMaterial).toHaveBeenCalledWith(expect.objectContaining({ unit: '件', imageAssetId: 'image-1' }))
    expect(replaceMaterialSupplierLinks).toHaveBeenCalledWith('m3', [expect.objectContaining({ supplierId: 's1', primary: true })])
  })

  it('allows a disabled material to be enabled again', async () => {
    enableMaterial.mockResolvedValue({ id: 'm3', materialCode: 'MAT-3', name: '标签', materialType: '耗材', unit: '件', status: 'ACTIVE' })
    const { settleConfirmation } = await import('@/state/uiConfirmation')
    const wrapper = mount(MaterialsView, { global })
    await flushPromises()

    const row = wrapper.findAll('tbody tr').find(item => item.text().includes('标签'))!
    const enableButton = row.findAll('button').find(button => button.text() === '启用')!
    expect(enableButton.attributes('disabled')).toBeUndefined()
    await enableButton.trigger('click')
    settleConfirmation('')
    await flushPromises()

    expect(enableMaterial).toHaveBeenCalledWith('m3')
    expect(row.text()).toContain('启用')
    expect(row.findAll('button').some(button => button.text() === '停用')).toBe(true)
  })
})
