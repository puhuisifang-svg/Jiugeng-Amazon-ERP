import type { WatchEventDto } from '@/api/resources'
export function noveltyStatus(event: WatchEventDto) { return event.noveltyOverride || event.noveltyStatus || 'PENDING' }
export function isNovel(event: WatchEventDto) { return ['CANDIDATE', 'SUSPECTED', 'CONFIRMED'].includes(noveltyStatus(event)) }
export function noveltyLabel(event: WatchEventDto) {
  const labels: Record<string, string> = { CANDIDATE: '新品候选', SUSPECTED: '疑似新品', PENDING: '待核实', OLD: '老链接', CONFIRMED: '已确认新品', EXCLUDED: '已排除' }
  return labels[noveltyStatus(event)] || '待核实'
}
