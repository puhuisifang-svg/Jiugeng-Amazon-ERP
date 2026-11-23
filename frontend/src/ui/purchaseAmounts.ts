/** Round payable amounts per line using decimal integer arithmetic. */
function decimal(value:number|string){const text=String(value);if(!/^\d+(\.\d+)?$/.test(text))return {n:0n,s:0};return {n:BigInt(text.replace('.','')),s:text.split('.')[1]?.length??0}}
export function purchaseLineAmount(line:{quantity?:number;orderedQuantity?:number;unitPrice:number;taxIncluded?:boolean;taxRate?:number}){
 const q=decimal(line.quantity??line.orderedQuantity??0),p=decimal(line.unitPrice),r=decimal(line.taxIncluded?(line.taxRate??1):0)
 const n=q.n*p.n*(100n*10n**BigInt(r.s)+r.n),scale=q.s+p.s+r.s+2
 const divisor=10n**BigInt(scale)
 return Number((n*100n+divisor/2n)/divisor)/100
}
export function purchasePrice(value:number){return Number(value).toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:4})}
