import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'

import AuthenticatedDashboardImage from './AuthenticatedDashboardImage.vue'

describe('AuthenticatedDashboardImage', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('renders an authenticated blob and revokes its object URL on unmount', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, blob: async () => new Blob(['image'], { type: 'image/png' }) }))
    const create = vi.fn(() => 'blob:protected-image'); const revoke = vi.fn()
    vi.stubGlobal('URL', { ...URL, createObjectURL: create, revokeObjectURL: revoke })
    const wrapper = mount(AuthenticatedDashboardImage, { props: { imageId: 'img-1', alt: '产品图案' } })
    await flushPromises()
    expect(wrapper.get('img').attributes('src')).toBe('blob:protected-image')
    wrapper.unmount()
    expect(revoke).toHaveBeenCalledWith('blob:protected-image')
  })

  it('uses a non-blocking placeholder when no image is present', () => {
    const wrapper = mount(AuthenticatedDashboardImage, { props: { imageId: undefined, alt: '产品图案' } })
    expect(wrapper.get('[role="img"]').attributes('aria-label')).toContain('无可用图片')
  })
})
