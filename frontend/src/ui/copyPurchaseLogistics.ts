/** Keep copying available on the existing HTTP ERP entry as well as HTTPS. */
export async function copyPurchaseLogistics(carrier?:string|null,trackingNumber?:string|null){
  const text=[carrier?.trim(),trackingNumber?.trim()].filter(Boolean).join(' ')
  try{if(navigator.clipboard){await navigator.clipboard.writeText(text);return}}catch{/* HTTP/permission fallback below. */}
  const focus=document.activeElement as HTMLElement|null
  const field=document.createElement('textarea')
  field.value=text;field.readOnly=true;field.style.cssText='position:fixed;left:0;top:0;opacity:0;pointer-events:none'
  ;(focus?.closest('dialog')??document.body).append(field)
  try{field.select();if(!document.execCommand('copy'))throw Error('复制失败')}finally{field.remove();focus?.focus()}
}
