/** Keep keyboard navigation inside the most recently opened modal, including Safari buttons. */
export function installModalKeyboard(root: Document = document): () => void {
  const selector = 'dialog[open], [role="dialog"][aria-modal="true"]'
  let stack: HTMLElement[] = []
  let highlighted: HTMLElement | null = null
  const clearHighlight = () => {
    highlighted?.removeAttribute('data-modal-keyboard-focus')
    highlighted = null
  }
  const focusControl = (element: HTMLElement) => {
    clearHighlight()
    element.focus()
    highlighted = root.activeElement instanceof HTMLElement ? root.activeElement : null
    highlighted?.setAttribute('data-modal-keyboard-focus', '')
  }
  const visible = (element: HTMLElement) => {
    if (element.closest('[hidden], [inert], [aria-hidden="true"], dialog:not([open])')) return false
    const style = getComputedStyle(element)
    return style.visibility !== 'hidden' && style.visibility !== 'collapse' && element.getClientRects().length > 0
  }
  const refresh = () => {
    const current = Array.from(root.querySelectorAll<HTMLElement>(selector)).filter(visible)
    stack = stack.filter(element => current.includes(element))
    for (const element of current) if (!stack.includes(element)) stack.push(element)
  }
  const observer = new MutationObserver(refresh)
  observer.observe(root.documentElement, { childList: true, subtree: true, attributes: true, attributeFilter: ['open', 'hidden', 'class', 'style', 'aria-modal', 'aria-hidden'] })
  refresh()
  const keydown = (event: KeyboardEvent) => {
    if (event.key !== 'Tab' || event.altKey || event.ctrlKey || event.metaKey || event.defaultPrevented) return
    refresh()
    const modal = stack.at(-1)
    if (!modal) return
    const controls = Array.from(modal.querySelectorAll<HTMLElement>('a[href], button, input, select, textarea, summary, [tabindex], [contenteditable="true"]'))
      .filter(element => element.tabIndex >= 0 && !element.matches(':disabled, input[type="hidden"]') && visible(element))
      .filter((element, _, all) => {
        if (!(element instanceof HTMLInputElement) || element.type !== 'radio' || !element.name) return true
        const group = all.filter((other): other is HTMLInputElement => other instanceof HTMLInputElement && other.type === 'radio' && other.name === element.name && other.form === element.form)
        return element === (group.find(other => other.checked) ?? group[0])
      })
      .sort((a, b) => (a.tabIndex || Infinity) - (b.tabIndex || Infinity))
    event.preventDefault()
    if (!controls.length) {
      if (!modal.hasAttribute('tabindex')) modal.tabIndex = -1
      focusControl(modal)
      return
    }
    const index = controls.indexOf(root.activeElement as HTMLElement)
    const next = index < 0 ? (event.shiftKey ? controls.length - 1 : 0) : (index + (event.shiftKey ? -1 : 1) + controls.length) % controls.length
    if (controls[next]) focusControl(controls[next])
  }
  root.addEventListener('keydown', keydown)
  root.addEventListener('pointerdown', clearHighlight, true)
  root.addEventListener('focusout', clearHighlight, true)
  return () => { observer.disconnect(); root.removeEventListener('keydown', keydown); root.removeEventListener('pointerdown', clearHighlight, true); root.removeEventListener('focusout', clearHighlight, true); clearHighlight(); stack = [] }
}
