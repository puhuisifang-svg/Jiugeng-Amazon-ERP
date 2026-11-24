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

export function sampleLabelsDocument(rows: SampleLabelData[]) {
  if (!rows.length || rows.length > 500) throw new Error('一次支持 1～500 张标签')
  const base = sampleLabelDocument(rows[0]!)
  const labels = rows.map(row => sampleLabelDocument(row).split('<body>')[1]!.split('</body>')[0]!).join('')
  return base.replace('height:30mm;background:', 'height:auto;background:')
    .replace('</style>', '.label{break-after:page;page-break-after:always;overflow:hidden}.label:last-child{break-after:auto;page-break-after:auto}@media screen{html,body{background:#f1f4f8;overflow:hidden}.label{background:white;margin-bottom:12px}.label:last-child{margin-bottom:0}}</style>')
    .replace(/<body>[\s\S]*<\/body>/, () => `<body>${labels}</body>`)
}

export function sampleLabelFilename(rows: SampleLabelData[]) {
  const safe = (value: string) => [...value].map(c => c.charCodeAt(0) < 32 ? '-' : c).join('').replace(/[\\/:*?"<>|]/g, '-').trim().slice(0, 80)
  const name = safe(rows[0]?.skuName ?? '') || '未命名样品'
  const location = safe(rows[0]?.locationCode ?? '')
  return rows.length > 1
    ? `样品标签-${name}等${rows.length}款.pdf`
    : `样品标签-${name}${location ? `-${location}` : ''}.pdf`
}
