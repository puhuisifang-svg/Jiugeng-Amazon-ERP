import { beijingDate } from '@/utils/dateTime'
import type { PurchaseOrder, PurchaseLine } from '@/api/purchases'
import { catalogApi } from '@/api/catalog'
export interface ExportRow { date:string; line:PurchaseLine }
export function purchaseExportRows(orders:PurchaseOrder[]):ExportRow[]{
  return [...orders].sort((a,b)=>b.createdAt.localeCompare(a.createdAt)).flatMap(order=>{
    const date=beijingDate(order.createdAt)
    return order.lines.map(line=>({date,line}))
  })
}
export function exportPages(rows:ExportRow[],size=20){return Array.from({length:Math.ceil(rows.length/size)},(_,i)=>rows.slice(i*size,(i+1)*size))}
function wrap(ctx:CanvasRenderingContext2D,text:string,width:number){const lines:string[]=[];let current='';for(const char of text){if(current&&ctx.measureText(current+char).width>width){lines.push(current);current=''}current+=char}if(current)lines.push(current);return lines}
async function loadImage(line:PurchaseLine):Promise<HTMLImageElement|null>{
  if(!line.imageAssetId&&!line.imageUrl)return null
  const blob=line.imageAssetId?await catalogApi.imageBlob(line.imageAssetId,'original'):await fetch(line.imageUrl!,{credentials:'omit',referrerPolicy:'no-referrer',signal:AbortSignal.timeout(15000)}).then(r=>{if(!r.ok)throw Error('图片加载失败');return r.blob()})
  const url=URL.createObjectURL(blob)
  try{const image=new Image();image.src=url;await image.decode();return image}finally{URL.revokeObjectURL(url)}
}
export async function renderPurchasePage(rows:ExportRow[],progress:()=>void){
  const canvas=document.createElement('canvas');canvas.width=1348
  const ctx=canvas.getContext('2d');if(!ctx)throw Error('浏览器无法生成图片')
  const x=[0,213,531,887,1051,1348],header=50
  ctx.font='24px "Microsoft YaHei", "PingFang SC", sans-serif'
  const names=rows.map(r=>wrap(ctx,r.line.itemName,294)),heights=names.map(n=>Math.max(220,n.length*30+24))
  canvas.height=header+heights.reduce((a,b)=>a+b,0)
  ctx.fillStyle='#fff';ctx.fillRect(0,0,canvas.width,canvas.height)
  ctx.fillStyle='#216bfa';ctx.fillRect(0,0,canvas.width,header)
  ctx.font='bold 28px "Microsoft YaHei", "PingFang SC", sans-serif';ctx.fillStyle='#fff';ctx.textAlign='center';ctx.textBaseline='middle'
  ;['采购日期','名称','图片','数量','单位'].forEach((name,i)=>ctx.fillText(name,(x[i]!+x[i+1]!)/2,25))
  let y=header,groupStart=header;const warnings:string[]=[]
  for(let i=0;i<rows.length;i++){
    const {line,date}=rows[i]!,height=heights[i]!
    ctx.fillStyle='#111';ctx.font='24px "Microsoft YaHei", "PingFang SC", sans-serif';ctx.textAlign='left'
    names[i]!.forEach((name,n)=>ctx.fillText(name,x[1]!+10,y+(height-names[i]!.length*30)/2+n*30+15))
    ctx.textAlign='center';ctx.font='bold 28px sans-serif';ctx.fillText(String(line.orderedQuantity),(x[3]!+x[4]!)/2,y+height/2)
    ctx.font='26px "Microsoft YaHei", "PingFang SC", sans-serif';ctx.fillText(line.unit,(x[4]!+x[5]!)/2,y+height/2,270)
    let image:HTMLImageElement|null=null,failed=false
    try{image=await loadImage(line)}catch{failed=true;warnings.push(line.itemName)}
    if(image){const ratio=Math.min(336/image.naturalWidth,(height-12)/image.naturalHeight);const w=image.naturalWidth*ratio,h=image.naturalHeight*ratio;ctx.drawImage(image,(x[2]!+x[3]!-w)/2,y+(height-h)/2,w,h)}
    else{ctx.fillStyle='#64748b';ctx.font='22px sans-serif';ctx.fillText(failed?'图片加载失败':'暂无图片',(x[2]!+x[3]!)/2,y+height/2)}
    progress();y+=height
    const groupEnd=rows[i+1]?.date!==date
    if(groupEnd){ctx.fillStyle='#111';ctx.font='26px sans-serif';ctx.fillText(date,106.5,(groupStart+y)/2);groupStart=y}
    ctx.strokeStyle='#111';ctx.lineWidth=2;ctx.beginPath();ctx.moveTo(groupEnd?0:213,y);ctx.lineTo(1348,y);ctx.stroke()
  }
  ctx.strokeStyle='#111';ctx.lineWidth=2
  for(const column of x){ctx.beginPath();ctx.moveTo(column,0);ctx.lineTo(column,canvas.height);ctx.stroke()}
  ctx.beginPath();ctx.moveTo(0,header);ctx.lineTo(1348,header);ctx.stroke()
  const blob=await new Promise<Blob>((resolve,reject)=>canvas.toBlob(b=>b?resolve(b):reject(Error('图片生成失败')),'image/png'))
  return {blob,warnings}
}
