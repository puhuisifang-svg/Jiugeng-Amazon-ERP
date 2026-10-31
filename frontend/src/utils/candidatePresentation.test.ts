import {describe,it,expect} from 'vitest'
import {candidateScore,issueTab,riskProgress,safeLink} from './candidatePresentation'
import {defaults} from '@/api/launch'
describe('candidate presentation',()=>{
 it('keeps unknown scores distinct from a supported zero',()=>{expect(candidateScore(defaults({}))).toBeNull();expect(candidateScore({criteria:Array.from({length:5},()=>({score:0,reason:'Evidence'}))})).toBe(0);expect(candidateScore({criteria:Array.from({length:5},()=>({score:3,reason:'Evidence'}))})).toBe(60)})
 it('does not imply incomplete investigations are complete',()=>expect(riskProgress(defaults({}))).toBe(0))
 it('groups unresolved requirements and cost checks',()=>{expect(issueTab('开发要求缺少验收标准')).toBe('needs');expect(issueTab('汇率日期必填')).toBe('cost');expect(issueTab('风险调查尚未经审核人员复核')).toBe('risk')})
 it('only links HTTPS URLs without embedded credentials',()=>{expect(safeLink('javascript:alert(1)')).toBe('');expect(safeLink('https://user:pass@example.com')).toBe('');expect(safeLink('https://example.com/a')).toBe('https://example.com/a')})
})
