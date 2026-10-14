import type { SelectionCandidateDto } from '@/api/resources'

export type ExportCandidate = SelectionCandidateDto & { rejectionReason?: string }

export const candidateCsvHeaders = [
  'ASIN',
  '标题',
  '状态',
  '评分',
  '类目',
  '价格',
  '星级',
  '评论数',
  '负责人',
  '标签',
  '选品理由',
  '来源店铺',
  '发现时间',
  '淘汰原因',
] as const

function protectSpreadsheetFormula(value: string): string {
  return /^\s*[=+\-@]/.test(value) ? `'${value}` : value
}

export function escapeCsvValue(value: string | number | undefined): string {
  if (value === undefined) return ''
  const text = typeof value === 'string' ? protectSpreadsheetFormula(value) : String(value)
  return /[",\r\n]/.test(text) ? `"${text.replaceAll('"', '""')}"` : text
}

function candidateRow(candidate: ExportCandidate): Array<string | number | undefined> {
  return [
    candidate.asin,
    candidate.title,
    candidate.status,
    candidate.score,
    candidate.category,
    candidate.price,
    candidate.rating,
    candidate.reviewCount,
    candidate.owner,
    candidate.tags?.join('、'),
    candidate.selectionReason,
    candidate.sourceStore,
    candidate.discoveredAt,
    candidate.rejectionReason,
  ]
}

export function buildCandidatesCsv(candidates: ExportCandidate[]): string {
  const rows = [candidateCsvHeaders, ...candidates.map(candidateRow)]
  return `\uFEFF${rows.map(row => row.map(escapeCsvValue).join(',')).join('\r\n')}`
}

export function downloadCandidatesCsv(candidates: ExportCandidate[], filename = 'selection-candidates.csv'): void {
  const url = URL.createObjectURL(new Blob([buildCandidatesCsv(candidates)], { type: 'text/csv;charset=utf-8' }))
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.click()
  URL.revokeObjectURL(url)
}
