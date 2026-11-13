/** Purchase prices are net prices; round each payable line to cents before totaling. */
export function purchaseLineAmount(line: { quantity?: number; orderedQuantity?: number; unitPrice: number; taxIncluded?: boolean; taxRate?: number }) {
  const value = Number(line.quantity ?? line.orderedQuantity ?? 0) * Number(line.unitPrice || 0) * (line.taxIncluded ? 1 + Number(line.taxRate ?? 1) / 100 : 1)
  return Math.round((value + Number.EPSILON) * 100) / 100
}
