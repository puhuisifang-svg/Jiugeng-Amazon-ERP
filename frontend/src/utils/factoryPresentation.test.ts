import {describe,it,expect} from 'vitest'
import {factoryMoney,paymentState} from './factoryPresentation'
describe('factory wage presentation',()=>{
 it('distinguishes unknown amounts from zero',()=>{expect(factoryMoney(undefined)).toBe('—');expect(factoryMoney(0)).toBe('0.00');expect(factoryMoney('115.50')).toBe('115.50')})
 it('keeps overpayment visible rather than silently clamping it',()=>{expect(paymentState('100','120')).toBe('待处理超发');expect(paymentState(100,30)).toBe('部分发放');expect(paymentState(100,100)).toBe('已发清');expect(paymentState(0,0)).toBe('无需发放')})
})
