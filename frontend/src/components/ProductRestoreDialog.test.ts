import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import ProductRestoreDialog from './ProductRestoreDialog.vue'
import { apiRequest, ApiError } from '@/api/client'
import { session } from '@/state/session'

vi.mock('@/api/client', async original => ({ ...await original<typeof import('@/api/client')>(), apiRequest: vi.fn() }))
vi.mock('@/state/session', () => ({ session: { canAccess: vi.fn(() => true) } }))
enableAutoUnmount(afterEach)
beforeEach(() => {
  vi.clearAllMocks()
  HTMLDialogElement.prototype.showModal = function () { this.open = true }
  HTMLDialogElement.prototype.close = function () { this.open = false }
  vi.mocked(session.canAccess).mockReturnValue(true)
  vi.mocked(apiRequest).mockReset().mockResolvedValue({ items: [{ kind: 'SKU', id: 'v1', productId: 'p1', name: '合成子体', parentName: '合成父体', version: 4, deletedAt: '2026-01-01' }], total: 1, page: 0 })
})
it('requires explicit confirmation and retains parent-conflict errors', async () => {
  const wrapper = mount(ProductRestoreDialog); await flushPromises()
  await wrapper.findAll('button').find(button => button.text() === '恢复')!.trigger('click')
  expect(apiRequest).toHaveBeenCalledTimes(1)
  vi.mocked(apiRequest).mockRejectedValueOnce(new ApiError(409, 'RESTORE_PARENT_FIRST', '请先恢复所属父体'))
  await wrapper.findAll('button').find(button => button.text() === '确认恢复')!.trigger('click'); await flushPromises()
  expect(apiRequest).toHaveBeenLastCalledWith('/v1/products/deleted/SKU/v1/restore', { method: 'POST', body: JSON.stringify({ version: 4 }) })
  expect(wrapper.get('[aria-label="确认恢复"]').text()).toContain('请先恢复所属父体')
  expect(wrapper.emitted('changed')).toBeUndefined()
})
it('does not offer restore without write permission', async () => {
  vi.mocked(session.canAccess).mockReturnValue(false)
  const wrapper = mount(ProductRestoreDialog); await flushPromises()
  expect(wrapper.text()).toContain('合成子体')
  expect(wrapper.findAll('button').some(button => button.text() === '恢复')).toBe(false)
})
