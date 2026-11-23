import { describe,it,expect } from 'vitest'
import { multiplyQuantity,validMaterialQuantity } from './materialQuantity'
describe('material base quantities',()=>{
 it('keeps measured decimals but rejects fractional counts',()=>{expect(validMaterialQuantity('0.125','千克')).toBe(true);expect(validMaterialQuantity('0.125','件')).toBe(false);expect(validMaterialQuantity('0.00001','米')).toBe(false);expect(validMaterialQuantity('0','米',true)).toBe(true);expect(validMaterialQuantity('0','米')).toBe(false)})
 it('converts decimal packs exactly without silent rounding',()=>{expect(multiplyQuantity('3','0.1')).toBe('0.3');expect(multiplyQuantity('0.0001','0.0001')).toBe('0.00000001');expect(validMaterialQuantity(multiplyQuantity('0.0001','0.0001'),'米')).toBe(false);expect(multiplyQuantity('2','12.5')).toBe('25')})
 it('rejects invalid and excessive input',()=>{for(const n of ['-1','NaN','Infinity','1e3','100000000000000'])expect(validMaterialQuantity(n,'米')).toBe(false)})
})
