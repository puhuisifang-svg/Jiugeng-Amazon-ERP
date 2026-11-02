import { flushPromises,mount } from '@vue/test-utils'
import { beforeEach,expect,it,vi } from 'vitest'
import { domainApi,type ProductDetailDto } from '@/api/domain'
import CustomerProductImage from './CustomerProductImage.vue'
vi.mock('@/api/domain',()=>({domainApi:{product:vi.fn()}}))
beforeEach(()=>{vi.clearAllMocks();HTMLDialogElement.prototype.showModal=function(){this.setAttribute('open','')};HTMLDialogElement.prototype.close=function(){this.removeAttribute('open')}})
it('uses the selected SKU image and opens a native preview above the picker',async()=>{
 vi.mocked(domainApi.product).mockResolvedValue({variants:[{id:'v1',imageAssetId:'asset1'},{id:'v2',imageAssetId:'asset2'}]} as ProductDetailDto)
 const w=mount(CustomerProductImage,{attachTo:document.body,props:{productId:'p1',variantId:'v2',alt:'测试商品'},global:{stubs:{AuthenticatedCatalogImage:true}}});await flushPromises()
 expect(w.findComponent({name:'AuthenticatedCatalogImage'}).props('imageId')).toBe('asset2')
 await w.get('[aria-label="放大查看测试商品"]').trigger('click')
 expect(document.querySelector('dialog')?.hasAttribute('open')).toBe(true)
 expect(w.findAllComponents({name:'AuthenticatedCatalogImage'})[1]!.props()).toMatchObject({imageId:'asset2',variant:'original'})
 ;(document.querySelector('[aria-label="关闭图片预览"]') as HTMLButtonElement).click();expect(document.querySelector('dialog')?.getAttribute('open')).toBeNull();w.unmount()
})
it('shows a placeholder when product images are unavailable',async()=>{
 vi.mocked(domainApi.product).mockRejectedValue(new Error('not accessible'))
 const w=mount(CustomerProductImage,{attachTo:document.body,props:{productId:'p1',variantId:'v1',alt:'测试商品'}});await flushPromises()
 expect(w.text()).toContain('暂无图片');expect(w.find('.customer-product-image').element.tagName).toBe('SPAN');w.unmount()
})
