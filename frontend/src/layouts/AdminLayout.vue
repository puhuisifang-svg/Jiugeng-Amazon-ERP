<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'

import { appRoutes } from '@/router'
import { session } from '@/state/session'

const route = useRoute()
const router = useRouter()
const navigation = ref<HTMLElement>()
const content = ref<HTMLElement>()
const openDomain = ref('')
const visited = ref<string[]>([])
const definitions = [
  { title: '工作台', icon: '⌂', groups: ['工作台', '工作台维护'] },
  { title: '商品', icon: '◇', groups: ['产品管理'] },
  { title: '发货', icon: '↗', groups: ['发货管理'] },
  { title: '客户', icon: '客', groups: ['客户管理'] },
  { title: '合规', icon: '✓', groups: ['报关管理'] },
  { title: '选品', icon: '☆', groups: ['选品管理'] },
  { title: '设置', icon: '⚙', groups: ['系统管理'] },
]
const entries = computed(() => (appRoutes.find(item => item.path === '/')?.children ?? [])
  .filter(item => !item.meta?.hidden && session.canAccess(item.meta?.capabilities)))
const domains = computed(() => definitions.map(domain => ({
  ...domain,
  rows: domain.groups.map(group => ({ group, items: entries.value.filter(item => item.meta?.group === group) }))
    .filter(row => row.items.length),
})).filter(domain => domain.rows.length))
const currentEntry = computed(() => entries.value.find(item => `/${item.path}` === route.path)
  ?? entries.value.filter(item => route.path.startsWith(`/${item.path}/`)).sort((a, b) => b.path.length - a.path.length)[0]
  ?? entries.value.find(item => `/${item.path}` === route.meta.parentPath)
  ?? (route.path.startsWith('/inventory/') ? entries.value.find(item => item.path === 'materials') : undefined))
const activeDomain = computed(() => domains.value.find(domain => domain.rows.some(row => row.items.includes(currentEntry.value!)))?.title)
const breadcrumbs = computed(() => route.meta.breadcrumbs ?? [
  { title: activeDomain.value ?? '工作台' },
  { title: String(route.meta.title) },
])
const home = computed(() => entries.value.find(item => item.path === 'home'))
const tabs = computed(() => visited.value.map(path => entries.value.find(item => item.path === path)).filter(item => item !== undefined))
const expanded = computed(() => domains.value.find(domain => domain.title === openDomain.value))

watch([() => route.fullPath, entries], () => {
  openDomain.value = ''
  visited.value = visited.value.filter(path => entries.value.some(item => item.path === path))
  const entry = currentEntry.value
  if (entry && entry.path !== 'home' && !visited.value.includes(entry.path)) {
    visited.value = [...visited.value, entry.path].slice(-10)
  }
}, { immediate: true })

// The workspace, rather than the document, owns page scrolling.
watch(() => route.fullPath, () => {
  if (content.value) {
    content.value.scrollTop = 0
    content.value.scrollLeft = 0
  }
}, { flush: 'post' })

function closeMenu(restoreFocus = false) {
  const title = openDomain.value
  openDomain.value = ''
  if (restoreFocus) navigation.value?.querySelector<HTMLButtonElement>(`[data-domain="${title}"]`)?.focus()
}
function onOutside(event: PointerEvent) {
  if (event.target instanceof Node && !navigation.value?.contains(event.target)) closeMenu()
}
function onEscape(event: KeyboardEvent) {
  if (event.key === 'Escape' && openDomain.value) closeMenu(true)
}
async function focusPanel(title: string) {
  openDomain.value = title
  await nextTick()
  navigation.value?.querySelector<HTMLAnchorElement>('.mega-panel a')?.focus()
}
function onFocusOut(event: FocusEvent) {
  if (event.relatedTarget instanceof Node && !navigation.value?.contains(event.relatedTarget)) closeMenu()
}
async function closeTab(path: string) {
  if (currentEntry.value?.path === path) {
    const remaining = tabs.value.filter(item => item.path !== path)
    const target = remaining.at(-1) ?? home.value ?? entries.value.find(item => item.path !== path)
    if (!target) return
    await router.push(`/${target.path}`)
  }
  visited.value = visited.value.filter(item => item !== path)
}
async function logout() {
  await session.logout()
  await router.replace('/login')
}
onMounted(() => {
  document.addEventListener('pointerdown', onOutside)
  document.addEventListener('keydown', onEscape)
})
onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onOutside)
  document.removeEventListener('keydown', onEscape)
})
</script>

