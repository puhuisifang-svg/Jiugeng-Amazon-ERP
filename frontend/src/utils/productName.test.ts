import { describe, expect, it } from 'vitest'
import { editName, formatName, nameRuns, type NameStyleSpan } from './productName'
describe('SKU name formatting', () => {
  const text = '黑色-沙发网-大号\n610cm*40.5cm'
  it('colors only the selected second line while retaining black bold first line', () => {
    const start = text.indexOf('\n') + 1
    const spans = formatName(text, undefined, false, start, text.length, { color: 'RED' })
    expect(nameRuns(text, spans)).toEqual([
      { start: 0, end: start, text: text.slice(0, start), color: 'BLACK', bold: true },
      { start, end: text.length, text: '610cm*40.5cm', color: 'RED', bold: true },
    ])
    expect(nameRuns(text, formatName(text, spans, false, 0, start, { bold: false }))[0]?.bold).toBe(false)
  })
  it('supports legacy all-red names and plain names', () => {
    expect(nameRuns('旧名称', null, true)[0]).toMatchObject({ color: 'RED', bold: true })
    expect(nameRuns('旧名称')[0]).toMatchObject({ color: 'BLACK', bold: true })
  })
  it('moves offsets on insert/delete and retains selected-format inheritance on replacement', () => {
    const spans: NameStyleSpan[] = [{ start: 0, end: 2, color: 'BLACK', bold: false }, { start: 2, end: 4, color: 'RED', bold: true }]
    expect(nameRuns('前ABCD', editName('ABCD', '前ABCD', spans)).map(r => [r.text, r.color, r.bold])).toEqual([['前AB', 'BLACK', false], ['CD', 'RED', true]])
    expect(nameRuns('ACD', editName('ABCD', 'ACD', spans)).map(r => [r.text, r.color])).toEqual([['A', 'BLACK'], ['CD', 'RED']])
    expect(nameRuns('AB中文', editName('ABCD', 'AB中文', spans, false, { start: 2, end: 4 })).at(-1)).toMatchObject({ text: '中文', color: 'RED' })
    expect(editName('ABCD', '', spans)).toEqual([])
  })
  it('uses selection to distinguish replacing identical characters and safely renders emoji', () => {
    const spans = formatName('aaa', undefined, false, 1, 2, { color: 'RED' })
    expect(nameRuns('aa', editName('aaa', 'aa', spans, false, { start: 0, end: 1 }))[0]).toMatchObject({ text: 'a', color: 'RED' })
    const emoji = '名称😀\n规格'
    expect(nameRuns(emoji, formatName(emoji, undefined, false, 5, emoji.length, { color: 'RED' })).map(r => r.text).join('')).toBe(emoji)
  })
})
