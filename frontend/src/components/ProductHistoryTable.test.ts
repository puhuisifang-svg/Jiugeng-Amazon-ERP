import { mount } from '@vue/test-utils'
import { describe,expect,it } from 'vitest'
import ProductHistoryTable from './ProductHistoryTable.vue'
const entry={id:'event',action:'MATERIAL_CREATE',actorLoginName:'test',occurredAt:'2026-11-26T00:00:00Z',detail:'创建辅料主数据',afterValue:{id:'internal-material-id',name:'测试辅料',materialCode:'M01',status:'ACTIVE'}}
const global={stubs:{TableViewport:{template:'<div><slot /></div>'}}}
describe('material history presentation',()=>{
  it('renders creation as initial facts, keeping existing shared table behavior opt-in',()=>{
    const w=mount(ProductHistoryTable,{props:{entries:[entry],material:true},global})
    expect(w.text()).toContain('测试辅料');expect(w.text()).toContain('启用')
    expect(w.text()).not.toContain('internal-material-id');expect(w.text()).not.toContain('未填写');expect(w.find('.product-history-arrow').exists()).toBe(false)
    w.unmount()
    const original=mount(ProductHistoryTable,{props:{entries:[entry]},global})
    expect(original.find('.product-history-arrow').exists()).toBe(true);original.unmount()
  })
})
