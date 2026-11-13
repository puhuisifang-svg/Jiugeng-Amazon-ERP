<script setup lang="ts">
import { beijingDateTime } from '@/utils/dateTime'

import { computed } from 'vue'
import type { AuditEvent } from '@/api/audit'
import AccessDialog from './AccessDialog.vue'
import { actionLabel, categoryLabel, resourceLabel } from '@/utils/auditLabels'
const props = defineProps<{ event: AuditEvent }>()
const emit = defineEmits<{ close: [] }>()
const labels: Record<string, string> = { id:'编号', name:'名称', displayName:'显示名称', loginName:'登录账号', role:'角色', roles:'角色', roleIds:'角色', permissions:'权限', status:'状态', description:'说明', reason:'原因', remark:'备注', remarks:'备注', version:'版本', authorizationVersion:'授权版本', staySignedInDays:'免登录天数', days:'天数', email:'邮箱', phone:'手机号', lockedAt:'锁定时间', mustChangePassword:'首次登录修改密码', updatedAt:'更新时间', createdAt:'创建时间', productCode:'产品编码', skuCode:'SKU', quantity:'数量', price:'价格', amount:'金额', kind:'类型', memberCount:'成员数' }
function record(value: unknown): value is Record<string, unknown> { return !!value && typeof value === 'object' && !Array.isArray(value) }
function equal(a: unknown, b: unknown): boolean {
  if (a === b) return true
  if (Array.isArray(a) && Array.isArray(b)) return a.length === b.length && a.every((v,i) => equal(v,b[i]))
  if (record(a) && record(b)) return Object.keys(a).length === Object.keys(b).length && Object.keys(a).every(k => Object.hasOwn(b,k) && equal(a[k],b[k]))
  return false
}
const rows = computed(() => {
  const before: unknown = props.event.beforeValue, after: unknown = props.event.afterValue
  if (before == null && after == null) return []
  if ((before == null || record(before)) && (after == null || record(after))) {
    return [...new Set([...Object.keys(before || {}), ...Object.keys(after || {})])].map(key => ({ key, label:labels[key] || key, before:before?.[key], after:after?.[key], changed:!equal(before?.[key],after?.[key]) }))
  }
  return [{ key:'content', label:'内容', before, after, changed:!equal(before,after) }]
})
const changedCount = computed(() => rows.value.filter(r => r.changed).length)
function valueText(value: unknown) { return value === undefined || value === null ? '未记录' : value === '' ? '（空）' : typeof value === 'boolean' ? value ? '是' : '否' : typeof value === 'object' ? JSON.stringify(value,null,2) : String(value) }
const time = computed(() => beijingDateTime(props.event.occurredAt))
</script>
<template>
  <!-- eslint-disable vue/max-attributes-per-line, vue/singleline-html-element-content-newline -->
  <AccessDialog title="审计日志详情" wide @close="emit('close')">
    <div class="audit-detail">
      <div class="audit-detail-summary"><div><span class="audit-detail-category">{{ categoryLabel(event.category) }}</span><h3>{{ actionLabel(event.action) }}</h3></div><span class="audit-result" :class="event.outcome === 'SUCCESS' ? 'audit-result--success' : 'audit-result--failure'">{{ event.outcome === 'SUCCESS' ? '操作成功' : '操作失败' }}</span></div>
      <dl class="audit-detail-meta">
        <div><dt>操作人</dt><dd>{{ event.actorDisplayName || event.actorLoginName }}<small v-if="event.actorDisplayName && event.actorDisplayName !== event.actorLoginName">{{ event.actorLoginName }}</small></dd></div>
        <div><dt>操作时间</dt><dd>{{ time }}</dd></div>
        <div><dt>操作资源</dt><dd>{{ resourceLabel(event.resourceType) }}<small>{{ event.resourceId || '未关联具体资源' }}</small></dd></div>
        <div><dt>操作类型</dt><dd>{{ actionLabel(event.action) }}<small>{{ event.action }}</small></dd></div>
      </dl>
      <section v-if="event.detail" class="audit-detail-note"><h4>操作说明</h4><p>{{ event.detail }}</p></section>
      <section class="audit-change-section" aria-label="变更明细">
        <div class="audit-change-heading"><h4>变更明细</h4><span v-if="rows.length">{{ rows.length }} 个字段<span v-if="changedCount"> · {{ changedCount }} 项变动</span></span></div>
        <div v-if="rows.length" class="audit-change-table" role="table" aria-label="变更前后对比">
          <div class="audit-change-row audit-change-header" role="row"><span role="columnheader">字段</span><span role="columnheader">变更前</span><span role="columnheader">变更后</span></div>
          <div v-for="row in rows" :key="row.key" class="audit-change-row" :class="{'is-changed':row.changed}" role="row">
            <div class="audit-field-name" role="rowheader">{{ row.label }}<small v-if="row.changed">有变动</small></div>
            <div role="cell" data-label="变更前"><pre>{{ valueText(row.before) }}</pre></div><div role="cell" data-label="变更后"><pre>{{ valueText(row.after) }}</pre></div>
          </div>
        </div>
        <div v-else class="audit-no-changes">此操作未记录字段变更</div>
      </section>
      <p class="audit-record-id">记录编号：{{ event.id }}</p>
    </div>
  </AccessDialog>
