import { flushPromises, mount } from '@vue/test-utils'
import { afterEach,describe,expect,it,vi } from 'vitest'
import CustomsFields from './CustomsFields.vue'
import { blankCustoms,profileElements,serializeElements,applyHsSchema,hsSchemaApi,type HsSchema } from '@/api/customs'
const schema=(code:string):HsSchema=>({code,productName:'合成分类名',measurementUnit:code==='6307909000'?'千克/个':'千克/平方米',sourceUrl:'https://www.hsbianma.com/Code/'+code+'.html',sourceUpdatedAt:'2026/8/23',fetchedAt:'2026-11-04',stale:false,fields:(code==='6307909000'?[['brandType','品牌类型'],['exportBenefit','出口享惠情况'],['compositionContent','成分含量'],['brand','品牌(中文或外文名称)'],['articleNumber','货号'],['gtin','GTIN'],['cas','CAS'],['other','其他']]:[['brandType','品牌类型'],['exportBenefit','出口享惠情况'],['floorCovering','是否为铺地制品'],['fiberComposition','纤维成分'],['dimensions','规格尺寸'],['weavingMethod','织造方法(无纺、粘绒、植绒、针织等)'],['fixingMethod','固定方式(施胶、涂层粘合等)'],['gtin','GTIN'],['cas','CAS'],['other','其他']]).map(([key,label])=>({key:key!,label:label!}))})
afterEach(()=>{vi.restoreAllMocks();vi.useRealTimers()})
describe('SKU申报要素',()=>{
 it('loads fields and measurement unit for each HS and preserves removed answers',async()=>{
  vi.useFakeTimers();vi.spyOn(hsSchemaApi,'get').mockImplementation(async code=>schema(code))
  const w=mount(CustomsFields,{props:{modelValue:{...blankCustoms(),chinaCode:'6307909000'}}});await vi.advanceTimersByTimeAsync(400);await flushPromises()
  let value=w.emitted('update:modelValue')!.at(-1)![0] as ReturnType<typeof blankCustoms>;await w.setProps({modelValue:value})
  expect(w.text()).toContain('成分含量');expect(w.text()).not.toContain('是否为铺地制品');expect(value.measurementUnit).toBe('千克/个')
  value={...value,elements:{...value.elements,articleNumber:'SKU-OLD'},chinaCode:'5705002000'};await w.setProps({modelValue:value});await vi.advanceTimersByTimeAsync(400);await flushPromises()
  value=w.emitted('update:modelValue')!.at(-1)![0] as typeof value;await w.setProps({modelValue:value});expect(w.text()).toContain('织造方法(无纺、粘绒、植绒、针织等)');expect(w.text()).not.toContain('成分含量');expect(value.elements?.other).toContain('货号：SKU-OLD');expect(value.measurementUnit).toBe('千克/平方米');w.unmount()
 })
 it('keeps answers and exposes a retry when lookup fails',async()=>{
  vi.useFakeTimers();const get=vi.spyOn(hsSchemaApi,'get').mockRejectedValue(new Error('查询失败'))
  const value=applyHsSchema(blankCustoms(),schema('6307909000'));value.elements!.articleNumber='KEEP';value.chinaCode='5705002000'
  const w=mount(CustomsFields,{props:{modelValue:value}});await vi.advanceTimersByTimeAsync(400);await flushPromises();expect(w.text()).toContain('查询失败');expect(value.elements!.articleNumber).toBe('KEEP');expect(w.get('input').element.validationMessage).toBe('查询失败')
  get.mockResolvedValue(schema('5705002000'));await w.get('button').trigger('click');await flushPromises();expect(w.emitted('update:modelValue')!.at(-1)![0]).toMatchObject({schemaCode:'5705002000'});w.unmount()
 })
 it('ignores a late response for the previously entered HS',async()=>{
  vi.useFakeTimers();let resolve!:(s:HsSchema)=>void
  vi.spyOn(hsSchemaApi,'get').mockImplementation(code=>code==='6307909000'?new Promise(r=>{resolve=r}):Promise.resolve(schema(code)))
  const w=mount(CustomsFields,{props:{modelValue:{...blankCustoms(),chinaCode:'6307909000'}}});await vi.advanceTimersByTimeAsync(400)
  await w.setProps({modelValue:{...blankCustoms(),chinaCode:'5705002000'}});await vi.advanceTimersByTimeAsync(400);await flushPromises();resolve(schema('6307909000'));await flushPromises();expect(w.emitted('update:modelValue')).toHaveLength(1);expect(w.emitted('update:modelValue')![0]![0]).toMatchObject({schemaCode:'5705002000'});w.unmount()
 })

 it('shows the twelve requested fields and emits structured edits',async()=>{const w=mount(CustomsFields,{props:{modelValue:{...blankCustoms(),elements:{fiberComposition:'原有内容'}}}});expect(w.findAll('label').slice(0,12).map(l=>l.text())).toEqual(['HS','品名','品牌类型','出口享惠情况','是否为铺地制品','纤维成分','规格尺寸','织造方法','固定方式','GTIN','CAS','其他']);await w.findAll('label').find(l=>l.text()==='纤维成分')!.get('input').setValue('100%合成纤维');expect(w.emitted('update:modelValue')?.at(-1)?.[0]).toMatchObject({elements:{fiberComposition:'100%合成纤维'}});w.unmount()})
 it('preserves unmapped historical details without guessing their meanings',()=>{const old={...blankCustoms(),elements:null,declarationElements:'品牌类型：无品牌\n旧内容',composition:'合成材质'};const result=profileElements(old);expect(result.brandType).toBe('无品牌');expect(result.other).toContain('旧内容');expect(result.other).toContain('原材质/成分：合成材质');expect(serializeElements(result)).toContain('CAS：');expect(old.declarationElements).toContain('旧内容')})
 it('disables editing for read-only views',()=>{const w=mount(CustomsFields,{props:{modelValue:blankCustoms(),disabled:true}});expect(w.get('fieldset').attributes('disabled')).toBeDefined();w.unmount()})
})
