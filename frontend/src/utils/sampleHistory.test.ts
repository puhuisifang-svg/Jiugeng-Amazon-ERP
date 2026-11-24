import { expect, it } from 'vitest'
import { sampleHistoryChanges } from './sampleHistory'
import type { ProductHistoryDto } from '@/api/domain'

it('shows only location changes and ignores other snapshot fields', () => {
 const event = {beforeValue:{locationCode:'A-01',remark:'old',sampleName:'old'},afterValue:{locationCode:'B-02',remark:'new',sampleName:'new'}} as unknown as ProductHistoryDto
 expect(sampleHistoryChanges(event)).toEqual([{key:'locationCode',label:'库位',before:'A-01',after:'B-02'}])
 expect(sampleHistoryChanges({...event,afterValue:{...event.afterValue,locationCode:'A-01'}})).toEqual([])
})
