import {expect,it} from 'vitest'
import {recognizeAddress} from './recognizeAddress'
it('recognizes address, name and mobile separated by mixed punctuation',()=>{
 expect(recognizeAddress('合成省合成市测试路6号C栋2楼，测试甲,13800000000')).toEqual({contactPerson:'测试甲',phone:'13800000000',address:'合成省合成市测试路6号C栋2楼'})
})
it('supports labelled multiline text and country prefix without sending data externally',()=>{
 expect(recognizeAddress('联系人：测试乙\n手机：+86 138 0000 0000\n地址：测试市测试路8号')).toEqual({contactPerson:'测试乙',phone:'13800000000',address:'测试市测试路8号'})
})
it('does not invent a contact or phone for address-only input',()=>{
 expect(recognizeAddress('测试市测试路8号')).toEqual({address:'测试市测试路8号'})
 expect(recognizeAddress('')).toEqual({})
})
