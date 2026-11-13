import type { ObjectDirective } from 'vue'
import { validChinaPhone, PHONE_MESSAGE, type PhoneKind } from '@/utils/chinaPhone'
const handlers = new WeakMap<HTMLInputElement, { kind: PhoneKind | false; validate: () => void }>()
export const vPhone: ObjectDirective<HTMLInputElement, PhoneKind | false | undefined> = {
  mounted(el, binding) {
    const state = { kind: binding.value ?? 'contact', validate: () => {
      el.setCustomValidity(state.kind && !validChinaPhone(el.value, state.kind) ? (state.kind === 'mobile' ? '请填写有效的中国大陆手机号（11 位）' : PHONE_MESSAGE) : '')
    } }
    handlers.set(el, state)
    if (state.kind) { el.type = 'tel'; el.inputMode = 'tel' }
    el.addEventListener('input', state.validate); el.addEventListener('blur', state.validate)
    state.validate()
  },
  updated(el, binding) { const state=handlers.get(el); if(state){state.kind=binding.value??'contact';state.validate()} },
  beforeUnmount(el) {const state=handlers.get(el);if(state){el.removeEventListener('input',state.validate);el.removeEventListener('blur',state.validate);el.setCustomValidity('');handlers.delete(el)}} ,
}
