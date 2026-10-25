export interface NameStyleSpan { start: number; end: number; color: 'BLACK' | 'RED'; bold: boolean }
type Style = Pick<NameStyleSpan, 'color' | 'bold'>
const base = (highlighted = false): Style => ({ color: highlighted ? 'RED' : 'BLACK', bold: true })

function characters(text: string, spans?: NameStyleSpan[] | null, highlighted = false): Style[] {
  const styles = Array.from({ length: text.length }, () => base(highlighted))
  for (const span of spans ?? []) {
    if (!Number.isInteger(span.start) || !Number.isInteger(span.end) || span.start < 0 || span.end > text.length || span.start >= span.end) continue
    for (let i = span.start; i < span.end; i++) styles[i] = { color: span.color === 'RED' ? 'RED' : 'BLACK', bold: span.bold === true }
  }
  return styles
}
function compact(styles: Style[]): NameStyleSpan[] {
  const result: NameStyleSpan[] = []
  styles.forEach((style, i) => {
    const previous = result.at(-1)
    if (previous && previous.color === style.color && previous.bold === style.bold) previous.end = i + 1
    else result.push({ start: i, end: i + 1, ...style })
  })
  return result
}
export function nameRuns(text: string, spans?: NameStyleSpan[] | null, highlighted = false) {
  return compact(characters(text, spans, highlighted)).map(span => ({ ...span, text: text.slice(span.start, span.end) }))
}
export function formatName(text: string, spans: NameStyleSpan[] | null | undefined, highlighted: boolean, start: number, end: number, patch: Partial<Style>) {
  const styles = characters(text, spans, highlighted)
  for (let i = Math.max(0, start); i < Math.min(text.length, end); i++) styles[i] = { ...styles[i]!, ...patch }
  return compact(styles)
}
/** Keep unaffected formatting when typing, replacing, pasting or deleting plain text. */
export function editName(before: string, after: string, spans?: NameStyleSpan[] | null, highlighted = false, selection?: { start: number; end: number }) {
  let start = 0; let oldEnd = before.length; let newEnd = after.length
  const insertedLength = selection ? after.length - before.length + selection.end - selection.start : -1
  if (selection && insertedLength >= 0 && after.slice(0, selection.start) === before.slice(0, selection.start) && after.slice(selection.start + insertedLength) === before.slice(selection.end)) {
    start = selection.start; oldEnd = selection.end; newEnd = start + insertedLength
  } else {
    while (start < oldEnd && start < newEnd && before[start] === after[start]) start++
    while (oldEnd > start && newEnd > start && before[oldEnd - 1] === after[newEnd - 1]) { oldEnd--; newEnd-- }
  }
  const old = characters(before, spans, highlighted)
  const inherited = old[start] ?? old[start - 1] ?? base(highlighted)
  return compact([...old.slice(0, start), ...Array.from({ length: newEnd - start }, () => ({ ...inherited })), ...old.slice(oldEnd)])
}
