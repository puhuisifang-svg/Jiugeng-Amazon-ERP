import type { InventoryTransactionType } from '@/api/domain'

export const inventoryTransactionTypeLabels: Record<InventoryTransactionType, string> = {
  OPENING: '期初入库',
  INBOUND: '入库',
  OUTBOUND: '出库',
  ADJUST_IN: '盘盈调整',
  ADJUST_OUT: '盘亏调整',
  REVERSAL: '冲销',
  ADJUSTMENT: '库存调整',
}

export function inventoryTransactionTypeLabel(type: InventoryTransactionType) {
  return inventoryTransactionTypeLabels[type]
}

export function isPositiveInventoryInteger(value: number) {
  return Number.isInteger(value) && value > 0
}

export function isNonNegativeInventoryInteger(value: number | undefined): value is number {
  return typeof value === 'number' && Number.isInteger(value) && value >= 0
}
