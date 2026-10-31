import { vi } from 'vitest'
vi.mock('@/state/session', () => ({ session: { canAccess: () => true } }))
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ProductMonitorDetail from './ProductMonitorDetail.vue'
import type { MonitorTarget, MonitorSnapshot } from '@/api/monitoring'
const target:MonitorTarget={id:'synthetic',marketplace:'US',asin:'B0TEST0001',enabled:true,ownerId:null,ownerName:null,tags:[],remark:'Review packaging',version:0,fields:{title:'Synthetic product',price:19.99,currency:'USD',rating:'4.5 out of 5 stars',ratingCount:'100',smallCategoryRank:'#1 in Synthetic',largeCategoryRank:'#10 in Testing',dimensions:'12 x 10 inches',weight:'2 pounds',listedAt:'September 1, 2026',bullets:['First feature','Second feature']},source:'EXTERNAL:amazon-public-us',observedAt:'2026-10-27T12:00:00Z',lastStatus:'CHANGED',lastReason:null,lastAttemptAt:null,createdAt:'2026-10-20T12:00:00Z',followed:false}
const snapshot:MonitorSnapshot={id:'snapshot',targetId:'synthetic',requestId:'r',observedAt:'2026-10-27T12:00:00Z',recordedAt:'2026-10-27T12:01:00Z',source:'EXTERNAL:amazon-public-us',quality:'PARTIAL',decision:'CHANGED',reason:'Partial fields',fields:target.fields,changes:[{field:'price',before:10,after:19.99}]}
function open(extra={}){return mount(ProductMonitorDetail,{props:{target,history:[snapshot],historyPage:0,historyTotal:21,loading:false,busy:false,pending:false,collectionEnabled:true,collectionLabel:'已更新',...extra}})}
describe('product monitoring detail layout',()=>{
 it('groups all observed fields, keeps unknowns explicit and exposes shared editors',async()=>{
  const w=open();expect(w.text()).toContain('#1 in Synthetic');expect(w.text()).toContain('#10 in Testing');expect(w.text()).toContain('2 pounds');expect(w.text()).toContain('September 1, 2026');expect(w.text()).toContain('未填写');expect(w.findAll('.pmd-bullets li')).toHaveLength(2)
  expect(w.get('a[href="https://www.amazon.com/dp/B0TEST0001"]').attributes('target')).toBe('_blank')
  await w.get('[aria-label="编辑产品备注"]').trigger('click');expect(w.emitted('edit')?.[0]?.[0]).toBe('remark')
  await w.get('[aria-label="编辑近30天销量"]').trigger('click');expect(w.emitted('edit')?.[1]?.[0]).toBe('sales30Days');w.unmount()
 })
 it('separates history, compares before/after values and keeps server pagination',async()=>{
  const w=open();expect(w.find('.pmd-history').exists()).toBe(false)
  await w.findAll('nav button').find(b=>b.text().startsWith('变化记录'))!.trigger('click')
  expect(w.text()).toContain('1 项变化 · 价格');expect(w.findAll('.pmd-comparison p').map(e=>e.text())).toEqual(['10','19.99']);expect(w.text()).toContain('查看本次观测字段')
  await w.get('[aria-label="下一页"]').trigger('click');expect(w.emitted('page')).toEqual([[2]])
  await w.setProps({loading:true});expect(w.get('[aria-label="下一页"]').attributes('disabled')).toBeDefined();w.unmount()
 })
 it('shows running state and prevents duplicate updates, while paused target cannot collect',async()=>{
  const w=open({pending:true,collectionLabel:'采集中…'});expect(w.text()).toContain('采集中…');expect(w.findAll('button').find(b=>b.text()==='正在更新…')!.attributes('disabled')).toBeDefined()
  await w.setProps({pending:false,target:{...target,enabled:false}});expect(w.findAll('button').find(b=>b.text()==='立即更新')!.attributes('disabled')).toBeDefined();expect(w.findAll('button').find(b=>b.text()==='人工补录')!.attributes('disabled')).toBeDefined();w.unmount()
 })
})
