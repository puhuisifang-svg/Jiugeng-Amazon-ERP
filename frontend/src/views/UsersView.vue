<script setup lang="ts">
import { vPhone } from '@/directives/phone'
import { beijingDateTime } from '@/utils/dateTime'


import { computed, onMounted, ref, watch } from 'vue'
import PermissionBrowser from '@/components/PermissionBrowser.vue'
import { usersApi, userDefaultsApi, type AppUser } from '@/api/auth'
import { rolesApi, type Role, type Permission, type RoleRef, type RoleHistory } from '@/api/roles'
import { session } from '@/state/session'
import { runtimeConfig } from '@/config/runtime'
import { confirmAction } from '@/state/uiConfirmation'
import { actionLabel } from '@/utils/auditLabels'
import RowMoreActions from '@/components/RowMoreActions.vue'
import TableViewport from '@/components/TableViewport.vue'
import ListPagination from '@/components/ListPagination.vue'
import FilterMultiSelect from '@/components/FilterMultiSelect.vue'
import PasswordField from '@/components/PasswordField.vue'
import AccessDialog from '@/components/AccessDialog.vue'
import UserRolesEditor from '@/components/UserRolesEditor.vue'
import SuccessToast from '@/components/SuccessToast.vue'
const users = ref<AppUser[]>([]), roles = ref<Role[]>([]), catalog = ref<Permission[]>([])
const loading = ref(false), error = ref(''), success = ref(''), editorError = ref('')
const q = ref(''), selectedRoles = ref<string[]>([]), selectedStatuses = ref<string[]>([])
const applied = ref({ q: '', roles: [] as string[], statuses: [] as string[] }), page = ref(1), size = ref(20)
const filtered = computed(() => users.value.filter(u => `${u.loginName} ${u.displayName}`.toLowerCase().includes(applied.value.q.toLowerCase()) && (!applied.value.roles.length || u.roles?.some(r => applied.value.roles.includes(r.id))) && (!applied.value.statuses.length || applied.value.statuses.includes(u.status))))
const visible = computed(() => filtered.value.slice((page.value - 1) * size.value, page.value * size.value))
const assignment = ref<AppUser>(), detail = ref<AppUser>(), detailTab = ref('profile'), sources = ref<Record<string, RoleRef[]>>({}), history = ref<RoleHistory[]>([])
const editor = ref<'create' | 'profile' | 'password'>(), target = ref<AppUser>(), initial = ref('')
const form = ref({ loginName: '', displayName: '', email: '', phone: '', initialPassword: '', roleIds: [] as string[] })
const dirty = computed(() => JSON.stringify(form.value) !== initial.value)
const historyPage=ref(1), historySize=ref(10), historyTotal=ref(0), historyLoading=ref(false), historyError=ref('')
let historyRequest=0
const detailTabs=[{key:'profile',label:'基本资料'},{key:'permissions',label:'有效权限'},{key:'history',label:'操作记录'}]
function tabKeydown(event:KeyboardEvent,key:string) {
 const keys=detailTabs.map(t=>t.key),i=keys.indexOf(key)
 const next=event.key==='ArrowRight'?(i+1)%keys.length:event.key==='ArrowLeft'?(i+keys.length-1)%keys.length:event.key==='Home'?0:event.key==='End'?keys.length-1:-1
 if(next<0)return
 event.preventDefault();detailTab.value=keys[next]!;(event.currentTarget as HTMLElement).parentElement?.querySelectorAll<HTMLButtonElement>('[role=tab]')[next]?.focus()
}
async function loadHistory() {
 if(!detail.value)return
 const request=++historyRequest;historyLoading.value=true;historyError.value='';history.value=[]
 try { const result=await rolesApi.userHistory(detail.value.id,historyPage.value,historySize.value);if(request!==historyRequest)return;history.value=result.items;historyTotal.value=result.total }
 catch(e){if(request===historyRequest)historyError.value=fail(e)}finally{if(request===historyRequest)historyLoading.value=false}
}
watch([historyPage,historySize],()=>{if(detailTab.value==='history')void loadHistory()})
watch(detailTab,value=>{if(value==='history')void loadHistory()})
const fail = (e: unknown) => e instanceof Error ? e.message : '操作失败，请重试'
const time = (s?: string | null) => s ? beijingDateTime(s) : '—'
async function load() { if (runtimeConfig.authMode === 'mock') return; loading.value = true; error.value = ''; try { [users.value, roles.value, catalog.value] = await Promise.all([usersApi.list(), rolesApi.list(), rolesApi.permissions()]) } catch (e) { error.value = fail(e) } finally { loading.value = false } }
function search() { applied.value = { q: q.value.trim(), roles: [...selectedRoles.value], statuses: [...selectedStatuses.value] }; page.value = 1 }
function clear() { q.value = ''; selectedRoles.value = []; selectedStatuses.value = []; search() }
async function open(kind: 'create' | 'profile' | 'password', user?: AppUser) {
 let password='';editorError.value=''
 if(kind==='create') { loading.value=true; try { password=(await userDefaultsApi.get()).initialPassword } catch(e) { editorError.value=fail(e) } finally { loading.value=false } }
 target.value=user;form.value={loginName:user?.loginName??'',displayName:user?.displayName??'',email:user?.email??'',phone:user?.phone??'',initialPassword:password,roleIds:user?.roles?.map(r=>r.id)??[]}
 initial.value=JSON.stringify(form.value);editor.value=kind
}

