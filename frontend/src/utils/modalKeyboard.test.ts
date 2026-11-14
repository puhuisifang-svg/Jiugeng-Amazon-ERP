import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { installModalKeyboard } from './modalKeyboard'

describe('modal keyboard navigation', () => {
  let cleanup: () => void
  const element = (id: string) => document.getElementById(id) as HTMLElement
  const tab = (shiftKey = false) => {
    const event = new KeyboardEvent('keydown', { key: 'Tab', shiftKey, bubbles: true, cancelable: true })
    document.activeElement?.dispatchEvent(event)
    return event
  }
  beforeEach(() => {
    vi.spyOn(HTMLElement.prototype, 'getClientRects').mockImplementation(function (this: HTMLElement) {
      return (this.closest('[hidden]') ? [] : [{}]) as unknown as DOMRectList
    })
    document.body.innerHTML = '<button id="outside">outside</button>'
    cleanup = installModalKeyboard()
  })
  afterEach(() => { cleanup(); document.body.innerHTML = ''; vi.restoreAllMocks() })
  for (const tag of ['dialog open', 'section role="dialog" aria-modal="true"']) {
    it(`includes footer buttons and cycles both ways in ${tag}`, () => {
      const close = tag.split(' ')[0]
      document.body.insertAdjacentHTML('beforeend', `<${tag}><input id="first"><input id="last"><button id="cancel">取消</button><button id="save">保存</button></${close}>`)
      element('last').focus()
      expect(tab().defaultPrevented).toBe(true)
      expect(document.activeElement).toBe(element('cancel'))
      tab(); expect(document.activeElement).toBe(element('save'))
      tab(); expect(document.activeElement).toBe(element('first'))
      tab(true); expect(document.activeElement).toBe(element('save'))
    })
  }
  it('skips disabled, hidden, negative tabindex and unchecked grouped radio controls', () => {
    document.body.insertAdjacentHTML('beforeend', '<dialog open><input id="first"><button disabled>disabled</button><div hidden><input></div><input tabindex="-1"><input type="radio" name="choice"><input id="checked" type="radio" name="choice" checked><button id="save">保存</button></dialog>')
    element('first').focus(); tab(); expect(document.activeElement).toBe(element('checked'))
    tab(); expect(document.activeElement).toBe(element('save'))
  })
  it('uses the newest modal even when inserted earlier in DOM, and returns to underlying modal', async () => {
    document.body.insertAdjacentHTML('beforeend', '<dialog open id="parent"><input id="first"><button id="save">保存</button></dialog>')
    await Promise.resolve()
    document.body.insertAdjacentHTML('afterbegin', '<section role="dialog" aria-modal="true" id="child"><button id="nested">确认</button></section>')
    element('nested').focus(); tab(); expect(document.activeElement).toBe(element('nested'))
    element('child').remove(); element('first').focus(); tab(); expect(document.activeElement).toBe(element('save'))
  })
  it('ignores closed dialogs and nonmodal popovers outside a modal', () => {
    document.body.insertAdjacentHTML('beforeend', '<dialog><input></dialog><div role="dialog"><input></div>')
    element('outside').focus(); expect(tab().defaultPrevented).toBe(false)
  })
  it('keeps focus in an empty modal and removes the listener on cleanup', () => {
    document.body.insertAdjacentHTML('beforeend', '<section id="empty" role="dialog" aria-modal="true"></section>')
    tab(); expect(document.activeElement).toBe(element('empty'))
    cleanup(); expect(tab().defaultPrevented).toBe(false)
  })
  it('does not override a control that already handled Tab', () => {
    document.body.insertAdjacentHTML('beforeend', '<dialog open><input id="first"><button>保存</button></dialog>')
    element('first').addEventListener('keydown', event => event.preventDefault())
    element('first').focus(); tab(); expect(document.activeElement).toBe(element('first'))
  })
  it('marks the actual Tab target visibly and clears the mark on mouse input or blur', () => {
    document.body.insertAdjacentHTML('beforeend', '<dialog open><input id="first"><button id="cancel">取消</button><button id="save">保存</button></dialog>')
    element('first').focus(); tab()
    expect(element('cancel').hasAttribute('data-modal-keyboard-focus')).toBe(true)
    tab()
    expect(element('cancel').hasAttribute('data-modal-keyboard-focus')).toBe(false)
    expect(element('save').hasAttribute('data-modal-keyboard-focus')).toBe(true)
    element('save').dispatchEvent(new Event('pointerdown', { bubbles: true }))
    expect(element('save').hasAttribute('data-modal-keyboard-focus')).toBe(false)
    tab(true); element('first').focus()
    expect(document.querySelector('[data-modal-keyboard-focus]')).toBeNull()
  })

})