<template>
  <div class="erp-shell">
    <a
      class="skip-navigation"
      href="#main-content"
    >跳转到内容</a>
    <header class="erp-header">
      <div
        ref="navigation"
        class="erp-navigation"
        @focusout="onFocusOut"
      >
        <RouterLink
          class="erp-brand"
          to="/home"
          aria-label="九更亚马逊 ERP"
          @click="closeMenu()"
        >
          <span class="erp-brand__mark">九</span>
          <span>九更 <strong>ERP</strong></span>
        </RouterLink>
        <nav
          class="domain-nav"
          aria-label="主菜单"
        >
          <button
            v-for="domain in domains"
            :key="domain.title"
            type="button"
            :data-domain="domain.title"
            :class="{ 'is-active': activeDomain === domain.title, 'is-open': openDomain === domain.title }"
            :aria-expanded="openDomain === domain.title"
            :aria-controls="openDomain === domain.title ? 'navigation-panel' : undefined"
            @click="openDomain = openDomain === domain.title ? '' : domain.title"
            @keydown.down.prevent="focusPanel(domain.title)"
          >
            {{ domain.title }} <span
              class="domain-chevron"
              aria-hidden="true"
            >⌄</span>
          </button>
        </nav>
        <div
          v-if="expanded"
          id="navigation-panel"
          class="mega-panel"
          :aria-label="`${expanded.title}功能`"
        >
          <section
            v-for="row in expanded.rows"
            :key="row.group"
            class="mega-row"
          >
            <h2><span aria-hidden="true">{{ expanded.icon }}</span>{{ row.group }}</h2>
            <div class="mega-links">
              <RouterLink
                v-for="item in row.items"
                :key="item.path"
                :to="`/${item.path}`"
                :class="{ 'is-current': currentEntry?.path === item.path }"
                :aria-current="currentEntry?.path === item.path ? 'page' : undefined"
                @click="closeMenu()"
              >
                {{ item.meta?.title }}
              </RouterLink>
            </div>
          </section>
        </div>
      </div>
      <div class="erp-account">
        <span class="platform-label">亚马逊</span>
        <span
          class="erp-account__user"
          aria-label="当前账号"
        >{{ session.user.value?.displayName }} · {{ session.user.value?.role }}</span>
        <button
          type="button"
          @click="logout"
        >
          退出
        </button>
      </div>
    </header>
    <nav
      class="workspace-tabs"
      aria-label="最近访问页面"
    >
      <RouterLink
        v-if="home"
        to="/home"
        class="home-tab"
        :class="{ 'is-current': currentEntry?.path === 'home' }"
        aria-label="首页"
      >
        <span aria-hidden="true">⌂</span>
      </RouterLink>
      <div
        v-for="item in tabs"
        :key="item.path"
        class="workspace-tab"
        :class="{ 'is-current': currentEntry?.path === item.path }"
      >
        <RouterLink
          :to="`/${item.path}`"
          :aria-current="currentEntry?.path === item.path ? 'page' : undefined"
        >
          {{ item.meta?.title }}
        </RouterLink>
        <button
          type="button"
          :aria-label="`关闭${item.meta?.title}页签`"
          @click="closeTab(item.path)"
        >
          ×
        </button>
      </div>
      <span
        v-if="!tabs.length"
        class="tabs-hint"
      >从上方菜单打开功能，最近访问的页面将在这里显示</span>
    </nav>
    <div class="page-heading">
      <div class="page-heading__trail">
        <template
          v-for="(item, index) in breadcrumbs"
          :key="`${item.title}-${index}`"
        >
          <span
            v-if="index"
            aria-hidden="true"
          >/</span><RouterLink
            v-if="item.to"
            :to="item.to"
          >
            {{ item.title }}
          </RouterLink><h1 v-else-if="index === breadcrumbs.length - 1">
            {{ item.title }}
          </h1><span v-else>{{ item.title }}</span>
        </template>
      </div>
      <span class="page-heading__brand">九更亚马逊 ERP</span>
    </div>
    <main
      id="main-content"
      ref="content"
      class="erp-content"
      tabindex="-1"
    >
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.erp-shell { display: grid; grid-template-rows: auto auto auto minmax(0, 1fr); height: 100vh; height: 100dvh; overflow: hidden; background: #f0f2f7; }
.erp-shell > * { min-width: 0; }
.erp-header { position: relative; z-index: 12; display: flex; align-items: stretch; justify-content: space-between; min-height: 56px; color: #e4e9f5; background: #101c3b; }
.erp-navigation { display: flex; min-width: 0; align-items: stretch; }
.erp-brand { display: flex; flex: 0 0 auto; align-items: center; gap: 10px; padding: 0 24px 0 20px; color: inherit; white-space: nowrap; font-size: 17px; text-decoration: none; }
.erp-brand:hover { color: white; background: #192746; }
.erp-brand strong { font-size: 13px; color: #9eaccb; font-weight: 500; }
.erp-brand__mark { display: grid; width: 30px; height: 30px; place-items: center; color: #101c3b; background: #ffb448; border-radius: 8px; font-weight: 800; }
.domain-nav { display: flex; overflow-x: auto; }
.domain-nav button { position: relative; flex: 0 0 auto; min-width: 90px; padding: 0 20px; color: #c8d0e3; border: 0; background: transparent; cursor: pointer; font-weight: 600; }
.domain-nav button:hover, .domain-nav button.is-open { color: white; background: #293858; }
.domain-nav button.is-active::after { position: absolute; bottom: 0; left: 20px; right: 20px; height: 3px; background: #ffb448; content: ''; }
.domain-chevron { margin-left: 6px; color: #8d9bb9; font-size: 12px; }
.erp-account { display: flex; flex: 0 0 auto; align-items: center; gap: 18px; padding: 0 20px; font-size: 12px; }
.platform-label { padding: 6px 12px; color: #cbd4ec; background: #283451; border-radius: 16px; }
.erp-account button { padding: 8px 0; color: #d7dff0; border: 0; background: transparent; cursor: pointer; }
.mega-panel { position: absolute; top: calc(100% + 5px); left: 16px; width: min(860px, calc(100vw - 32px)); max-height: calc(100vh - 130px); overflow-y: auto; padding: 8px 20px; color: white; background: #232c48; border: 1px solid #34405b; border-radius: 7px; box-shadow: 0 12px 24px #10172b40; }
.mega-row { display: grid; grid-template-columns: 150px minmax(0, 1fr); align-items: start; padding: 16px 0; }
.mega-row + .mega-row { border-top: 1px dashed #46516a; }
.mega-row h2 { display: flex; align-items: center; gap: 12px; margin: 8px 16px 8px 0; color: #b8c1d5; border-right: 1px solid #46516a; font-size: 14px; line-height: 24px; }
.mega-row h2 span { color: #ffb448; font-size: 22px; }
.mega-links { display: flex; flex-wrap: wrap; gap: 4px 8px; }
.mega-links a { padding: 10px 14px; border-radius: 4px; font-size: 14px; line-height: 20px; text-decoration: none; }
.mega-links a:hover, .mega-links a.is-current { color: #ffb448; background: #3a435d; }
.workspace-tabs { display: flex; align-items: stretch; min-height: 38px; padding: 4px 16px 0; overflow-x: auto; color: #d8e0f6; background: #2b4083; }
.home-tab { display: grid; flex: 0 0 44px; place-items: center; margin-right: 8px; border-radius: 6px 6px 0 0; text-decoration: none; font-size: 23px; }
.workspace-tab { display: flex; flex: 0 0 auto; align-items: center; margin-right: 4px; border-radius: 6px 6px 0 0; font-size: 13px; }
.workspace-tab a { display: flex; align-items: center; height: 100%; padding: 7px 10px 7px 14px; text-decoration: none; }
.workspace-tab button { margin-right: 7px; width: 24px; height: 24px; color: inherit; background: transparent; border: 0; border-radius: 4px; cursor: pointer; }
.workspace-tab button:hover { background: #8191bd40; }
.workspace-tab:hover, .home-tab:hover { background: #405597; }
.workspace-tab.is-current, .home-tab.is-current { color: #233b79; background: white; }
.tabs-hint { padding: 7px 12px; color: #b0bfe4; font-size: 12px; white-space: nowrap; }
.page-heading { display: flex; align-items: center; justify-content: space-between; min-height: 60px; padding: 12px 24px; background: white; border-bottom: 1px solid #e5e8ef; }
.page-heading__trail { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; min-width: 0; color: #8992a4; font-size: 13px; }
.page-heading__trail a { color: #53617a; font-weight: 700; text-decoration: none; }
.page-heading__trail a:hover { color: #ff8a34; }
.page-heading h1 { margin: 0; color: #202c43; font-size: 17px; font-weight: 600; }
.page-heading__brand { color: #8a94a6; font-size: 12px; }
.erp-content { min-height: 0; overflow: auto; overscroll-behavior: contain; padding: 20px 24px; outline: none; }
.erp-shell :is(a, button):focus-visible { outline: 2px solid #ffb448; outline-offset: -3px; }
.skip-navigation { position: fixed; z-index: 30; top: -60px; left: 16px; padding: 12px; color: #101c3b; background: #ffb448; }
.skip-navigation:focus { top: 4px; }
@media (max-width: 1100px) {
  .erp-brand { padding-inline: 16px; }
  .domain-nav button { min-width: 76px; padding-inline: 14px; }
  .erp-account { gap: 10px; padding-inline: 14px; }
  .platform-label { display: none; }
}
@media (max-width: 720px) {
  .erp-header { flex-wrap: wrap; }
  .erp-navigation { width: 100%; min-height: 54px; }
  .erp-brand { font-size: 14px; padding-inline: 12px; }
  .erp-brand strong { display: none; }
  .domain-nav { flex: 1; }
  .domain-nav button { min-width: 70px; padding-inline: 10px; }
  .erp-account { justify-content: flex-end; width: 100%; min-height: 32px; background: #192746; }
  .mega-panel { left: 8px; width: calc(100vw - 16px); padding-inline: 12px; }
  .mega-row { grid-template-columns: 1fr; padding-block: 10px; }
  .mega-row h2 { border: 0; margin: 4px 0; }
  .page-heading, .erp-content { padding: 14px; }
  .page-heading__brand { display: none; }
}
</style>
