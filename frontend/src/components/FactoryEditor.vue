<script setup lang="ts">
import { computed,onMounted,reactive,ref,watch } from 'vue'
import AccessDialog from './AccessDialog.vue'
import FactoryPicker from './FactoryPicker.vue'
import DatePicker from './DatePicker.vue'
import {vSingleSelect} from '@/directives/singleSelect'
import {factoryApi,type FactoryKind,type FactoryRecord,type FactoryAdjustment} from '@/api/factory'
import {factoryMoney,localDay} from '@/utils/factoryPresentation'
import {session} from '@/state/session'
const props=defineProps<{kind:FactoryKind;mode:string;record?:FactoryRecord|undefined;month?:string}>()
const emit=defineEmits<{close:[];saved:[]}>()
const dialog=ref<InstanceType<typeof AccessDialog>>()
const source=props.record
const form=reactive({name:source?.name??'',team:source ? (source.team??'') : (props.kind==='employees'?'缝制组':''),phone:source?.phone??'',bankName:source?.bankName??'',bankAccount:source?.bankAccount??'',operations:source?.operations??'',date:props.kind==='payroll'?(props.month||source?.date||localDay().slice(0,7)):(props.kind==='rates'?localDay():source?.date||localDay()),endDate:source?.endDate??'',remark:source?.remark??'',project:source?.project??'',operation:source?.operation??'',unit:source?.unit??'件',price:String(source?.price??''),employeeId:source?.employeeId??'',employeeName:source?.employeeName??'',productId:source?.productId??'',variantId:source?.variantId??'',skuCode:source?.skuCode??'',decimalQuantity:source?.decimalQuantity??false,reason:'',status:source?.status==='PENDING'?'PENDING':'DRAFT',duplicateConfirmed:false,historical:false,historicalAmount:'',amount:'',paymentDate:localDay(),method:'现金',advance:props.mode==='pay'&&source?.status==='DRAFT'})
interface EditLine { key:string;rateId:string;project:string;operation:string;unit:string;quantity:string;price:string;error:string;employeeId:string;employeeName:string;remark:string }
function emptyLine():EditLine{return {key:crypto.randomUUID(),rateId:'',project:'',operation:'',unit:'',quantity:'',price:'',error:'',employeeId:'',employeeName:'',remark:''}}
const lines=ref<EditLine[]>(source?.lines?.map(l=>({...emptyLine(),...l,quantity:String(l.quantity),price:String(l.price??'')}))??[emptyLine()])
const batch=ref(false),commonRate=ref<FactoryRecord>(),operations=ref<string[]>([]),adjustments=ref<FactoryAdjustment[]>(JSON.parse(JSON.stringify(source?.adjustments??[])))
const busy=ref(false),previewing=ref(false),error=ref(''),initial=ref(''),request=ref({content:'',key:''})
const serial=()=>JSON.stringify([form,lines.value,batch.value,commonRate.value?.id,adjustments.value])
initial.value=serial()
const dirty=computed(()=>serial()!==initial.value)
const title=computed(()=>props.mode==='pay'?'登记工资发放':props.mode==='adjust'?'工资加减项':props.mode==='stop'?'停止工价':props.kind==='employees'?(source?'编辑员工':'新增员工'):props.kind==='rates'?'新增工价版本':props.kind==='reports'?(source?'修改报工':'新增报工'):'生成员工工资')
const total=computed(()=>lines.value.reduce((sum,l)=>sum+Math.round((Number(l.quantity)||0)*(Number(l.price)||0)*100)/100,0))
let previewSeq=0
async function refreshPrices(){const seq=++previewSeq;previewing.value=true;const results=await Promise.all(lines.value.map(async l=>{const employee=batch.value?l.employeeId:form.employeeId;const rate=batch.value?commonRate.value?.id:l.rateId;if(!employee||!rate||!form.date)return {key:l.key,error:'',price:l.price};try{const r=await factoryApi.preview(employee,form.date,rate);return {key:l.key,error:'',price:String(r.price),project:r.project!,operation:r.operation!,unit:r.unit!,rateId:rate}}catch(e){return {key:l.key,error:e instanceof Error?e.message:'无法获取工价',price:''}}}));if(seq===previewSeq){for(const result of results){const line=lines.value.find(l=>l.key===result.key);if(line)Object.assign(line,result)}previewing.value=false}}
watch(()=>[form.employeeId,form.date],()=>{if(props.kind==='reports')void refreshPrices()})
function chooseRate(line:EditLine,r:FactoryRecord){Object.assign(line,{rateId:r.id,project:r.project,operation:r.operation,unit:r.unit,price:String(r.price)});void refreshPrices()}
function chooseEmployee(r:FactoryRecord){form.employeeId=r.id;form.employeeName=`${r.name} · ${r.number}`}
async function copyLast(){if(!form.employeeId)return;error.value='';try{const result=await factoryApi.list('reports',{employeeId:form.employeeId,size:10});const last=result.items.find(r=>r.status!=='VOID');if(!last?.lines?.length){error.value='没有可复制的报工项目';return}lines.value=last.lines.map(l=>({...emptyLine(),rateId:l.rateId,project:l.project,operation:l.operation,unit:l.unit,price:'',quantity:''}));await refreshPrices()}catch(e){error.value=e instanceof Error?e.message:'加载失败'}}
function addLine(){lines.value.push(emptyLine())}
function switchBatch(value:boolean){batch.value=value;lines.value=[emptyLine()];commonRate.value=undefined}
async function submit(){if(busy.value||previewing.value)return;error.value='';busy.value=true;try{
 let action=props.mode;let data:Record<string,unknown>={...form};
 if(props.kind==='reports'){
   if(form.status!=='DRAFT'&&lines.value.some(l=>l.error))throw new Error('请先处理明细行中的工价错误');
   const base={date:form.date,status:form.status,remark:form.remark,duplicateConfirmed:form.duplicateConfirmed,reason:form.reason};
   if(batch.value){const groups=new Map<string,EditLine[]>();for(const l of lines.value){if(!l.employeeId)throw new Error('每行都需要选择员工');groups.set(l.employeeId,[...(groups.get(l.employeeId)??[]),l])}action='saveBatch';data={items:[...groups].map(([employeeId,rows])=>({...base,employeeId,lines:rows.map(l=>({rateId:commonRate.value?.id??'',quantity:l.quantity,remark:l.remark}))}))}}
   else data={...base,employeeId:form.employeeId,lines:lines.value.map(l=>({rateId:l.rateId,quantity:l.quantity,remark:l.remark}))}
 }
 if(props.mode==='adjust')data={adjustments:adjustments.value,reason:form.reason}
 if(source&&!(props.kind==='rates'&&props.mode==='save'))Object.assign(data,{id:source.id,version:source.version})
 const content=JSON.stringify(data);if(content!==request.value.content)request.value={content,key:crypto.randomUUID()};
 await factoryApi.command(props.kind,action,{...data,requestId:request.value.key});emit('saved')
 }catch(e){error.value=e instanceof Error?e.message:'保存失败，请重试'}finally{busy.value=false}}
