import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import TableViewport from './TableViewport.vue'

afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals() })

describe('TableViewport', () => {
  it('uses the content boundary without reserving a second pagination bar when docked', async () => {
    const host = document.createElement('div')
    host.className = 'erp-shell'
    host.innerHTML = '<main class="erp-content"></main><div id="workspace-pagination"><nav class="list-pagination"></nav></div>'
    document.body.append(host)
    vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockReturnValue({ top: 240, bottom: 751 } as DOMRect)
    vi.stubGlobal('innerHeight', 800)
    const wrapper = mount(TableViewport, { attachTo: host.querySelector('main')! })
    await nextTick()
    expect(wrapper.attributes('style')).toContain('495px')
    wrapper.unmount(); host.remove()
  })
  it('retains a semantic table in a keyboard-accessible scroll region', () => {
    const wrapper = mount(TableViewport, { slots: { default: '<table><thead><tr><th>数量</th></tr></thead><tbody><tr><td>12</td></tr></tbody></table>' } })
    expect(wrapper.attributes('tabindex')).toBe('0')
    expect(wrapper.attributes('role')).toBe('region')
    expect(wrapper.get('thead th').text()).toBe('数量')
    expect(wrapper.get('tbody td').text()).toBe('12')
    wrapper.unmount()
  })

  it('fits remaining viewport space and releases resize observers', async () => {
    const disconnect = vi.fn()
    const observe = vi.fn()
    vi.stubGlobal('ResizeObserver', class { observe = observe; disconnect = disconnect })
    vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockReturnValue({ top: 240, bottom: 900 } as DOMRect)
    vi.stubGlobal('innerHeight', 900)
    const wrapper = mount(TableViewport)
    await nextTick()
    expect(wrapper.attributes('style')).toContain('588px')
    expect(observe).toHaveBeenCalled()
    wrapper.unmount()
    expect(disconnect).toHaveBeenCalledOnce()
  })

  it('keeps a usable minimum height on a short screen', async () => {
    vi.stubGlobal('innerHeight', 200)
    const wrapper = mount(TableViewport)
    await nextTick()
    expect(wrapper.attributes('style')).toContain('180px')
    wrapper.unmount()
  })
})
