<script setup lang="ts">
import { computed } from 'vue'
import type { Permission, PermissionPreview } from '@/api/roles'
const props = defineProps<{ preview: PermissionPreview; catalog: Permission[] }>()
const sections = computed(() => [
  { key: 'added', title: '新增权限', symbol: '+', codes: props.preview.added },
  { key: 'removed', title: '移除权限', symbol: '−', codes: props.preview.removed },
].map(section => ({ ...section, items: section.codes.map(code => ({ code, permission: props.catalog.find(p => p.code === code) })) })))
</script>
<template>
  <div class="permission-difference">
    <div
      class="difference-metrics"
      aria-label="权限变更概览"
    >
      <div class="metric-added">
        <span>新增权限</span><strong>{{ preview.added.length }}<small> 项</small></strong>
      </div>
      <div class="metric-removed">
        <span>移除权限</span><strong>{{ preview.removed.length }}<small> 项</small></strong>
      </div>
      <div><span>关联成员</span><strong>{{ preview.members.length }}<small> 人</small></strong></div>
    </div>
    <div class="difference-columns">
      <section
        v-for="section in sections"
        :key="section.key"
        :class="`difference-${section.key}`"
      >
        <h3><span aria-hidden="true">{{ section.symbol }}</span>{{ section.title }}<small>{{ section.items.length }} 项</small></h3>
        <ul v-if="section.items.length">
          <li
            v-for="item in section.items"
            :key="item.code"
          >
            <strong>{{ item.permission?.label || item.code }}</strong><small>{{ item.permission?.module || '其他权限' }}</small>
          </li>
        </ul>
        <p
          v-else
          class="difference-empty"
        >
          无{{ section.title }}
        </p>
      </section>
    </div>
    <details
      v-if="preview.members.length"
      class="difference-members"
    >
      <summary>查看关联成员（{{ preview.members.length }} 人）</summary><div>
        <span
          v-for="user in preview.members"
          :key="user.id"
        >{{ user.displayName || user.loginName }}<small v-if="user.displayName && user.loginName">{{ user.loginName }}</small></span>
      </div>
    </details>
    <p class="difference-note">
      {{ preview.members.length ? '权限或角色状态变更生效后，相关成员的现有登录将失效。' : '当前没有关联成员，本次变更不会影响其他账号。' }}
    </p>
  </div>
</template>
<style scoped>
.permission-difference{font-size:13px;min-width:0}.difference-metrics{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px;margin:0 0 16px}.difference-metrics>div{display:grid;gap:6px;background:#f7f9fc;border:1px solid var(--line);border-radius:6px;padding:12px}.difference-metrics span{color:var(--muted);font-size:12px}.difference-metrics strong{font-size:24px;font-weight:600;line-height:1.2;font-variant-numeric:tabular-nums}.difference-metrics small{font-size:12px;font-weight:400;color:var(--muted)}.metric-added strong{color:#16745a}.metric-removed strong{color:#b42318}.difference-columns{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.difference-columns section{min-width:0;border:1px solid var(--line);border-radius:6px;overflow:hidden}.difference-columns h3{display:flex;align-items:center;gap:7px;margin:0;padding:10px 12px;font-size:13px;background:#f8fafc;border-bottom:1px solid var(--line)}.difference-columns h3>small{margin-left:auto;font-weight:400;color:var(--muted);font-size:12px}.difference-added h3>span{color:#16745a}.difference-removed h3>span{color:#b42318}.difference-columns ul{list-style:none;margin:0;padding:0;max-height:240px;overflow:auto;overscroll-behavior:contain}.difference-columns li{padding:9px 12px;border-bottom:1px solid #edf0f4;display:grid;gap:3px;overflow-wrap:anywhere}.difference-columns li:last-child{border-bottom:0}.difference-columns li strong{font-weight:500;font-size:13px}.difference-columns li small{color:var(--muted);font-size:12px}.difference-empty{padding:24px 12px;text-align:center;color:var(--muted);margin:0;font-size:12px}.difference-members{margin-top:14px;border:1px solid var(--line);border-radius:6px}.difference-members summary{padding:10px 12px;cursor:pointer;color:var(--accent)}.difference-members>div{padding:0 12px 12px;display:flex;flex-wrap:wrap;gap:6px;max-height:140px;overflow:auto}.difference-members>div>span{padding:5px 8px;background:#f5f7fa;border-radius:4px;overflow-wrap:anywhere}.difference-members small{display:block;font-size:11px;color:var(--muted)}.difference-note{margin:12px 0 0;font-size:12px;color:var(--muted);line-height:1.6}@media(max-width:480px){.difference-columns{grid-template-columns:1fr}.difference-metrics{gap:6px}.difference-metrics>div{padding:10px 8px}.difference-metrics strong{font-size:20px}.difference-columns ul{max-height:180px}}
</style>
