<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'

import { appRoutes } from '@/router'

const route = useRoute()
const collapsed = ref(false)

const menuGroups = computed(() => {
  const shell = appRoutes.find((item) => item.path === '/')
  const groups = new Map<string, NonNullable<typeof shell>['children']>()

  for (const item of shell?.children ?? []) {
    if (item.meta?.hidden) continue
    const group = item.meta?.group ?? '其他'
    const entries = groups.get(group) ?? []
    entries.push(item)
    groups.set(group, entries)
  }

  return [...groups.entries()]
})
</script>

<template>
  <div
    class="admin-shell"
    :class="{ 'admin-shell--collapsed': collapsed }"
  >
    <aside class="sidebar">
      <div class="brand">
        <span class="brand__mark">九</span>
        <span
          v-if="!collapsed"
          class="brand__text"
        >九更 ERP</span>
      </div>

      <nav
        aria-label="主菜单"
        class="menu"
      >
        <section
          v-for="[group, items] in menuGroups"
          :key="group"
          class="menu-group"
        >
          <p
            v-if="!collapsed"
            class="menu-group__label"
          >
            {{ group }}
          </p>
          <RouterLink
            v-for="item in items"
            :key="item.path"
            :to="`/${item.path}`"
            class="menu-item"
            :title="collapsed ? String(item.meta?.title) : ''"
          >
            <span
              aria-hidden="true"
              class="menu-item__icon"
            >{{ item.meta?.icon }}</span>
            <span v-if="!collapsed">{{ item.meta?.title }}</span>
          </RouterLink>
        </section>
      </nav>

      <button
        class="collapse-button"
        type="button"
        @click="collapsed = !collapsed"
      >
        <span aria-hidden="true">{{ collapsed ? '→' : '←' }}</span>
        <span v-if="!collapsed">收起菜单</span>
      </button>
    </aside>

    <div class="workspace">
      <header class="topbar">
        <div>
          <p class="eyebrow">
            内部管理端
          </p>
          <h1>{{ route.meta.title }}</h1>
        </div>
        <label class="global-search">
          <span class="sr-only">全局搜索</span>
          <input
            disabled
            placeholder="搜索产品编码 / SKU / ASIN（待联调）"
            type="search"
          >
        </label>
        <div class="topbar__actions">
          <button
            disabled
            type="button"
          >
            帮助
          </button>
          <button
            disabled
            type="button"
          >
            通知
          </button>
          <div
            class="account-placeholder"
            aria-label="当前账号占位"
          >
            <span class="status-dot" />
            <span>管理员占位</span>
          </div>
        </div>
      </header>

      <main class="content">
        <RouterView />
      </main>
    </div>
  </div>
</template>
