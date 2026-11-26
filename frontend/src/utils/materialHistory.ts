import type { ProductHistoryDto } from '@/api/domain'
import { productHistoryChanges } from './productHistory'

export function materialHistoryChanges(event: ProductHistoryDto) {
  const initial = ['MATERIAL_CREATE', 'MATERIAL_IMPORT_CREATE'].includes(event.action)
  const hidden = new Set(['id', 'materialId', 'version', 'updatedAt', 'createdAt'])
  return productHistoryChanges(event)
    .filter(change => !hidden.has(change.key)
      && (event.action !== 'INVENTORY_SAFETY_STOCK_UPDATE' || change.key === 'safetyStock')
      && (!initial || (event.afterValue?.[change.key] != null && event.afterValue[change.key] !== '')))
    .map(change => ({ ...change, initial,
      before: change.key === 'imageAssetId' ? (event.beforeValue?.imageAssetId ? '已设置图片' : '未设置') : change.before,
      after: change.key === 'imageAssetId' ? (event.afterValue?.imageAssetId ? '已设置图片' : '未设置') : change.after,
    }))
}
