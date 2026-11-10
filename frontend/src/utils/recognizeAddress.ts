export interface RecognizedAddress { contactPerson?: string; phone?: string; address?: string }
// Parse locally. Uncertain names are left blank for manual review.
export function recognizeAddress(text:string):RecognizedAddress {
 const result:RecognizedAddress={}
 let remaining=text.trim()
 const phone=remaining.match(/(?:\+?86[-\s]*)?(?<!\d)(1[3-9]\d(?:[-\s]?\d){8})(?!\d)/)
 if(phone){result.phone=phone[1]!.replace(/[-\s]/g,'');remaining=remaining.replace(phone[0],'')}
 const labelled=remaining.match(/(?:收货人|联系人|姓名)\s*[:：]?\s*([\p{Script=Han}·]{2,12})(?=[\s,，;；]|$)/u)
 if(labelled){result.contactPerson=labelled[1]!;remaining=remaining.replace(labelled[0],'')}
 const segments=remaining.replace(/(?:联系电话|手机号|手机|电话|详细地址|收货地址|地址)\s*[:：]?/g,'').split(/[,，;；\n\r]+/).map(v=>v.trim()).filter(Boolean)
 if(!result.contactPerson){const names=segments.filter(v=>/^[\p{Script=Han}·]{2,5}$/u.test(v)&&!/[省市区县镇村路街号楼栋室]$/.test(v));if(names.length===1){result.contactPerson=names[0]!;segments.splice(segments.indexOf(names[0]!),1)}}
 const address=segments.join(' ').trim()
 if(address)result.address=address
 return result
}
