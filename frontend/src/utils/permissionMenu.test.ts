import { describe, expect, it } from 'vitest'
import { appRoutes } from '@/router'
import { navigationDomains } from '@/config/navigation'
import { buildPermissionMenu } from './permissionMenu'
import type { Permission } from '@/api/roles'
const permission=(code:string):Permission=>({code,module:'Synthetic',label:code,dependencies:[],assignable:true,sensitive:false})
const routes=appRoutes.find(r=>r.path==='/')!.children!
describe('permission menu follows application navigation',()=>{
 it('preserves first and second level menu order and excludes hidden routes',()=>{
  const groups=buildPermissionMenu(routes,[])
  expect(groups.map(g=>g.title)).toEqual(navigationDomains.map(g=>g.title))
  for(const group of groups){const def=navigationDomains.find(d=>d.title===group.title)!;expect(group.children.map(c=>c.key)).toEqual(def.groups.flatMap(g=>routes.filter(r=>!r.meta?.hidden&&r.meta?.group===g).map(r=>r.path)))}
 })
 it('covers shared permissions and never drops an unknown capability',()=>{
  const groups=buildPermissionMenu(routes,['products.read','imports.read','inventory.read','system.users.manage','synthetic.new'].map(permission))
  const children=groups.flatMap(g=>g.children)
  expect(children.find(c=>c.key==='products')!.codes).toContain('imports.read')
  expect(children.find(c=>c.key==='materials')!.codes).toEqual(['imports.read','inventory.read'])
  expect(children.find(c=>c.key==='compliance/customs')!.codes).toContain('products.read')
  expect(children.find(c=>c.key==='fba/warehouses')!.codes).toEqual(['products.read'])
  expect(groups.at(-1)!.codes).toEqual(['synthetic.new'])
  expect(new Set(groups.flatMap(g=>g.codes)).size).toBe(5)
 })
})
