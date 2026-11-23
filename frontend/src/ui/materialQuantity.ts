const measured=new Set(['米','厘米','毫米','千克','公斤','克','升','毫升','平方米','立方米','M','CM','MM','KG','G','L','ML','M2','M3'])
export function materialQuantityScale(unit:string){return measured.has(unit.trim().toUpperCase())?4:0}
export function validMaterialQuantity(value:string|number,unit:string,zero=false){
 const s=String(value).trim(),scale=materialQuantityScale(unit)
 return new RegExp(`^\\d{1,14}${scale?'(\\.\\d{1,4})?':''}$`).test(s)&&Number.isFinite(Number(s))&&(zero?Number(s)>=0:Number(s)>0)
}
/** Decimal multiplication without floating-point rounding before stock validation. */
export function multiplyQuantity(quantity:string,factor:string){
 if(!/^\d{1,14}(\.\d{1,4})?$/.test(quantity)||!/^\d{1,14}(\.\d{1,4})?$/.test(factor))return ''
 const digits=(v:string)=>({n:BigInt(v.replace('.','')),s:v.split('.')[1]?.length??0})
 const a=digits(quantity),b=digits(factor),scale=a.s+b.s
 const raw=(a.n*b.n).toString().padStart(scale+1,'0')
 return scale?`${raw.slice(0,-scale)}.${raw.slice(-scale)}`.replace(/0+$/,'').replace(/\.$/,''):raw
}
export function materialNumber(value:number|string|null|undefined){return value==null?'—':Number(value).toLocaleString('zh-CN',{maximumFractionDigits:6})}
