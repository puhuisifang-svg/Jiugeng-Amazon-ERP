import {afterEach,expect,it,vi} from 'vitest'
import {copyPurchaseLogistics} from './copyPurchaseLogistics'
const clipboardDescriptor=Object.getOwnPropertyDescriptor(navigator,'clipboard'),copyDescriptor=Object.getOwnPropertyDescriptor(document,'execCommand')
afterEach(()=>{vi.restoreAllMocks();document.body.innerHTML='';if(clipboardDescriptor)Object.defineProperty(navigator,'clipboard',clipboardDescriptor);else Reflect.deleteProperty(navigator,'clipboard');if(copyDescriptor)Object.defineProperty(document,'execCommand',copyDescriptor);else Reflect.deleteProperty(document,'execCommand')})
it('copies on HTTP inside the active dialog and restores focus',async()=>{
  Object.defineProperty(navigator,'clipboard',{configurable:true,value:undefined})
  const dialog=document.createElement('dialog'),button=document.createElement('button');dialog.append(button);document.body.append(dialog);dialog.open=true;button.focus()
  const copy=vi.fn(()=>{expect(dialog.querySelector('textarea')?.value).toBe('合成物流 SYN001');return true})
  Object.defineProperty(document,'execCommand',{configurable:true,value:copy})
  await copyPurchaseLogistics('合成物流','SYN001')
  expect(copy).toHaveBeenCalledWith('copy');expect(document.querySelector('textarea')).toBeNull();expect(document.activeElement).toBe(button)
})
it('reports failure when neither clipboard mechanism succeeds',async()=>{
  Object.defineProperty(navigator,'clipboard',{configurable:true,value:undefined})
  Object.defineProperty(document,'execCommand',{configurable:true,value:()=>false})
  await expect(copyPurchaseLogistics(null,'SYN001')).rejects.toThrow('复制失败');expect(document.querySelector('textarea')).toBeNull()
})