function toggleRole(id: string) { if (form.value.roleIds.includes(id)) form.value.roleIds = form.value.roleIds.filter(r => r !== id); else form.value.roleIds = id === 'ADMIN' ? ['ADMIN'] : [...form.value.roleIds.filter(r => r !== 'ADMIN'), id] }
async function save() { loading.value = true; editorError.value = ''; try { if (editor.value === 'create') await rolesApi.createUser(form.value); else if (editor.value === 'profile' && target.value) await rolesApi.profile(target.value.id, form.value); else if (target.value) await usersApi.resetPassword(target.value.id, form.value.initialPassword); editor.value = undefined; success.value = '账号已保存。'; await load() } catch (e) { editorError.value = fail(e) } finally { loading.value = false } }
async function act(user: AppUser, action: 'enable' | 'disable' | 'remove') { const verb = { enable: '启用', disable: '停用', remove: '删除' }[action]; if (!await confirmAction(`确定${verb} ${user.loginName}？${['disable','remove'].includes(action) ? '原会话立即失效，历史业务及审计保留。' : ''}`)) return; loading.value = true; error.value = ''; try { await usersApi[action](user.id); await load(); success.value = `账号已${verb}。` } catch (e) { error.value = fail(e) } finally { loading.value = false } }
async function showDetail(user: AppUser) { error.value=''; loading.value=true; try { sources.value=await rolesApi.sources(user.id); historyRequest++; detail.value=user;detailTab.value='profile';historyPage.value=1;historySize.value=10;historyTotal.value=0;history.value=[] } catch(e){error.value=fail(e)}finally{loading.value=false} }

