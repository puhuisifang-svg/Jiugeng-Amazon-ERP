export type PhoneKind = 'mobile' | 'contact'
export const PHONE_MESSAGE = '请填写有效的中国手机号或固定电话（需区号，如 0592-1234567，分机用 - 分隔）'
export function validChinaPhone(value: string, kind: PhoneKind = 'contact') {
  const text = value.trim().replaceAll(' ', '')
  if (!text) return true
  if (/^(?:\+86|0086)?1[3-9][0-9]{9}$/.test(text)) return true
  return kind === 'contact' && /^(?:\+86|0086)?(?:0[1-9][0-9]{1,2}-?[1-9][0-9]{6,7}(?:-[0-9]{1,6})?|(?:400|800)-?[0-9]{3}-?[0-9]{4}|100[0-9]{2}|9[56][0-9]{3,4})$/.test(text)
}
