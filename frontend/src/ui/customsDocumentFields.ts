export interface DocumentField {key:string;label:string;type?:'date'|'number'|'textarea'|undefined;required?:boolean;hint?:string}
const field=(key:string,label:string,required=false,type?:DocumentField['type']):DocumentField=>({key,label,required,type})
export const headerGroups=[
 {name:'单证信息',fields:[field('contractNo','合同协议号',true),field('invoiceNo','发票编号',true),field('date','单证日期',true,'date'),field('currency','币制',true),field('orderNo','订单编号'),field('fbaNo','FBA 编号'),field('warehouse','FBA 仓库代码'),field('signPlace','签约地点')]},
 {name:'境内发货人 / 卖方',fields:[field('seller','境内发货人',true),field('sellerAddress','发货人地址',true),field('sellerPhone','发货人电话'),field('sellerFax','发货人传真'),field('producer','生产销售单位',true)]},
 {name:'境外收货人 / 买方',fields:[field('buyer','境外收货人',true),field('buyerAddress','收货人地址',true,'textarea'),field('buyerPhone','收货人电话'),field('buyerFax','收货人传真')]},
]
export const transportGroups=[
 {name:'运输与申报',fields:[field('customsPort','出境关别'),field('exportDate','出口日期',false,'date'),field('declarationDate','申报日期',false,'date'),field('recordNo','备案号'),field('transport','运输方式'),field('voyage','运输工具名称及航次号'),field('billNo','提运单号'),field('supervision','监管方式（9810）'),field('taxNature','征免性质'),field('license','许可证号'),field('tradeCountry','贸易国（地区）'),field('destination','运抵国（地区）',true),field('destinationPort','指运港'),field('exitPort','离境口岸')]},
 {name:'包装与费用',fields:[field('packaging','包装种类',true),field('terms','成交方式',true),field('freight','运费（含币制）',true),field('insurance','保费（含币制）'),field('otherCharges','杂费（含币制）'),field('sourcePlace','境内货源地（默认）'),field('exemption','征免（默认）'),field('payment','付款条件'),field('tradeTerms','贸易条款'),field('tolerance','数量及总值增减比例（%，默认5）',false,'number'),field('packingMarks','包装及唛头'),field('shippingMarks','装运标记')]},
 {name:'随附单证与确认事项',fields:[field('documents','随附单证',false,'textarea'),field('marks','标记唛码及备注',false,'textarea'),field('relatedParty','特殊关系确认'),field('priceInfluence','价格影响确认'),field('royalty','支付特许权使用费确认'),field('selfTax','自报自缴'),field('declarant','申报人员 / 证号 / 电话'),field('declarationCompany','申报单位')]},
]
export const itemFields:DocumentField[]=[field('sku','SKU',true),field('name','中文申报品名',true),field('nameEn','英文申报品名'),field('hsCode','商品编号（HS）',true),field('quantity','成交数量',true,'number'),field('unit','成交单位',true),field('measurementUnit','计量单位（HS）'),field('price','申报单价',true,'number'),field('cartonNo','箱号范围'),field('cartons','箱数',true,'number'),field('grossWeight','总毛重（kg）',true,'number'),field('netWeight','总净重（kg）',true,'number'),field('origin','原产国（地区）',true),field('destination','最终目的国（留空沿用运抵国）'),field('sourcePlace','境内货源地（留空沿用默认）'),field('exemption','征免（留空沿用默认）'),field('quantity2','第二数量（留空取净重）',false,'number'),field('unit2','第二单位（默认千克）'),field('quantity3','第三数量',false,'number'),field('unit3','第三单位'),{...field('declarationElements','申报要素',true,'textarea'),hint:'可逐行填写“要素名称：内容”。按本票商品实际信息填写，不自动推断归类。'}]
export function blankItem(){return Object.fromEntries(itemFields.map(f=>[f.key,'']))}
export function missingFields(fields:Record<string,string>,items:Record<string,string>[]){const missing=[...headerGroups,...transportGroups].flatMap(g=>g.fields).filter(f=>f.required&&!String(fields[f.key]??'').trim()).map(f=>f.label);if(String(fields.freight??'').trim()&&!/^(?:USD\s*\/\s*)?\d+(?:\.\d+)?$/i.test(fields.freight!.trim()))missing.push('运费金额须为非负数字（USD）');if(!items.length)missing.push('至少添加一项商品');if(fields.ruleVersion==='2')for(const [key,label] of [['sellerPrefix','公司简称'],['fbaNo','FBA编号'],['warehouse','FBA仓库'],['grossWeight','毛重'],['netWeight','净重']])if(!String(fields[key!]??'').trim())missing.push(label!);items.forEach((r,i)=>{itemFields.filter(f=>f.required&&!(fields.ruleVersion==='2'&&['grossWeight','netWeight'].includes(f.key))&&!String(r[f.key]??'').trim()).forEach(f=>missing.push(`商品 ${i+1}：${f.label}`))});return missing}

