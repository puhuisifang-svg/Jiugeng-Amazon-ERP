import type { ProductVariantDto } from '@/api/domain'
export function sampleSpecification(sku: ProductVariantDto): string {
  const dimensions=(values:(number|undefined)[],legacy?:string)=>{
    const parts=values.map((value,index)=>value!=null&&Number.isFinite(value)&&value>0?`${['长','宽','高'][index]} ${value} cm`:'').filter(Boolean)
    return parts.length?parts.join(' × '):legacy?.trim()??''
  }
  const product=dimensions([sku.itemLengthCm,sku.itemWidthCm,sku.itemHeightCm],sku.itemDimensions)
  const packaging=dimensions([sku.packageLengthCm,sku.packageWidthCm,sku.packageHeightCm],sku.packageDimensions)
  const weight=sku.packageWeightGrams!=null&&sku.packageWeightGrams>0?`${sku.packageWeightGrams} g`:sku.packageWeight!=null&&sku.packageWeight>0&&sku.packageWeightUnit?`${sku.packageWeight} ${sku.packageWeightUnit.toLowerCase()}`:''
  return [sku.specification?.trim(),product?`产品：${product}`:'',packaging?`包装：${packaging}`:'',weight?`包装重量：${weight}`:''].filter(Boolean).join('；')
}
