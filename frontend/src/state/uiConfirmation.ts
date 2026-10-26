import { shallowRef } from 'vue'

export interface UiConfirmation { title: string; message: string; input?: boolean }
export const uiConfirmation = shallowRef<UiConfirmation>()
let resolvePending: ((value: string | null) => void) | undefined
function request(options: UiConfirmation): Promise<string | null> {
  // Do not replace a pending user choice or accidentally submit twice.
  if (resolvePending) return Promise.resolve(null)
  uiConfirmation.value = options
  return new Promise(resolve => { resolvePending = resolve })
}
export function settleConfirmation(value: string | null) {
  const resolve = resolvePending; resolvePending = undefined; uiConfirmation.value = undefined; resolve?.(value)
}
export async function confirmAction(message: string) { return (await request({ title: '请确认操作', message })) !== null }
export function promptReason(message: string) { return request({ title: '填写操作原因', message, input: true }) }
