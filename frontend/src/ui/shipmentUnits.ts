export function shipmentUnit(unit?: string) { return !unit || unit === 'SET' ? '套' : unit }
export function shipmentQuantitySummary(items: Array<{ quantity: number; quantityUnit?: string }>) {
  const groups = new Map<string, number>()
  items.forEach(item => { const unit = shipmentUnit(item.quantityUnit); groups.set(unit, (groups.get(unit) ?? 0) + item.quantity) })
  return [...groups].map(([unit, quantity]) => `${Number(quantity.toFixed(3))} ${unit}`).join(' / ')
}