</template>
<style scoped>
.audit-detail{display:grid;gap:16px;font-size:13px;color:var(--ink);min-width:0}
.audit-detail-summary{display:flex;align-items:center;justify-content:space-between;gap:12px;padding-bottom:12px;border-bottom:1px solid var(--line)}
.audit-detail-category{color:var(--muted);font-size:12px}.audit-detail-summary h3{margin:4px 0 0;font-size:18px;font-weight:600}
.audit-result{flex:none;border-radius:4px;padding:4px 9px;font-size:12px}.audit-result--success{background:#edf8f2;color:#21734b}.audit-result--failure{background:#fff0ef;color:#b42318}
.audit-detail-meta{margin:0;display:grid;grid-template-columns:1fr 1fr;gap:14px 24px}.audit-detail-meta>div{display:grid;grid-template-columns:64px minmax(0,1fr);gap:10px}.audit-detail-meta dt{color:var(--muted)}.audit-detail-meta dd{margin:0;overflow-wrap:anywhere}.audit-detail-meta small{display:block;margin-top:3px;color:var(--muted);font-size:12px}
.audit-detail-note{padding:12px;background:#f6f8fb;border:1px solid #e6eaf0;border-radius:6px}.audit-detail h4{margin:0;font-size:13px;font-weight:600}.audit-detail-note p{margin:6px 0 0;line-height:1.6;white-space:pre-wrap;overflow-wrap:anywhere}
.audit-change-heading{display:flex;justify-content:space-between;gap:10px;margin-bottom:8px}.audit-change-heading>span{color:var(--muted);font-size:12px}.audit-change-table{border:1px solid var(--line);border-radius:6px;overflow:hidden}.audit-change-row{display:grid;grid-template-columns:minmax(100px,.6fr) minmax(0,1fr) minmax(0,1fr);border-top:1px solid #e8ecf1}.audit-change-row>div,.audit-change-row>span{padding:10px 12px;min-width:0}.audit-change-header{background:#f6f8fb;border-top:0;color:var(--muted);font-size:12px}.audit-change-row.is-changed>[role=cell]:last-child{background:#f0f7ff}.audit-field-name{overflow-wrap:anywhere;font-weight:500}.audit-field-name small{display:block;font-size:11px;color:#2864c5;font-weight:400;margin-top:4px}.audit-change-row pre{margin:0;white-space:pre-wrap;overflow-wrap:anywhere;font:12px/1.65 ui-monospace,monospace;color:inherit}.audit-no-changes{padding:22px 12px;text-align:center;color:var(--muted);border:1px dashed var(--line);border-radius:6px;background:#fafbfd}.audit-record-id{margin:0;color:var(--muted);font-size:11px;overflow-wrap:anywhere}
@media(max-width:640px){.audit-detail-meta{grid-template-columns:1fr}.audit-change-header{display:none}.audit-change-row{grid-template-columns:1fr}.audit-change-row>[role=cell]::before{content:attr(data-label);display:block;color:var(--muted);font-size:11px;margin-bottom:4px}.audit-field-name{display:flex;align-items:center;gap:8px;background:#f6f8fb}.audit-field-name small{margin:0}.audit-change-heading{flex-wrap:wrap}}
</style>
