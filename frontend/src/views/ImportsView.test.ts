import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { list, upload } = vi.hoisted(() => ({ list: vi.fn(), upload: vi.fn() }))

vi.mock('@/config/runtime', () => ({ runtimeConfig: { authMode: 'password' } }))
vi.mock('@/state/session', () => ({ session: { canAccess: () => true } }))
vi.mock('@/api/imports', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/api/imports')>()
  return { ...original, importsApi: { ...original.importsApi, list, upload } }
})

import ImportsView from './ImportsView.vue'

describe('ImportsView', () => {
  beforeEach(() => {
    list.mockReset().mockResolvedValue([])
    upload.mockReset().mockResolvedValue({ batch: { id: 'b1' }, replayed: false })
  })

  it('only offers active types and uploads xlsx', async () => {
    const wrapper = mount(ImportsView, { global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } } })
    await flushPromises()
    expect(wrapper.find('select').text()).not.toContain('STORE_LISTINGS')
    const file = new File(['xlsx'], 'products.xlsx', { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    Object.defineProperty(wrapper.find('input[type="file"]').element, 'files', { value: [file] })
    await wrapper.find('input[type="file"]').trigger('change')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(upload).toHaveBeenCalledWith('PRODUCTS', file)
    expect(wrapper.text()).toContain('文件已进入校验队列')
  })

  it('shows the selected type template version', async () => {
    const wrapper = mount(ImportsView, { global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } } })
    await flushPromises()
    const version = wrapper.find('input[readonly]')
    expect((version.element as HTMLInputElement).value).toBe('2.0')
    await wrapper.find('select').setValue('VARIANTS')
    expect((version.element as HTMLInputElement).value).toBe('2.1')
    await wrapper.find('select').setValue('AUXILIARY_MATERIALS')
    expect((version.element as HTMLInputElement).value).toBe('1.0')
  })
})
