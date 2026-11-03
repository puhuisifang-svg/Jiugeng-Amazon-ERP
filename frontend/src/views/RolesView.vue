<script setup lang="ts">
import PermissionBrowser from '@/components/PermissionBrowser.vue'
import { vSingleSelect } from '@/directives/singleSelect'
import { pageTitles } from '@/state/pageTitles'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter, onBeforeRouteUpdate, onBeforeRouteLeave } from 'vue-router'
import { rolesApi, type Role, type Permission, type RoleEdit, type PermissionPreview, type RoleHistory } from '@/api/roles'
import { usersApi, type AppUser } from '@/api/auth'
import { session } from '@/state/session'
import { confirmAction, promptReason } from '@/state/uiConfirmation'
import { actionLabel } from '@/utils/auditLabels'
import TableViewport from '@/components/TableViewport.vue'
import ListPagination from '@/components/ListPagination.vue'
import FilterMultiSelect from '@/components/FilterMultiSelect.vue'
import AccessDialog from '@/components/AccessDialog.vue'
import PermissionDifference from '@/components/PermissionDifference.vue'
import UserRolesEditor from '@/components/UserRolesEditor.vue'
import SuccessToast from '@/components/SuccessToast.vue'
const route = useRoute(), router = useRouter()
const list = ref<Role[]>([]), catalog = ref<Permission[]>([]), error = ref(''), success = ref(''), busy = ref(false)
const query = ref(''), statuses = ref<string[]>([]), kinds = ref<string[]>([]), applied = ref({ q: '', statuses: [] as string[], kinds: [] as string[] }), page = ref(1), size = ref(20)
const filtered = computed(() => list.value.filter(r => r.name.includes(applied.value.q) && (!applied.value.statuses.length || applied.value.statuses.includes(r.status)) && (!applied.value.kinds.length || applied.value.kinds.includes(r.kind))).sort((a, b) => Number(b.id === 'ADMIN') - Number(a.id === 'ADMIN')))
const visible = computed(() => filtered.value.slice((page.value - 1) * size.value, page.value * size.value))
const current = computed(() => list.value.find(r => r.id === route.params.id))
const tabs = ref('permissions'), memberList = ref<AppUser[]>([]), history = ref<RoleHistory[]>([]), assignment = ref<AppUser>()
const draft = ref<RoleEdit>({ name: '', description: '', status: 'DRAFT', permissions: [], version: 0, reason: '' }), initial = ref('')
const dirty = computed(() => !!current.value && JSON.stringify(draft.value) !== initial.value)
const preview = ref<PermissionPreview>()
const createOpen = ref(false), newName = ref(''), newDescription = ref(''), copyFrom = ref<string>(), createError = ref('')
const createDialog = ref<InstanceType<typeof AccessDialog>>()
const addMember = ref(false), allUsers = ref<AppUser[]>([]), memberQuery = ref('')
const eligibleMembers = computed(() => allUsers.value.filter(u => {
  const role = current.value
  if (!role || u.id === session.user.value?.id || memberList.value.some(m => m.id === u.id) || u.roles?.some(r => r.id === role.id)) return false
  if (role.id === 'ADMIN' ? !!u.roles?.length : u.roles?.some(r => r.id === 'ADMIN')) return false
  return `${u.loginName} ${u.displayName}`.toLowerCase().includes(memberQuery.value.trim().toLowerCase())
}))
const readonly = computed(() => current.value?.id === 'ADMIN')
const changed = computed(() => ({ added: draft.value.permissions.filter(p => !current.value?.permissions.includes(p)).length, removed: current.value?.permissions.filter(p => !draft.value.permissions.includes(p)).length ?? 0 }))
const statusLabel = (s: string) => ({ ENABLED: '启用', DISABLED: '停用', DRAFT: '草稿' }[s] ?? s)
const label = (code: string) => catalog.value.find(p => p.code === code)?.label || code
const fail = (e: unknown) => e instanceof Error ? e.message : '操作失败，请重试'
function setDraft() { if (current.value) { draft.value = { name: current.value.name, description: current.value.description, status: current.value.status, permissions: [...current.value.permissions], version: current.value.version, reason: '' }; initial.value = JSON.stringify(draft.value) } }
async function load() { busy.value = true; error.value = ''; try { [list.value, catalog.value] = await Promise.all([rolesApi.list(), rolesApi.permissions()]); setDraft(); if (current.value) { pageTitles[route.path] = '详情'; [memberList.value, history.value] = await Promise.all([rolesApi.members(current.value.id), rolesApi.history(current.value.id)]) } } catch (e) { error.value = fail(e) } finally { busy.value = false } }
function filter() { applied.value = { q: query.value.trim(), statuses: [...statuses.value], kinds: [...kinds.value] }; page.value = 1 }
function reset() { query.value = ''; statuses.value = []; kinds.value = []; filter() }
function create(role?: Role) { createError.value = ''; newName.value = role ? `${role.name} 副本` : ''; newDescription.value = role?.description ?? ''; copyFrom.value = role?.id; createOpen.value = true }
async function createRole() { busy.value = true; createError.value = ''; try { const r = await rolesApi.create({ name: newName.value, description: newDescription.value, copyFrom: copyFrom.value }); createOpen.value = false; await router.push(`/system/roles/${r.id}`); await load(); tabs.value = 'basics'; success.value = '角色草稿已创建，请配置权限后启用。' } catch (e) { createError.value = fail(e) } finally { busy.value = false } }
function addDependencies(values: string[]) { const next = new Set(values); let change = true; while (change) { change = false; for (const p of catalog.value) if (next.has(p.code)) for (const dependency of p.dependencies) if (!next.has(dependency)) { next.add(dependency); change = true } } return [...next] }
async function toggle(code: string) {
  if (readonly.value || busy.value) return
  if (!draft.value.permissions.includes(code)) { draft.value.permissions = addDependencies([...draft.value.permissions, code]); return }
  const removed = new Set([code]); let change = true
  while (change) { change = false; for (const p of catalog.value) if (draft.value.permissions.includes(p.code) && p.dependencies.some(d => removed.has(d)) && !removed.has(p.code)) { removed.add(p.code); change = true } }
  if (removed.size > 1 && !await confirmAction(`同时移除依赖权限：${[...removed].map(label).join('、')}？`)) return
  draft.value.permissions = draft.value.permissions.filter(p => !removed.has(p))
}
async function selectPermissions(codes: string[]) { const all=codes.every(code=>draft.value.permissions.includes(code)); if(all) { for(const code of codes) if(draft.value.permissions.includes(code)) await toggle(code) } else draft.value.permissions=addDependencies([...draft.value.permissions,...codes]) }