const allFields=[...headerGroups,...transportGroups].flatMap(g=>g.fields)
const byKey=(key:string)=>allFields.find(f=>f.key===key)??({key,label:({sellerPrefix:'公司简称',cartonsTotal:'件数',grossWeight:'毛重（kg）',netWeight:'净重（kg）'} as Record<string,string>)[key]??key,type:['grossWeight','netWeight'].includes(key)?'number':undefined} as DocumentField)
export const customsSheetFields:Record<string,DocumentField[]>={
 declaration:['seller','customsPort','exportDate','declarationDate','recordNo','buyer','transport','voyage','billNo','producer','supervision','taxNature','license','contractNo','tradeCountry','destination','destinationPort','exitPort','packaging','cartonsTotal','grossWeight','netWeight','terms','currency','freight','insurance','otherCharges','documents','marks','orderNo','fbaNo','warehouse','relatedParty','priceInfluence','royalty','selfTax','declarant','declarationCompany','sourcePlace','exemption'].map(byKey),
 elements:[],
 contract:['seller','contractNo','sellerAddress','date','sellerPhone','sellerFax','signPlace','buyer','buyerAddress','buyerPhone','buyerFax','currency','tolerance','packingMarks','tradeTerms','destination','payment','shippingMarks'].map(byKey),
 invoice:['seller','invoiceNo','sellerAddress','date','buyer','buyerAddress','currency'].map(byKey),
 packing:['seller','date','sellerAddress','invoiceNo','buyer','contractNo','buyerAddress','payment','voyage','fbaNo','warehouse','grossWeight','netWeight'].map(byKey),
}
export const fixedReceiver='Amazon.com Services.Inc'
export function contractNumber(fields:Record<string,string>){const prefix=(fields.sellerPrefix??'').trim().toUpperCase(),fba=(fields.fbaNo??'').trim().toUpperCase(),date=fields.date??'';return /^[A-Z]{1,20}$/.test(prefix)&&/^\d{4}-\d{2}-\d{2}$/.test(date)&&/^[A-Z0-9-]{4,100}$/.test(fba)?prefix+date.replaceAll('-','')+fba.slice(-4):''}
export function cartonTotal(items:Record<string,string>[]){return items.reduce((sum,r)=>sum+(Number(r.cartons)||0),0)}

export const brokerFields=new Set(['customsPort','exportDate','declarationDate','recordNo','voyage','billNo','license','destinationPort','exitPort'])
export const shipmentDefaults={payment:'T/T',tolerance:'5',terms:'c&f',packaging:'纸制或纤维板制盒/箱',destination:'美国',transport:'水路运输',supervision:'跨境电商出口海外仓',taxNature:'一般征税',tradeCountry:'美国',documents:'随附单证1：跨境B2B出口单证\n随附单证2：代理报关委托协议（电子）\n随附单证3：合同；发票；装箱单'}
