function scaled(value: string | number | null, digits: number, wholeDigits: number): bigint | null {
  const text = String(value ?? '').trim()
  if (!new RegExp(`^\\d{1,${wholeDigits}}(?:\\.\\d{1,${digits}})?$`).test(text)) return null
  const [whole = '0', fraction = ''] = text.split('.')
  return BigInt(whole) * 10n ** BigInt(digits) + BigInt(fraction.padEnd(digits, '0'))
}
function money(cents: bigint) { return `${cents / 100n}.${String(cents % 100n).padStart(2, '0')}` }
export function invoiceAmounts(quantity: string | number, price: string | number | null, rate: string | number) {
  const q = scaled(quantity, 3, 9), p = scaled(price, 6, 8), r = scaled(rate, 2, 3)
  if (q === null || q === 0n || p === null || r === null || r > 10000n) return null
  const net = (q * p + 5000000n) / 10000000n
  const tax = (net * r + 5000n) / 10000n
  return { net: money(net), tax: money(tax), total: money(net + tax) }
}