async function review() { if (!current.value) return; busy.value = true; error.value = ''; try { preview.value = await rolesApi.preview(current.value.id, draft.value) } catch (e) { error.value = fail(e) } finally { busy.value = false } }
async function save() { if (!current.value) return; busy.value = true; error.value = ''; try { await rolesApi.save(current.value.id, draft.value); preview.value = undefined; await load(); success.value = '角色已保存；权限或状态变更已对成员生效。' } catch (e) { error.value = fail(e); preview.value = undefined } finally { busy.value = false } }
async function remove(role: Role) { const reason = await promptReason(`删除“${role.name}”？历史记录会保留。请填写原因。`); if (!reason) return; busy.value = true; try { await rolesApi.remove(role.id, role.version, reason); await load(); success.value = '角色已删除。' } catch (e) { error.value = fail(e) } finally { busy.value = false } }
async function reload() { if (dirty.value && !await confirmAction('放弃未保存的角色修改并重新加载？')) return; await load() }
const roleTabs = computed(() => [{ key: 'basics', label: '基本信息' }, { key: 'permissions', label: '权限配置' }, { key: 'members', label: `角色成员（${current.value?.memberCount ?? 0}）` }, { key: 'history', label: '变更记录' }])
function selectTab(key: string) { tabs.value = key }
function tabKeydown(event: KeyboardEvent, key: string) {
  const keys = roleTabs.value.map(t => t.key), index = keys.indexOf(key)
  const next = event.key === 'ArrowRight' ? (index + 1) % keys.length : event.key === 'ArrowLeft' ? (index + keys.length - 1) % keys.length : event.key === 'Home' ? 0 : event.key === 'End' ? keys.length - 1 : -1
  if (next < 0) return
  event.preventDefault(); selectTab(keys[next]!); (event.currentTarget as HTMLElement).parentElement?.querySelectorAll<HTMLButtonElement>('[role=tab]')[next]?.focus()
}
async function chooseMember() { try { allUsers.value = await usersApi.list(); memberQuery.value = ''; addMember.value = true } catch (e) { error.value = fail(e) } }
function editMember(user: AppUser) { addMember.value = false; assignment.value = user }
watch(() => route.params.id, () => { tabs.value = route.query.tab === 'members' ? 'members' : 'permissions'; void load() })
watch([size, filtered], () => { page.value = Math.max(1, Math.min(page.value, Math.ceil(filtered.value.length / size.value))) })
onBeforeRouteUpdate(async () => !dirty.value || await confirmAction('放弃未保存的角色权限修改？'))
onBeforeRouteLeave(async () => !dirty.value || await confirmAction('放弃未保存的角色权限修改？'))
onMounted(() => { tabs.value = route.query.tab === 'members' ? 'members' : 'permissions'; void load() })
</script>
<template>
  <!-- eslint-disable vue/max-attributes-per-line, vue/singleline-html-element-content-newline -->
  <section class="page-stack access-page roles-page">
    <p v-if="error" role="alert" class="data-state data-state--error">{{ error }} <button :disabled="busy" @click="reload">重新加载</button></p><SuccessToast :message="success" />
    <template v-if="!route.params.id">
      <form class="panel filter-panel" @submit.prevent="filter"><label>角色名称<input v-model="query" placeholder="搜索角色名称"></label><label>类型<FilterMultiSelect v-model="kinds" label="类型" :options="[{ value: 'BUILTIN', label: '系统预置' }, { value: 'CUSTOM', label: '自定义' }]" /></label><label>状态<FilterMultiSelect v-model="statuses" label="状态" :options="['DRAFT','ENABLED','DISABLED'].map(s => ({ value:s, label:statusLabel(s) }))" /></label><button type="submit">查询</button><button type="button" class="filter-reset-button" @click="reset">清空</button></form>
      <div class="material-toolbar"><button :disabled="busy" @click="create()">新增角色</button><span class="access-muted">配置岗位职责，再为用户分配角色</span></div>
      <div class="panel table-panel"><TableViewport><table class="data-table ui-action-table"><thead><tr><th>角色名称</th><th>类型</th><th>状态</th><th>成员数</th><th>权限数</th><th>说明</th><th>更新时间</th><th>操作</th></tr></thead><tbody><tr v-for="role in visible" :key="role.id"><td><RouterLink :to="`/system/roles/${role.id}`">{{ role.name }}</RouterLink></td><td>{{ role.kind === 'BUILTIN' ? '系统预置' : '自定义' }}</td><td>{{ statusLabel(role.status) }}</td><td><RouterLink :to="`/system/roles/${role.id}?tab=members`">{{ role.memberCount }}</RouterLink></td><td>{{ role.permissions.length }}</td><td>{{ role.description || '—' }}</td><td>{{ new Date(role.updatedAt).toLocaleString() }}</td><td class="table-actions"><RouterLink :to="`/system/roles/${role.id}`">详情</RouterLink><button v-if="role.id !== 'ADMIN'" :disabled="busy" @click="create(role)">复制</button><button v-if="role.id !== 'ADMIN'" :disabled="busy || !!role.memberCount" :title="role.memberCount ? '请先解除成员绑定' : '删除角色'" @click="remove(role)">删除</button></td></tr><tr v-if="!visible.length"><td colspan="8" class="data-state">{{ busy ? '正在加载…' : '暂无角色' }}</td></tr></tbody></table></TableViewport></div>
      <ListPagination v-model:page="page" v-model:page-size="size" :total="filtered.length" :disabled="busy" />
    </template>
    <template v-else-if="current">
      <div class="panel role-heading"><strong>{{ current.name }}</strong><span class="access-tag">{{ statusLabel(current.status) }}</span><span class="access-muted">{{ readonly ? '系统管理员受保护，不可修改或删除' : current.kind === 'BUILTIN' ? '系统预置业务角色，可直接修改权限' : '自定义角色' }}</span><button v-if="current.id !== 'ADMIN'" @click="create(current)">复制角色</button></div>
      <div class="panel role-editor">
        <nav class="role-tabs" role="tablist" aria-label="角色详情">
          <button v-for="tab in roleTabs" :id="`role-tab-${tab.key}`" :key="tab.key" type="button" role="tab" :aria-selected="tabs === tab.key" :aria-controls="`role-panel-${tab.key}`" :tabindex="tabs === tab.key ? 0 : -1" @click="selectTab(tab.key)" @keydown="tabKeydown($event, tab.key)">{{ tab.label }}</button>
        </nav>
        <div :id="`role-panel-${tabs}`" role="tabpanel" :aria-labelledby="`role-tab-${tabs}`" tabindex="0">
          <section v-if="tabs==='basics'" class="role-profile" aria-label="角色基本信息">
            <div class="role-section-intro"><h3>基本信息</h3><p>设置岗位名称与使用状态；具体功能权限请在“权限配置”中维护。</p></div>
            <div class="role-profile-fields"><label>角色名称 <span>必填</span><input v-model="draft.name" maxlength="50" placeholder="例如：采购专员" :disabled="readonly || busy"></label><label>角色状态<select v-model="draft.status" v-single-select :disabled="readonly || busy"><option v-if="current.status==='DRAFT'" value="DRAFT">草稿</option><option value="ENABLED">启用</option><option value="DISABLED">停用</option></select></label><label class="role-profile-description">角色说明 <span>选填</span><textarea v-model="draft.description" maxlength="200" rows="3" placeholder="说明该角色适用的岗位与职责" :disabled="readonly || busy" /><small>{{ draft.description.length }} / 200</small></label></div>
            <p class="role-state-note">启用后可分配给成员；停用会影响成员通过该角色获得的权限。保存前可预览变更。</p>
          </section>
          <template v-if="tabs==='permissions'">
            <PermissionBrowser :catalog="catalog" :selected="draft.permissions" :readonly="readonly" :busy="busy" @toggle="toggle" @select="selectPermissions" />
            <p class="permission-scope-note">同一权限可能被多个菜单共用，勾选后同时生效；财务查看权限覆盖全部店铺。</p>
            <label v-if="!readonly" class="role-reason">变更原因（选填）<input v-model="draft.reason" maxlength="200" placeholder="补充本次调整原因，最多200字" :disabled="busy"></label>
          </template>
          <template v-if="tabs==='members'"><div class="role-members-toolbar"><button class="role-member-add" :disabled="busy || dirty || current.status!=='ENABLED'" :title="dirty ? '请先保存或取消权限修改' : current.status!=='ENABLED' ? '启用角色后可添加成员' : '添加成员'" @click="chooseMember"><span aria-hidden="true">＋</span>添加成员</button><span>仅管理当前角色的成员关系</span></div><table class="data-table"><thead><tr><th>用户</th><th>角色</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="user in memberList" :key="user.id"><td>{{ user.displayName }} <small>{{ user.loginName }}</small></td><td>{{ user.roles?.map(r=>r.name).join('、') }}</td><td>{{ user.status==='ENABLED'?'启用':'停用' }}</td><td><button class="role-member-remove" :disabled="busy || dirty || user.id===session.user.value?.id" @click="editMember(user)">移除成员</button></td></tr><tr v-if="!memberList.length"><td colspan="4">暂无成员</td></tr></tbody></table></template>
          <template v-if="tabs==='history'"><div v-for="event in history" :key="event.id" class="role-history"><strong>{{ actionLabel(event.action) }}</strong><span>{{ event.actorDisplayName }} · {{ new Date(event.occurredAt).toLocaleString() }}</span><p>{{ event.detail }}</p><details><summary>查看变更快照</summary><pre>{{ JSON.stringify({before:event.beforeValue,after:event.afterValue},null,2) }}</pre></details></div><p v-if="!history.length" class="data-state">暂无变更记录</p></template>
        </div>
      </div>
      <div v-if="!readonly && (tabs==='permissions' || tabs==='basics')" class="role-savebar">
        <div class="role-save-summary" aria-live="polite"><strong>{{ dirty ? '有未保存的修改' : '暂无修改' }}</strong><span v-if="changed.added || changed.removed">新增 {{ changed.added }} 项 · 移除 {{ changed.removed }} 项权限</span></div>
        <div class="role-save-actions"><button :disabled="busy || !dirty" @click="reload">取消修改</button><button class="role-save-primary" :disabled="busy || !dirty" :aria-busy="busy" @click="review"><svg aria-hidden="true" viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="1.8"><path d="m5 12 4 4L19 6" /></svg>{{ busy?'处理中…':'保存变更' }}</button></div>
      </div>
    </template><p v-else class="data-state">{{ busy?'正在加载…':'角色不存在或已删除' }}</p>
    <AccessDialog v-if="createOpen" ref="createDialog" dialog-class="role-create-dialog" :title="copyFrom?'复制角色':'新增角色'" :dirty="!!newName || !!newDescription" :busy="busy" @close="createOpen=false">
      <div class="role-create-intro"><span class="role-create-icon" aria-hidden="true">＋</span><div><strong>{{ copyFrom?'基于现有角色创建副本':'创建一个岗位角色' }}</strong><p>填写基本信息后，继续配置权限并分配成员。</p></div></div>
      <form id="create-role" class="access-form role-create-form" @submit.prevent="createRole"><p v-if="createError" role="alert" class="data-state--error">{{ createError }}</p><label>角色名称 <input v-model="newName" minlength="2" maxlength="50" placeholder="例如：采购专员、财务专员" :disabled="busy" required><small>2–50 个字，使用清晰的岗位名称</small></label><label>角色说明（选填）<textarea v-model="newDescription" maxlength="200" rows="3" placeholder="描述岗位职责与适用范围" :disabled="busy" /><small>{{ newDescription.length }} / 200</small></label><p class="role-state-note">初始状态为草稿，配置权限并启用后才可分配成员。</p></form>
      <template #footer><button :disabled="busy" @click="createDialog?.requestClose()">取消</button><button type="submit" form="create-role" :disabled="busy" class="primary-button">{{ busy?'创建中…':'创建' }}</button></template>
    </AccessDialog>
    <AccessDialog v-if="preview && current" dialog-class="role-review-dialog" title="确认角色变更" :busy="busy" @close="preview=undefined">
      <div class="role-review-heading"><strong>{{ draft.name }}</strong><span class="access-tag">{{ statusLabel(draft.status) }}</span><p>请核对本次修改，确认后统一保存基本信息与权限。</p></div>
      <dl v-if="draft.name!==current.name || draft.status!==current.status || draft.description!==current.description" class="role-basic-diff"><template v-if="draft.name!==current.name"><dt>角色名称</dt><dd><del>{{ current.name }}</del><span>→</span><strong>{{ draft.name }}</strong></dd></template><template v-if="draft.status!==current.status"><dt>角色状态</dt><dd><del>{{ statusLabel(current.status) }}</del><span>→</span><strong>{{ statusLabel(draft.status) }}</strong></dd></template><template v-if="draft.description!==current.description"><dt>角色说明</dt><dd><del>{{ current.description || '未填写' }}</del><span>→</span><strong>{{ draft.description || '未填写' }}</strong></dd></template></dl>
      <PermissionDifference :preview="preview" :catalog="catalog" />
      <template #footer><button :disabled="busy" @click="preview=undefined">返回修改</button><button class="primary-button" :disabled="busy" @click="save">{{ busy?'保存中…':'确认保存' }}</button></template>
    </AccessDialog>
    <AccessDialog v-if="addMember && current" :title="`添加成员 · ${current.name}`" dialog-class="member-picker-dialog" @close="addMember=false">
      <p class="member-picker-note">选择要加入当前角色的账号。已排除已有成员和角色不兼容的账号。</p>
      <div class="member-picker-search"><input v-model="memberQuery" placeholder="搜索登录名或姓名" aria-label="搜索成员"><span>{{ eligibleMembers.length }} 个可选账号</span></div>
      <div class="member-picker-list"><table class="data-table"><thead><tr><th>用户</th><th>已有角色</th><th>账号状态</th><th>操作</th></tr></thead><tbody><tr v-for="u in eligibleMembers" :key="u.id"><td><strong>{{ u.displayName }}</strong><small>{{ u.loginName }}</small></td><td>{{ u.roles?.map(r=>r.name).join('、') || '暂无角色' }}</td><td>{{ u.status==='ENABLED'?'启用':'停用' }}</td><td><button class="member-picker-select" @click="editMember(u)">选择</button></td></tr><tr v-if="!eligibleMembers.length"><td colspan="4" class="data-state">没有可添加的用户</td></tr></tbody></table></div>
      <template #footer><button class="assignment-secondary" @click="addMember=false">取消</button></template>
    </AccessDialog>
    <UserRolesEditor v-if="assignment && current" :user="assignment" :roles="list" :catalog="catalog" :scoped-role="current" @close="assignment=undefined" @saved="assignment=undefined;load()" />
  </section>
</template>
<style scoped src="@/styles/rolePermissions.css"></style>
<style>
#app .roles-page .role-profile-fields .single-select-trigger{flex:0 0 100%;width:100%;min-height:40px;height:40px;box-sizing:border-box;font-size:13px}
.access-dialog.role-review-dialog{width:min(760px,calc(100vw - 24px))}.access-dialog.role-create-dialog{width:min(520px,calc(100vw - 24px))}
</style>
