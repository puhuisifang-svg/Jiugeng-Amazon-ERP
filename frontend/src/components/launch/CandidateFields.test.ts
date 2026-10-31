import {mount} from '@vue/test-utils'
import {it,expect} from 'vitest'
import CandidateFields from './CandidateFields.vue'
it('displays human percentages while saving fractional rates and preserves unknown',async()=>{const w=mount(CandidateFields,{props:{fields:[{key:'commission',label:'佣金率（0–1）',type:'number'}],model:{commission:.15}}});expect(w.get('input').element.value).toBe('15');expect(w.text()).toContain('佣金率（%）');await w.get('input').setValue('20');expect(w.emitted('change')?.[0]).toEqual(['commission',.2]);await w.get('input').setValue('');expect(w.emitted('change')?.[1]).toEqual(['commission',null])})