onMounted(async()=>{if(props.kind==='rates')try{operations.value=(await factoryApi.operations()).filter(x=>x.active).map(x=>x.name)}catch{error.value='工序列表暂时无法加载，可稍后重试'} })
</script>
<template>
  <AccessDialog
    ref="dialog"
    :title="title"
    :wide="kind==='reports'||kind==='rates'||mode==='adjust'"
    :dirty="dirty"
    :busy="busy"
    @close="emit('close')"
  >
    <div class="factory-ui">
      <p
        v-if="error"
        role="alert"
        class="factory-error"
      >
        {{ error }}
      </p>
      <form
        id="factory-editor-form"
        class="factory-form"
        @submit.prevent="submit"
      >
        <template v-if="mode==='pay'">
          <p class="factory-help wide">
            {{ source?.employeeName }} · {{ source?.date }} · 待发 ¥{{ factoryMoney(source?.remaining) }}。这里只登记实际发放，不执行转账。
          </p>
          <label>发放日期<DatePicker
            v-model="form.paymentDate"
            required
            :max="localDay()"
          /></label><label>发放金额<input
            v-model="form.amount"
            type="number"
            min="0.01"
            step="0.01"
            required
          ></label>
          <label>发放方式<select
            v-model="form.method"
            v-single-select
          ><option>现金</option><option>银行</option><option>其他</option></select></label>
          <label class="factory-check"><input
            v-model="form.advance"
            type="checkbox"
            :disabled="source?.status!=='DRAFT'"
          >登记为预支</label>
          <label class="wide">说明<textarea
            v-model="form.remark"
            maxlength="500"
            placeholder="实际付款情况、转账流水号或凭证说明"
          /></label>
        </template>
        <template v-else-if="mode==='adjust'">
          <p class="factory-help wide">
            加减项只影响应发。已付预支请登记在发放记录中，不要再扣一次。
          </p>
          <div class="factory-edit-table wide">
            <table>
              <thead><tr><th>方向</th><th>金额</th><th>用途说明</th><th /></tr></thead><tbody>
                <tr
                  v-for="(item,i) in adjustments"
                  :key="i"
                >
                  <td>
                    <select
                      v-model="item.direction"
                      v-single-select
                      :aria-label="`调整${i+1}方向`"
                    >
                      <option value="ADD">
                        增加
                      </option><option value="SUBTRACT">
                        减少
                      </option>
                    </select>
                  </td><td>
                    <input
                      v-model="item.amount"
                      type="number"
                      min="0.01"
                      step="0.01"
                      required
                      :aria-label="`调整${i+1}金额`"
                    >
                  </td><td>
                    <input
                      v-model="item.remark"
                      required
                      maxlength="500"
                      placeholder="如补贴、奖金、已确认的更正"
                      :aria-label="`调整${i+1}说明`"
                    >
                  </td><td>
                    <button
                      type="button"
                      class="factory-link danger"
                      @click="adjustments.splice(i,1)"
                    >
                      移除
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <button
            type="button"
            class="factory-secondary"
            @click="adjustments.push({direction:'ADD',amount:'',remark:''})"
          >
            ＋ 添加调整
          </button>
          <label class="wide">调整原因<input
            v-model="form.reason"
            required
            maxlength="500"
          ></label>
        </template>
        <template v-else-if="mode==='stop'">
          <p class="factory-help wide">
            停止日期当天及以后不再使用此工价，已保存报工不改变。
          </p><label>停止日期<DatePicker
            v-model="form.endDate"
            required
          /></label><label>原因<input
            v-model="form.reason"
            required
            maxlength="500"
          ></label>
        </template>
        <template v-else-if="kind==='employees'">
          <label>员工姓名<input
            v-model="form.name"
            required
            maxlength="100"
          ></label><label>班组<select
            v-model="form.team"
            v-single-select
          ><option
            v-if="source && !['缝制组','包装组','铁艺组'].includes(form.team)"
            :value="form.team"
          >{{ form.team || '未设置' }}（原班组）</option><option>缝制组</option><option>包装组</option><option>铁艺组</option></select></label><label>手机号<input
            v-model="form.phone"
            maxlength="100"
            inputmode="tel"
          ></label><label>开户行<input
            v-model="form.bankName"
            maxlength="100"
            placeholder="银行名称及支行（选填）"
          ></label><label>银行卡号<input
            v-model="form.bankAccount"
            type="text"
            inputmode="numeric"
            maxlength="40"
            placeholder="银行卡号（选填）"
            autocomplete="off"
          ></label><label>入职日期<DatePicker
            v-model="form.date"
            required
          /></label><label>离职日期<DatePicker v-model="form.endDate" /></label><label class="wide">备注<textarea
            v-model="form.remark"
            maxlength="500"
          /></label><p class="factory-help wide">
            工号自动生成。离职保留报工和工资，不影响结清历史款项。
          </p>
        </template>
        <template v-else-if="kind==='rates'">
          <p class="factory-help wide">
            工价按加工项目、工序、单位和生效日期管理。新价格不会自动覆盖历史报工。
          </p>
          <div
            v-if="session.canAccess(['products.read'])"
            class="wide factory-inline"
          >
            <FactoryPicker
              kind="products"
              label="关联产品 SKU（可选）"
              :value="form.skuCode"
              @select="form.productId=$event.productId!;form.variantId=$event.variantId!;form.skuCode=$event.skuCode!;form.project=$event.project!"
            /><button
              v-if="form.productId"
              type="button"
              @click="form.productId='';form.variantId='';form.skuCode=''"
            >
              清除关联
            </button>
          </div>
          <label>加工项目<input
            v-model="form.project"
            required
            maxlength="100"
            placeholder="工人熟悉的加工名称，规格不同请分开"
          ></label><label>工序<input
            v-model="form.operation"
            list="factory-operations"
            required
            maxlength="100"
            placeholder="如包边、压线"
          ><datalist id="factory-operations"><option
            v-for="op in operations"
            :key="op"
            :value="op"
          /></datalist></label>
          <label>单位<input
            v-model="form.unit"
            required
            maxlength="20"
            placeholder="件、套、条等"
          ></label><label>计件单价（元）<input
            v-model="form.price"
            type="number"
            min="0.000001"
            step="0.000001"
            required
          ></label><label>生效日期<DatePicker
            v-model="form.date"
            required
          /></label><label class="factory-check"><input
            v-model="form.decimalQuantity"
            type="checkbox"
          >数量允许最多3位小数</label>
          <div class="wide factory-inline">
            <FactoryPicker
              kind="employees"
              label="专属员工（默认全部员工）"
              :value="form.employeeId?form.employeeName:'全部员工（标准工价）'"
              @select="chooseEmployee"
            /><button
              v-if="form.employeeId"
              type="button"
              @click="form.employeeId='';form.employeeName=''"
            >
              使用标准工价
            </button>
          </div><label class="wide">备注<textarea
            v-model="form.remark"
            maxlength="500"
            placeholder="专属约定、规格或计价说明"
          /></label>
        </template>
        <template v-else-if="kind==='reports'">
          <div
            v-if="!source"
            class="factory-editor-tabs wide"
          >
            <button
              type="button"
              :aria-pressed="!batch"
              @click="switchBatch(false)"
            >
              按员工录入
            </button><button
              type="button"
              :aria-pressed="batch"
              @click="switchBatch(true)"
            >
              按项目分配员工
            </button>
          </div>
          <label>工作日期<DatePicker
            v-model="form.date"
            :max="localDay()"
            required
          /></label><div
            v-if="!batch"
            class="factory-field"
          >
            <span>员工</span><FactoryPicker
              kind="employees"
              label="选择报工员工"
              :value="form.employeeName"
              @select="chooseEmployee"
            />
          </div>
          <div
            v-else
            class="factory-field"
          >
            <span>加工项目 / 工序</span><FactoryPicker
              kind="rates"
              label="选择共同加工工序"
              :value="commonRate?`${commonRate.project} / ${commonRate.operation}`:''"
              @select="commonRate=$event;refreshPrices()"
            />
          </div>
          <div class="factory-edit-table wide">
            <table>
              <thead><tr><th>{{ batch?'员工':'加工项目 / 工序' }}</th><th>数量</th><th>单位</th><th>适用工价</th><th>预计金额</th><th>明细备注</th><th /></tr></thead><tbody>
                <tr
                  v-for="(line,i) in lines"
                  :key="line.key"
                >
                  <td>
                    <FactoryPicker
                      v-if="batch"
                      kind="employees"
                      :label="`选择第${i+1}行员工`"
                      :value="line.employeeName"
                      @select="line.employeeId=$event.id;line.employeeName=$event.name!;refreshPrices()"
                    /><FactoryPicker
                      v-else
                      kind="rates"
                      :label="`选择第${i+1}行加工工序`"
                      :value="line.rateId?`${line.project} / ${line.operation}`:''"
                      @select="chooseRate(line,$event)"
                    /><small
                      v-if="line.error"
                      class="factory-error"
                    >{{ line.error }}</small>
                  </td><td>
                    <input
                      v-model="line.quantity"
                      type="number"
                      min="0.001"
                      step="any"
                      required
                      :aria-label="`第${i+1}行数量`"
                    >
                  </td><td>{{ line.unit||'—' }}</td><td>{{ line.price||'—' }}</td><td class="factory-number">
                    {{ line.price&&line.quantity?factoryMoney(Number(line.price)*Number(line.quantity)):'—' }}
                  </td><td>
                    <input
                      v-model="line.remark"
                      maxlength="300"
                      :aria-label="`第${i+1}行备注`"
                    >
                  </td><td>
                    <button
                      type="button"
                      class="factory-link danger"
                      :disabled="lines.length===1"
                      @click="lines.splice(i,1)"
                    >
                      移除
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <button
            type="button"
            class="factory-secondary"
            @click="addLine"
          >
            ＋ 再加一行
          </button><button
            v-if="!batch&&!source&&form.employeeId"
            type="button"
            @click="copyLast"
          >
            复制最近报工项目
          </button><strong class="factory-total">{{ previewing?'正在匹配工价…':`预计合计 ¥${factoryMoney(total)}` }}</strong>
          <label class="wide">批次 / 备注<input
            v-model="form.remark"
            maxlength="500"
            placeholder="同日重复工序请说明不同批次"
          ></label><label class="factory-check wide"><input
            v-model="form.duplicateConfirmed"
            type="checkbox"
          >如有同日同工序记录，已核实为不同批次</label><label
            v-if="source"
            class="wide"
          >修改原因<input
            v-model="form.reason"
            required
            maxlength="500"
          ></label>
          <label>保存状态<select
            v-model="form.status"
            v-single-select
          ><option value="DRAFT">保存草稿</option><option value="PENDING">提交核对</option><option
            v-if="session.canAccess(['factory.reports.review'])"
            value="REVIEWED"
          >保存并核对</option></select></label>
        </template>
        <template v-else-if="kind==='payroll'">
          <label>工资月份<input
            v-model="form.date"
            type="month"
            required
          ></label><div class="factory-field">
            <span>员工</span><FactoryPicker
              kind="employees"
              label="选择工资员工"
              :value="form.employeeName"
              @select="chooseEmployee"
            />
          </div>
          <label class="factory-check wide"><input
            v-model="form.historical"
            type="checkbox"
          >历史工资期初登记（只有汇总，没有每日明细）</label><template v-if="form.historical">
            <label>历史应发金额<input
              v-model="form.historicalAmount"
              type="number"
              min="0"
              step="0.01"
              required
            ></label><label>来源说明<input
              v-model="form.remark"
              required
              maxlength="500"
              placeholder="核对过的手工账来源"
            ></label>
          </template><p class="factory-help wide">
            已核对报工自动汇总。历史期初不能与同月日常报工重复计薪；已发款在生成后单独登记。
          </p>
        </template>
      </form>
    </div>
    <template #footer>
      <button
        type="button"
        :disabled="busy"
        @click="dialog?.requestClose()"
      >
        取消
      </button><span class="factory-footer-note">关闭窗口时会检查未保存内容</span><button
        type="submit"
        form="factory-editor-form"
        class="factory-primary"
        :disabled="busy||previewing"
      >
        {{ busy?'正在保存…':mode==='pay'?'确认登记':kind==='reports'?({DRAFT:'保存草稿',PENDING:'提交核对',REVIEWED:'保存并核对'}[form.status]):'保存' }}
      </button>
    </template>
  </AccessDialog>
</template>
