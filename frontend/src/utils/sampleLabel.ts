export type SampleLabelData = { skuName: string; specification?: string; locationCode: string }
const escape = (value: string) => value.replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]!)
export function sampleLabelDocument(row: SampleLabelData) {
  return `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><title>样品标签</title><style>
@page { size:70mm 30mm; margin:0; }
*{box-sizing:border-box}html,body{margin:0;padding:0;width:70mm;height:30mm;background:#fff;color:#000}
.label{width:70mm;height:30mm;padding:2mm;display:flex;align-items:center;font-family:"PingFang SC","Microsoft YaHei",sans-serif}
.content{width:100%;font-size:12px;line-height:1.25;overflow-wrap:anywhere;white-space:pre-wrap}
.name{font-size:1.18em;font-weight:500}.spec{margin-top:1mm;font-size:1em}.location{margin-top:1.5mm;font-size:1.4em;font-weight:800}
</style></head><body><div class="label"><div class="content"><div class="name">${escape(row.skuName)}</div><div class="spec">${escape(row.specification?.trim() || '规格：未填写')}</div><div class="location">库位：${escape(row.locationCode)}</div></div></div></body></html>`
}
