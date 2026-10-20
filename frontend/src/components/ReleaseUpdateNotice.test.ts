import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, expect, it, vi } from 'vitest'
import ReleaseUpdateNotice from './ReleaseUpdateNotice.vue'
afterEach(() => { document.head.querySelector('#test-entry')?.remove(); vi.unstubAllEnvs(); vi.unstubAllGlobals(); vi.restoreAllMocks() })
it('warns for a new release without automatically discarding user work', async () => {
  vi.stubEnv('PROD', true)
  const script = document.createElement('script'); script.id = 'test-entry'; script.type = 'module'; script.src = '/assets/old.js'; document.head.append(script)
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('<script type="module" src="/assets/new.js"></script>', { headers: { 'content-type': 'text/html' } })))
  const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false)
  const wrapper = mount(ReleaseUpdateNotice); await flushPromises()
  expect(wrapper.get('[role="status"]').text()).toContain('请先保存当前内容')
  expect(confirm).not.toHaveBeenCalled()
  await wrapper.get('button').trigger('click')
  expect(confirm).toHaveBeenCalledOnce()
  wrapper.unmount()
})
it('shows a controlled notice for stale dynamic chunks instead of automatic reload', async () => {
  vi.stubEnv('PROD', true)
  const wrapper = mount(ReleaseUpdateNotice)
  const event = new Event('vite:preloadError', { cancelable: true })
  window.dispatchEvent(event); await flushPromises()
  expect(event.defaultPrevented).toBe(true)
  expect(wrapper.get('[role="status"]').text()).toContain('页面资源已失效')
  wrapper.unmount()
})
