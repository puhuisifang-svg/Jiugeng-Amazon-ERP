/** Business display and wall-clock inputs always use Beijing time, independent of browser timezone. */
export const BUSINESS_TIME_ZONE = 'Asia/Shanghai'
type DateValue = string | number | Date | null | undefined
const formatter = new Intl.DateTimeFormat('en-CA', {
  timeZone: BUSINESS_TIME_ZONE, year: 'numeric', month: '2-digit', day: '2-digit',
  hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23',
})
function parsed(value: DateValue): Date | undefined {
  if (value == null || value === '') return undefined
  // Unzoned wall-clock fields are business time, never the browser's local timezone.
  const input = typeof value === 'string' && /^\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}(:\d{2}(\.\d+)?)?$/.test(value)
    ? value.replace(' ', 'T') + '+08:00' : value
  const date = new Date(input)
  return Number.isNaN(date.getTime()) ? undefined : date
}
export function beijingDateTime(value: DateValue): string {
  if (typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value)) return value
  const date = parsed(value)
  if (!date) return '—'
  const p = Object.fromEntries(formatter.formatToParts(date).map(part => [part.type, part.value]))
  return `${p.year}-${p.month}-${p.day} ${p.hour}:${p.minute}:${p.second}`
}
export function beijingDate(value: DateValue = new Date()): string { return beijingDateTime(value).slice(0, 10) }
export function beijingDateTimeInput(value: DateValue = new Date()): string {
  const text = beijingDateTime(value)
  return text === '—' ? '' : text.replace(' ', 'T')
}
export function beijingInputToIso(value: string): string { return parsed(value)?.toISOString() ?? '' }
