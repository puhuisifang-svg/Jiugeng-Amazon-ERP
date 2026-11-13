import { describe, it, expect } from 'vitest'
import { validChinaPhone } from './chinaPhone'
import { mount } from '@vue/test-utils'
import { vPhone } from '@/directives/phone'
describe('China contact phone rules',()=>{
 it('accepts mobiles, area-code landlines, extensions and service numbers',()=>{
  for(const value of ['', '13800138000','+86 13800138000','008613800138000','0592-1234567','010-87654321-123','05921234567','400-123-4567','10086'])expect(validChinaPhone(value),value).toBe(true)
 })
 it('rejects Chinese, arbitrary symbols, incorrect lengths and invalid mobile prefixes',()=>{
  for(const value of ['你好','电话13800138000','123','12345678901','1380013800','138001380001','0592-123','138\n00138000','+1-555-1234567'])expect(validChinaPhone(value),value).toBe(false)
  expect(validChinaPhone('0592-1234567','mobile')).toBe(false)
 })
 it('updates validity on typed and cleared phone values',async()=>{
  const w=mount({directives:{phone:vPhone},template:'<input v-phone />'})
  const input=w.get('input');await input.setValue('中文');expect((input.element as HTMLInputElement).checkValidity()).toBe(false)
  await input.setValue('0592-1234567');expect((input.element as HTMLInputElement).checkValidity()).toBe(true)
  await input.setValue('');expect((input.element as HTMLInputElement).checkValidity()).toBe(true);w.unmount()
 })
})
