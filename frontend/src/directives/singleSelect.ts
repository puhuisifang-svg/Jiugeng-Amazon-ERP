import type { ObjectDirective } from 'vue'
import '@/styles/singleSelect.css'

// Keep the native select as the source of truth so existing v-model (including
// numeric/boolean values), validation and change handlers remain intact.
const instances = new WeakMap<HTMLSelectElement, { sync: () => void; destroy: () => void }>()
let sequence = 0
export const vSingleSelect: ObjectDirective<HTMLSelectElement> = {
  mounted(select) {
    if (select.multiple || select.size > 1) return
    const trigger = document.createElement('button'), panel = document.createElement('div')
    const text = document.createElement('span'), arrow = document.createElement('span')
    trigger.type = 'button'; trigger.className = 'single-select-trigger'
    text.className = 'single-select-text'; arrow.className = 'single-select-arrow'; arrow.setAttribute('aria-hidden', 'true')
    trigger.append(text, arrow)
    panel.id = `single-select-${++sequence}`; panel.className = 'single-select-panel'; panel.setAttribute('popover', 'manual'); panel.setAttribute('role', 'listbox')
    trigger.setAttribute('role', 'combobox'); trigger.setAttribute('aria-haspopup', 'listbox'); trigger.setAttribute('aria-controls', panel.id)
    select.after(trigger, panel)
    const originalTabIndex = select.getAttribute('tabindex'), originalHidden = select.getAttribute('aria-hidden')
    select.classList.add('single-select-native'); select.tabIndex = -1; select.setAttribute('aria-hidden', 'true')
    let open = false, active = -1, search = '', searchAt = 0
    const options = () => Array.from(select.options)
    const enabled = () => options().map((o, i) => ({ o, i })).filter(({ o }) => !o.disabled && !o.hidden && !(o.parentElement instanceof HTMLOptGroupElement && o.parentElement.disabled))
    function place() {
      if (!open) return
      const r = trigger.getBoundingClientRect()
      panel.style.width = `${Math.min(Math.max(r.width, 120), innerWidth - 16)}px`
      panel.style.maxHeight = `${Math.min(256, innerHeight - 16)}px`
      const height = panel.offsetHeight
      panel.style.left = `${Math.max(8, Math.min(r.left, innerWidth - panel.offsetWidth - 8))}px`
      panel.style.top = `${Math.max(8, r.bottom + height + 4 <= innerHeight - 8 ? r.bottom + 4 : r.top - height - 4)}px`
    }
    function close(focus = false) {
      open = false; panel.hidePopover?.(); panel.hidden = true; trigger.setAttribute('aria-expanded', 'false'); trigger.removeAttribute('aria-activedescendant')
      if (focus) trigger.focus()
    }
    function highlight() {
      panel.querySelectorAll<HTMLElement>('[role=option]').forEach(o => o.classList.toggle('is-active', Number(o.dataset.index) === active))
      if (active >= 0) { trigger.setAttribute('aria-activedescendant', `${panel.id}-${active}`); panel.querySelector<HTMLElement>(`[data-index="${active}"]`)?.scrollIntoView?.({ block: 'nearest' }) }
    }
    function sync() {
      text.textContent = select.options[select.selectedIndex]?.textContent || select.getAttribute('placeholder') || '请选择'
      trigger.disabled = select.disabled || select.matches(':disabled')
      trigger.setAttribute('aria-expanded', String(open))
      const label = select.getAttribute('aria-label') || Array.from(select.labels || []).map(l => Array.from(l.childNodes).filter(n => n !== select && n !== trigger && n !== panel).map(n => n.textContent).join('').trim()).join(' ') || select.name || '请选择'
      trigger.setAttribute('aria-label', label); panel.setAttribute('aria-label', label)
      trigger.setAttribute('aria-required', String(select.required))
      panel.replaceChildren(...options().map((o, i) => {
        const item = document.createElement('div'); item.id = `${panel.id}-${i}`; item.dataset.index = String(i); item.setAttribute('role', 'option'); item.setAttribute('aria-selected', String(o.selected)); item.setAttribute('aria-disabled', String(!enabled().some(e => e.i === i))); item.hidden = o.hidden; item.textContent = o.textContent; return item
      }))
      if (open) { if (trigger.disabled) close(); else { highlight(); place() } }
    }
    function show() { if (trigger.disabled) return; sync(); active = enabled().some(e => e.i === select.selectedIndex) ? select.selectedIndex : enabled()[0]?.i ?? -1; open = true; panel.hidden = false; panel.showPopover?.(); trigger.setAttribute('aria-expanded', 'true'); place(); highlight() }
    function choose(index: number) {
      if (!enabled().some(e => e.i === index)) return
      if (select.selectedIndex !== index) { select.selectedIndex = index; select.dispatchEvent(new Event('input', { bubbles: true })); select.dispatchEvent(new Event('change', { bubbles: true })) }
      close(true); sync()
    }
    function click() { if (open) close(); else show() }
    function pick(e: MouseEvent) { e.preventDefault(); const option = (e.target as HTMLElement).closest<HTMLElement>('[role=option]'); if (option) choose(Number(option.dataset.index)) }
    function outside(e: Event) { if (e.target !== trigger && !trigger.contains(e.target as Node) && !panel.contains(e.target as Node)) close() }
    function key(e: KeyboardEvent) {
      if (e.key === 'Tab') { close(); return }
      if (e.key === 'Escape') { if (open) { e.preventDefault(); e.stopPropagation(); close(true) }; return }
      if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); if (open) choose(active); else show(); return }
      if (['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(e.key)) {
        e.preventDefault(); if (!open) { show(); return }
        const available = enabled(), index = available.findIndex(o => o.i === active)
        const next = e.key === 'Home' ? 0 : e.key === 'End' ? available.length - 1 : (index + (e.key === 'ArrowDown' ? 1 : -1) + available.length) % available.length
        active = available[next]?.i ?? -1; highlight()
      } else if (e.key.length === 1 && !e.ctrlKey && !e.metaKey && !e.altKey) {
        if (!open) show(); search = Date.now() - searchAt > 700 ? e.key : search + e.key; searchAt = Date.now()
        const match = enabled().find(({ o }) => o.text.toLowerCase().startsWith(search.toLowerCase())); if (match) { active = match.i; highlight() }
      }
    }
    function focusNative() { trigger.focus() }
    function invalid(e: Event) { e.preventDefault(); trigger.focus(); show() }
    function reset() { queueMicrotask(sync) }
    const form = select.form
    trigger.addEventListener('click', click); trigger.addEventListener('keydown', key); panel.addEventListener('click', pick)
    // Prevent the surrounding label from activating the hidden native selector.
    panel.addEventListener('mousedown', e => e.preventDefault())
    select.addEventListener('focus', focusNative); select.addEventListener('change', sync); select.addEventListener('invalid', invalid)
    form?.addEventListener('reset', reset); document.addEventListener('pointerdown', outside, true); document.addEventListener('scroll', place, true); window.addEventListener('resize', place)
    const observer = new MutationObserver(sync); observer.observe(select, { childList: true, subtree: true, characterData: true, attributes: true, attributeFilter: ['disabled', 'label', 'hidden', 'required', 'selected', 'aria-label'] })
    close(); sync(); queueMicrotask(() => instances.get(select)?.sync())
    instances.set(select, { sync, destroy() {
      close(); observer.disconnect(); trigger.remove(); panel.remove(); select.classList.remove('single-select-native')
      if (originalTabIndex === null) select.removeAttribute('tabindex'); else select.setAttribute('tabindex', originalTabIndex)
      if (originalHidden === null) select.removeAttribute('aria-hidden'); else select.setAttribute('aria-hidden', originalHidden)
      select.removeEventListener('focus', focusNative); select.removeEventListener('change', sync); select.removeEventListener('invalid', invalid); form?.removeEventListener('reset', reset)
      document.removeEventListener('pointerdown', outside, true); document.removeEventListener('scroll', place, true); window.removeEventListener('resize', place)
    } })
  },
  updated(select) { queueMicrotask(() => instances.get(select)?.sync()) },
  beforeUnmount(select) { instances.get(select)?.destroy(); instances.delete(select) },
}
