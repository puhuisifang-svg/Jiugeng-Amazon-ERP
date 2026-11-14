import { expect,it } from 'vitest'
import type { ProductVariantDto } from '@/api/domain'
import { sampleSpecification } from './sampleSpecification'
it('uses structured dimensions before legacy dimensions without inventing missing values',()=>{
 expect(sampleSpecification({itemLengthCm:30,itemHeightCm:4,itemDimensions:'legacy',packageDimensions:'20×10×5 cm',packageWeight:2,packageWeightUnit:'KG'} as ProductVariantDto)).toBe('产品：长 30 cm × 高 4 cm；包装：20×10×5 cm；包装重量：2 kg')
 expect(sampleSpecification({} as ProductVariantDto)).toBe('')
 expect(sampleSpecification({specification:'棉布',packageLengthCm:10,packageWidthCm:8,packageHeightCm:2,packageWeightGrams:100} as ProductVariantDto)).toBe('棉布；包装：长 10 cm × 宽 8 cm × 高 2 cm；包装重量：100 g')
})
