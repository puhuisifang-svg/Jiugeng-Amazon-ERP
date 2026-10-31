import {it,expect} from 'vitest'
import {overdue,changedKeys} from './developmentPresentation'
it('marks overdue plans without marking completed projects or unknown dates',()=>{const today=new Date(2026,9,30);expect(overdue({endDate:'2026-10-29'},'ACTIVE',today)).toBe(true);expect(overdue({endDate:'2026-10-30'},'ACTIVE',today)).toBe(false);expect(overdue({},'ACTIVE',today)).toBe(false);expect(overdue({endDate:'2026-10-29'},'COMPLETED',today)).toBe(false)})
it('compares additions, removals and nested rows without hiding zero',()=>{expect(changedKeys({price:0,brand:'old',specs:[{name:'size',target:1}]},{price:10,specs:[{name:'size',target:2}]})).toEqual(['price','brand','specs'])})
