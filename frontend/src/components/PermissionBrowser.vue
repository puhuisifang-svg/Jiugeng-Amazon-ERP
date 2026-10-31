<script setup lang="ts">
import { computed,ref } from 'vue'
import { appRoutes } from '@/router'
import { navigationDomains } from '@/config/navigation'
import { buildPermissionMenu } from '@/utils/permissionMenu'
import type { Permission,RoleRef } from '@/api/roles'
const props=withDefaults(defineProps<{catalog:Permission[];selected:string[];readonly?:boolean;busy?:boolean;sources?:Record<string,RoleRef[]> | undefined}>(),{readonly:false,busy:false,sources:undefined})
const emit=defineEmits<{toggle:[string];select:[string[]]}>()
const search=ref(''),selectedModule=ref(''),selectedOnly=ref(false)
const label=(code:string)=>props.catalog.find(p=>p.code===code)?.label || code
const menuGroups = computed(() => buildPermissionMenu(appRoutes.find(r => r.path === '/')?.children ?? [], props.catalog))
const collapsedGroups = ref<string[]>(navigationDomains.map(g => `domain:${g.title}`))
const activeMenu = computed(() => menuGroups.value.flatMap(g => [g, ...g.children]).find(n => n.key === selectedModule.value))
const activeMenuTitle = computed(() => {
  if (!selectedModule.value) return '全部权限'
  const group = menuGroups.value.find(g => g.key === selectedModule.value || g.children.some(c => c.key === selectedModule.value))
  return group?.key === selectedModule.value ? group.title : `${group?.title} / ${activeMenu.value?.title}`
})
const matchingPermissions = computed(() => props.catalog.filter(p => (!selectedOnly.value || props.selected.includes(p.code)) && `${p.label} ${p.code} ${p.module}`.toLowerCase().includes(search.value.trim().toLowerCase())))
const modulePermissions = computed(() => {
  const codes = activeMenu.value?.codes ?? [...new Set(menuGroups.value.flatMap(g=>g.codes))]
  return codes.flatMap(code => matchingPermissions.value.filter(p => p.code === code))
})
const selectablePermissions = computed(() => modulePermissions.value.filter(p => p.assignable))
const allSelected = computed(() => selectablePermissions.value.length > 0 && selectablePermissions.value.every(p => props.selected.includes(p.code)))
const someSelected = computed(() => selectablePermissions.value.some(p => props.selected.includes(p.code)))
function matchingCount(codes: string[]) { return matchingPermissions.value.filter(p=>codes.includes(p.code)).length }
function toggleGroup(key: string) { collapsedGroups.value = collapsedGroups.value.includes(key) ? menuGroups.value.filter(g=>g.key!==key).map(g=>g.key) : [...collapsedGroups.value,key] }
function selectGroup(key: string) { selectedModule.value = key; collapsedGroups.value = menuGroups.value.filter(g=>g.key!==key).map(g=>g.key) }
</script>
<template>
  <!-- eslint-disable vue/max-attributes-per-line, vue/singleline-html-element-content-newline -->
  <div class="role-editor permission-browser">
    <div class="role-search">
      <div class="permission-search-box"><svg aria-hidden="true" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="10.5" cy="10.5" r="6.5" /><path d="m16 16 5 5" /></svg><input v-model="search" type="search" aria-label="搜索权限" placeholder="搜索功能、权限名称"><button v-if="search" type="button" aria-label="清空权限搜索" @click="search=''">×</button></div>
      <label><input v-model="selectedOnly" type="checkbox">只看已选</label><span class="access-muted">已选 {{ selected.length }} 项权限</span>
    </div>
    <div class="permission-layout">
      <aside class="permission-navigation" aria-label="按系统菜单选择权限">
        <button class="permission-all-menu" :class="{active:!selectedModule}" :aria-pressed="!selectedModule" @click="selectedModule=''">全部权限<span>{{ matchingPermissions.length }}</span></button>
        <div class="permission-menu-scroll" tabindex="0" aria-label="权限菜单，可上下滚动">
          <section v-for="group in menuGroups" :key="group.key" class="permission-menu-group">
            <div class="permission-group-heading"><button class="permission-group-toggle" :aria-label="`${collapsedGroups.includes(group.key)?'展开':'收起'}${group.title}`" :aria-expanded="!collapsedGroups.includes(group.key)" @click="toggleGroup(group.key)">{{ collapsedGroups.includes(group.key) ? '›' : '⌄' }}</button><button :class="{active:selectedModule===group.key}" :aria-pressed="selectedModule===group.key" @click="selectGroup(group.key)">{{ group.title }}<span>{{ matchingCount(group.codes) }}</span></button></div>
            <div v-if="!collapsedGroups.includes(group.key)" class="permission-menu-children"><button v-for="child in group.children" :key="child.key" :class="{active:selectedModule===child.key}" :aria-pressed="selectedModule===child.key" @click="selectedModule=child.key"><span>{{ child.title }}</span><small>{{ matchingCount(child.codes) }}</small></button></div>
          </section>
        </div>
      </aside>
      <div class="permission-content">
        <div class="permission-content-heading"><strong>{{ activeMenuTitle }}</strong><span>{{ modulePermissions.length }} 项权限</span></div>
        <label v-if="!readonly" class="permission-all"><input type="checkbox" :checked="allSelected" :indeterminate="someSelected && !allSelected" :disabled="busy || !selectablePermissions.length" @change="emit('select', selectablePermissions.map(p=>p.code))">全选当前结果</label>
        <div class="permission-list" tabindex="0" aria-label="权限明细，可上下滚动">
          <label v-for="permission in modulePermissions" :key="permission.code" :title="permission.code" class="permission-row"><input type="checkbox" :checked="selected.includes(permission.code)" :disabled="readonly || busy || !permission.assignable" @change="emit('toggle',permission.code)"><span><strong>{{ permission.label }}</strong><small>{{ permission.module }}<template v-if="permission.dependencies.length"> · 依赖：{{ permission.dependencies.map(label).join('、') }}</template></small></span><small v-if="sources" class="permission-source">授权来源：{{ sources[permission.code]?.map(r=>r.name).join('、') || '登录基础能力' }}</small><em v-if="permission.sensitive">敏感</em><small v-if="!permission.assignable">{{ permission.code==='audit.read.self' ? '登录基础能力' : '仅系统管理员' }}</small></label><p v-if="!modulePermissions.length" class="data-state">当前菜单下没有匹配的权限</p>
        </div>
      </div>
    </div>
  </div>
</template>
<style scoped src="@/styles/rolePermissions.css"></style>
<style scoped>
.permission-browser{padding:0}.permission-source{max-width:200px;color:var(--muted);font-size:12px}.permission-row:has(.permission-source){flex-wrap:wrap}
</style>
