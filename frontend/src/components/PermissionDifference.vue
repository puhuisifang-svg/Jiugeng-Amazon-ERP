<script setup lang="ts">
import type { Permission, PermissionPreview } from '@/api/roles'
defineProps<{ preview: PermissionPreview; catalog: Permission[] }>()
</script>
<template>
  <div class="permission-difference">
    <p>影响 {{ preview.members.length }} 个账号。权限发生变化后，相关账号需要重新登录。</p>
    <p
      v-if="preview.members.length"
      class="access-muted"
    >
      {{ preview.members.map(u => u.displayName || u.loginName).join('、') }}
    </p>
    <h3>新增 {{ preview.added.length }} 项</h3><ul>
      <li
        v-for="code in preview.added"
        :key="code"
      >
        {{ catalog.find(p => p.code === code)?.label || code }}
      </li>
    </ul>
    <h3>移除 {{ preview.removed.length }} 项</h3><ul>
      <li
        v-for="code in preview.removed"
        :key="code"
      >
        {{ catalog.find(p => p.code === code)?.label || code }}
      </li>
    </ul>
    <p
      v-if="!preview.added.length && !preview.removed.length"
      class="access-muted"
    >
      权限集合不变。角色启停仍会改变成员的有效权限。
    </p>
  </div>
</template>
<style scoped>.permission-difference h3{font-size:14px}.permission-difference ul{display:flex;gap:8px 20px;flex-wrap:wrap;padding-left:20px}.permission-difference p{line-height:1.6}</style>
