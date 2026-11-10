<script setup lang="ts">
import {computed,ref,watch} from 'vue'
import type {FactoryKind,FactoryRecord} from '@/api/factory'
import {factoryMoney,factoryStates,paymentState,factoryProject} from '@/utils/factoryPresentation'
import ListPagination from './ListPagination.vue'
const props=defineProps<{kind:FactoryKind;record:FactoryRecord;section?:string;printing?:boolean;canVoid?:boolean}>()
const emit=defineEmits<{voidPayment:[string]}>()
const page=ref(1),size=ref(20)
const lines=computed(()=>props.kind==='payroll'?(props.record.reports??[]).flatMap(r=>(r.lines??[]).map(l=>({...l,date:r.date,reportId:r.id}))):(props.record.lines??[]).map(l=>({...l,date:props.record.date,reportId:props.record.id})))
const shown=computed(()=>props.printing?lines.value:lines.value.slice((page.value-1)*size.value,page.value*size.value))
watch(()=>[props.record.id,props.section],()=>{page.value=1})
</script>
<template>
  <div class="factory-ui">
    <template v-if="!section||section==='overview'">
      <div
        v-if="kind==='payroll'"
        class="factory-metrics"
      >
        <div><span>应发工资</span><strong>¥ {{ factoryMoney(record.amount) }}</strong><small>计件 + 加项 − 减项</small></div><div><span>已发金额</span><strong>¥ {{ factoryMoney(record.paid) }}</strong><small>{{ paymentState(record.amount,record.paid) }}</small></div><div><span>{{ Number(record.remaining)<0?'超发待处理':'待发金额' }}</span><strong :class="{'danger':Number(record.remaining)<0}">¥ {{ factoryMoney(Math.abs(Number(record.remaining??0))) }}</strong><small>{{ record.status==='LOCKED'?'核算已锁定':'未锁定，不作为最终工资' }}</small></div>
      </div>
      <dl class="factory-facts">
        <template v-if="kind==='employees'">
          <dt>姓名 / 工号</dt><dd>{{ record.name }} / {{ record.number }}</dd><dt>班组</dt><dd>{{ record.team||'—' }}</dd><dt>手机号</dt><dd>{{ record.phone||'—' }}</dd><dt>开户行</dt><dd>{{ record.bankName||'未填写' }}</dd><dt>银行卡号</dt><dd>{{ record.bankAccount||'未填写' }}</dd><dt>身份证号</dt><dd>{{ record.identityNumber||'未填写' }}</dd><dt>缴纳医社保</dt><dd>{{ record.insured?'是':'否' }}</dd><dt>入职日期</dt><dd>{{ record.date }}</dd><dt>离职日期</dt><dd>{{ record.endDate||'在职' }}</dd>
        </template>
        <template v-else-if="kind==='rates'">
          <dt>加工项目</dt><dd>{{ factoryProject(record) }}</dd><dt>计件单价</dt><dd>{{ record.price }} 元 / {{ record.unit }}</dd><dt>适用员工</dt><dd>{{ record.employeeName }}</dd><dt>生效日期</dt><dd>{{ record.date }}</dd><dt>停止日期</dt><dd>{{ record.endDate||'未设置' }}</dd><dt>关联 SKU</dt><dd>{{ record.skuCode||'未关联，自定义加工项目' }}</dd><dt>数量精度</dt><dd>{{ record.decimalQuantity?'最多3位小数':'整数' }}</dd>
        </template>
        <template v-else>
          <dt>员工 / 工号</dt><dd>{{ record.employeeName }} / {{ record.number }}</dd><dt>{{ kind==='reports'?'工作日期':'工资月份' }}</dt><dd>{{ record.date }}</dd><dt>班组</dt><dd>{{ record.team||'—' }}</dd><dt>状态</dt><dd>{{ factoryStates[record.status]||record.status }}</dd><template v-if="kind==='payroll'">
            <dt>开户行（员工当前资料）</dt><dd>{{ record.bankName||'未填写' }}</dd><dt>银行卡号（员工当前资料）</dt><dd>{{ record.bankAccount||'未填写' }}</dd><dt>计件工资</dt><dd>¥ {{ factoryMoney(record.base) }}</dd><dt>加项 / 减项</dt><dd>+{{ factoryMoney(record.plus) }} / −{{ factoryMoney(record.minus) }}</dd><dt>报工天数</dt><dd>{{ record.days||0 }} 天（不是考勤天数）</dd><dt>核算版本</dt><dd>{{ record.revision||0 }}{{ record.historical?' · 历史期初':'' }}</dd>
          </template>
        </template>
        <dt>备注</dt><dd>{{ record.remark||'—' }}</dd><dt>最近维护</dt><dd>{{ record.updatedBy||'—' }} <small>{{ record.updatedAt?new Date(record.updatedAt).toLocaleString():'—' }}</small></dd>
      </dl>
      <p
        v-if="kind==='payroll'&&record.stale"
        class="factory-warning"
      >
        报工数据已变化，请重新计算后再锁定。
      </p><p
        v-if="kind==='payroll'&&record.pending"
        class="factory-warning"
      >
        还有 {{ record.pending }} 张报工未核对，请先处理。
      </p><p
        v-if="kind==='payroll'&&record.historical"
        class="factory-help"
      >
        历史期初只登记已核对的月合计，不含每日明细。
      </p>
    </template>
    <template v-if="section==='lines'||(kind==='reports'&&section==='overview')">
      <div class="factory-edit-table">
        <table>
          <thead><tr><th>工作日期</th><th>加工项目</th><th>数量</th><th>单位</th><th>工价</th><th>金额</th><th>备注</th></tr></thead><tbody>
            <tr
              v-for="(l,i) in shown"
              :key="`${l.reportId}-${i}`"
            >
              <td>{{ l.date }}</td><td><strong>{{ factoryProject(l) }}</strong></td><td>{{ l.quantity }}</td><td>{{ l.unit }}</td><td>{{ l.priced===false?'待完善':l.price }}</td><td class="factory-number">
                {{ l.priced===false?'待定':factoryMoney(l.amount) }}
              </td><td>{{ l.remark||'—' }}</td>
            </tr><tr v-if="!lines.length">
              <td colspan="7">
                暂无计件明细
              </td>
            </tr>
          </tbody>
        </table>
      </div><ListPagination
        :total="lines.length"
        :page="page"
        :page-size="size"
        @update:page="page=$event"
        @update:page-size="size=$event;page=1"
      />
    </template>
    <template v-if="section==='adjustments'">
      <div class="factory-edit-table">
        <table>
          <thead><tr><th>类型</th><th>金额</th><th>用途说明</th></tr></thead><tbody>
            <tr
              v-for="(r,i) in record.adjustments"
              :key="i"
            >
              <td>{{ r.direction==='ADD'?'增加':'减少' }}</td><td>{{ factoryMoney(r.amount) }}</td><td>{{ r.remark }}</td>
            </tr><tr v-if="!record.adjustments?.length">
              <td colspan="3">
                暂无工资加减项
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
    <template v-if="section==='payments'">
      <div class="factory-edit-table">
        <table>
          <thead>
            <tr>
              <th>发放日期</th><th>金额</th><th>方式</th><th>经办人</th><th>说明</th><th>状态</th><th v-if="canVoid">
                操作
              </th>
            </tr>
          </thead><tbody>
            <tr
              v-for="r in record.payments"
              :key="r.id"
            >
              <td>{{ r.date }}</td><td>{{ factoryMoney(r.amount) }}</td><td>{{ r.method }}{{ r.advance?' · 预支':'' }}</td><td>{{ r.actor }}</td><td>{{ r.remark||'—' }}<small v-if="r.voidReason">作废原因：{{ r.voidReason }}</small></td><td>{{ r.voided?'已作废':'有效' }}</td><td v-if="canVoid">
                <button
                  v-if="!r.voided"
                  type="button"
                  class="factory-link danger"
                  @click="emit('voidPayment',r.id)"
                >
                  作废登记
                </button>
              </td>
            </tr><tr v-if="!record.payments?.length">
              <td :colspan="canVoid?7:6">
                暂无发放记录
              </td>
            </tr>
          </tbody>
        </table>
      </div><p class="factory-help">
        作废登记只更正台账，不撤销真实付款。
      </p>
    </template>
  </div>
</template>