watch([size, filtered], () => { page.value = Math.max(1, Math.min(page.value, Math.ceil(filtered.value.length / size.value))) })
onMounted(load)
</script>
<template>
  <!-- eslint-disable vue/max-attributes-per-line, vue/singleline-html-element-content-newline -->
  <section class="page-stack access-page">
    <p v-if="runtimeConfig.authMode==='mock'" class="notice">模拟模式不维护真实账号与权限。</p>
    <template v-else>
      <form class="panel filter-panel" @submit.prevent="search"><label>账号/姓名<input v-model="q" placeholder="搜索登录名或姓名"></label><label>角色<FilterMultiSelect v-model="selectedRoles" label="角色" :options="roles.map(r=>({value:r.id,label:r.name}))" /></label><label>状态<FilterMultiSelect v-model="selectedStatuses" label="状态" :options="[{value:'ENABLED',label:'启用'},{value:'DISABLED',label:'停用'}]" /></label><button type="submit">查询</button><button type="button" class="filter-reset-button" @click="clear">清空</button></form>
      <div class="material-toolbar"><button :disabled="loading" @click="open('create')">新增用户</button><RouterLink to="/system/roles">管理角色与权限</RouterLink></div>
      <p v-if="error" role="alert" class="data-state data-state--error">{{ error }} <button :disabled="loading" @click="load">重试</button></p><SuccessToast :message="success" />
      <div class="panel table-panel"><TableViewport><table class="data-table ui-action-table"><thead><tr><th>登录名</th><th>显示名称</th><th>角色</th><th>账号状态</th><th>联系方式</th><th>最近登录</th><th>创建时间</th><th>操作</th></tr></thead><tbody><tr v-for="user in visible" :key="user.id"><td>{{ user.loginName }}</td><td>{{ user.displayName }}</td><td><div class="access-tags"><span v-for="role in (user.roles??[]).slice(0,2)" :key="role.id" class="access-tag">{{ role.name }}{{ role.status==='DISABLED'?'（停用）':'' }}</span><button v-if="(user.roles?.length??0)>2" @click="showDetail(user)">+{{ (user.roles?.length??0)-2 }}</button></div></td><td>{{ user.status==='ENABLED'?'启用':'停用' }}</td><td>{{ [user.email,user.phone].filter(Boolean).join(' / ')||'未设置' }}</td><td>{{ time(user.lastLoginAt) }}</td><td>{{ time(user.createdAt) }}</td><td class="table-actions"><button :disabled="loading" @click="showDetail(user)">详情</button><RowMoreActions :label="`${user.loginName} 更多操作`"><div class="user-actions"><button :disabled="loading" @click="open('profile',user)">编辑资料</button><template v-if="user.id!==session.user.value?.id"><button :disabled="loading" @click="assignment=user">分配角色</button><button :disabled="loading" @click="act(user,user.status==='ENABLED'?'disable':'enable')">{{ user.status==='ENABLED'?'停用':'启用' }}</button><button :disabled="loading" @click="open('password',user)">重置密码</button><button :disabled="loading" @click="act(user,'remove')">删除</button></template><span v-else class="access-muted">当前账号受保护</span></div></RowMoreActions></td></tr><tr v-if="!visible.length"><td colspan="8" class="data-state">{{ loading?'正在加载…':'没有符合条件的账号' }}</td></tr></tbody></table></TableViewport></div>
      <ListPagination v-model:page="page" v-model:page-size="size" :total="filtered.length" :disabled="loading" />
      <AccessDialog v-if="editor" :title="editor==='create'?'新增用户':editor==='profile'?'编辑用户资料':'重置密码'" :dirty="dirty" :busy="loading" @close="editor=undefined"><form id="user-editor" class="access-form" @submit.prevent="save"><p v-if="editorError" role="alert" class="data-state--error">{{ editorError }}</p><template v-if="editor!=='password'"><label>登录名<input v-model="form.loginName" :disabled="editor==='profile'" pattern="[A-Za-z0-9._-]{3,128}" required></label><label>显示名称<input v-model="form.displayName" maxlength="128" required></label><label>手机号<input v-model="form.phone" v-phone="'mobile'" type="tel" maxlength="16"></label></template><label v-if="editor!=='profile'">{{ editor==='create'?'初始密码':'临时密码' }}<PasswordField id="access-user-password" v-model="form.initialPassword" autocomplete="new-password" :minlength="6" required /></label><template v-if="editor==='create'"><strong>分配角色</strong><label v-for="role in roles.filter(r=>r.status==='ENABLED')" :key="role.id" class="role-choice"><input type="checkbox" :checked="form.roleIds.includes(role.id)" @change="toggleRole(role.id)">{{ role.name }}</label><p class="access-muted">初始密码可直接登录。多个角色权限取并集。</p></template><p v-if="editor==='password'" class="access-muted">原会话立即失效，下一次登录需修改临时密码。</p></form><template #footer><button type="submit" form="user-editor" class="primary-button" :disabled="loading || (editor==='create'&&!form.roleIds.length)">{{ loading?'保存中…':'保存' }}</button></template></AccessDialog>
      <UserRolesEditor v-if="assignment" :user="assignment" :roles="roles" :catalog="catalog" @close="assignment=undefined" @saved="assignment=undefined;load();success='角色分配已生效，相关账号需重新登录。'" />
      <AccessDialog v-if="detail" :title="`用户详情 · ${detail.displayName}`" wide @close="detail=undefined">
        <nav class="role-tabs" role="tablist" aria-label="用户详情"><button v-for="tab in detailTabs" :id="`user-tab-${tab.key}`" :key="tab.key" type="button" role="tab" :aria-selected="detailTab===tab.key" :aria-controls="`user-panel-${tab.key}`" :tabindex="detailTab===tab.key?0:-1" @click="detailTab=tab.key" @keydown="tabKeydown($event,tab.key)">{{ tab.label }}</button></nav><div :id="`user-panel-${detailTab}`" role="tabpanel" :aria-labelledby="`user-tab-${detailTab}`">
          <dl v-if="detailTab==='profile'" class="user-profile"><dt>登录名</dt><dd>{{ detail.loginName }}</dd><dt>角色</dt><dd>{{ detail.roles?.map(r=>r.name).join('、') }}</dd><dt>联系方式</dt><dd>{{ [detail.email,detail.phone].filter(Boolean).join(' / ')||'未设置' }}</dd><dt>账号状态</dt><dd>{{ detail.status==='ENABLED'?'启用':'停用' }}</dd><dt>密码状态</dt><dd>{{ detail.mustChangePassword?'需修改临时密码':'可正常登录' }}</dd></dl><PermissionBrowser v-if="detailTab==='permissions'" :catalog="catalog.filter(p=>detail?.capabilities.some(code=>code===p.code))" :selected="detail.capabilities" :sources="sources" readonly /><template v-if="detailTab==='history'"><p v-if="historyError" role="alert" class="data-state--error">{{ historyError }} <button @click="loadHistory">重试</button></p><p v-if="historyLoading" class="data-state">正在加载…</p><article v-for="event in history" :key="event.id" class="user-history"><strong>{{ actionLabel(event.action) }}</strong><span>{{ time(event.occurredAt) }} · {{ event.actorDisplayName }}</span><p>{{ event.detail }}</p><details><summary>变更快照</summary><pre>{{ JSON.stringify({before:event.beforeValue,after:event.afterValue},null,2) }}</pre></details></article><p v-if="!history.length && !historyLoading && !historyError" class="data-state">暂无记录</p></template>
        </div>
        <template v-if="detailTab==='history'" #pagination>
          <ListPagination v-model:page="historyPage" v-model:page-size="historySize" :total="historyTotal" :disabled="historyLoading" />
        </template>
      </AccessDialog>
    </template>
  </section>
</template>
<style scoped>
.user-actions{display:flex;flex-wrap:wrap;gap:4px;max-width:250px;padding:6px 0}.table-actions details{display:inline-block}.table-actions summary{cursor:pointer;color:#2864c5}.user-profile{display:grid;grid-template-columns:100px 1fr;gap:12px;font-size:13px}.user-profile dt{color:var(--muted)}.user-profile dd{margin:0}.user-history{padding:10px 0;border-bottom:1px solid var(--line);font-size:13px}.user-history span{display:block;color:var(--muted);margin-top:4px}.user-history pre{white-space:pre-wrap;overflow-wrap:anywhere}
.role-tabs{position:sticky;top:-14px;z-index:2;background:white;display:flex;gap:24px;border-bottom:1px solid var(--line);margin-bottom:12px;overflow-x:auto}
.role-tabs button{flex:none;border:0;border-bottom:2px solid transparent;border-radius:0;min-height:36px;padding:0 2px;background:transparent;color:var(--muted);font-size:13px;white-space:nowrap}
.role-tabs button[aria-selected=true]{color:#2864c5;border-bottom-color:#2864c5;font-weight:600}.role-tabs button:focus-visible{outline:2px solid #2864c5;outline-offset:-2px}
</style>
