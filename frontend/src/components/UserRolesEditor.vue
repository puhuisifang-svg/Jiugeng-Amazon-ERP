<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { rolesApi, type Role, type Permission, type PermissionPreview } from '@/api/roles'
import type { AppUser } from '@/api/auth'
import AccessDialog from './AccessDialog.vue'
import PermissionDifference from './PermissionDifference.vue'
const props = defineProps<{ user: AppUser; roles: Role[]; catalog: Permission[] }>()
const emit = defineEmits<{ close: []; saved: [] }>()
const selected = ref<string[]>([]), reason = ref(''), error = ref(''), busy = ref(false), preview = ref<PermissionPreview>()
onMounted(() => { selected.value = (props.user.roles ?? []).map(r => r.id) })
const input = computed(() => ({ roleIds: selected.value, version: props.user.authorizationVersion ?? 0, reason: reason.value }))
const rolesChanged = computed(() => [...selected.value].sort().join(',') !== (props.user.roles ?? []).map(r => r.id).sort().join(','))
const dirty = computed(() => rolesChanged.value || !!reason.value)
function toggle(id: string) { if (selected.value.includes(id)) selected.value = selected.value.filter(r => r !== id); else selected.value = id === 'ADMIN' ? ['ADMIN'] : [...selected.value.filter(r => r !== 'ADMIN'), id]; preview.value = undefined }
async function review() { busy.value = true; error.value = ''; try { preview.value = await rolesApi.previewAssignment(props.user.id, input.value) } catch (e) { error.value = e instanceof Error ? e.message : '预览失败' } finally { busy.value = false } }
async function save() { busy.value = true; error.value = ''; try { await rolesApi.assign(props.user.id, input.value); emit('saved') } catch (e) { error.value = e instanceof Error ? e.message : '保存失败'; preview.value = undefined } finally { busy.value = false } }
</script>
<template>
  <AccessDialog
    :title="`分配角色 · ${user.displayName}`"
    :dirty="dirty"
    :busy="busy"
    @close="emit('close')"
  >
    <p
      v-if="error"
      role="alert"
      class="data-state--error"
    >
      {{ error }}
    </p>
    <p
      v-if="!selected.length"
      role="status"
      class="data-state--warning"
    >
      移除全部角色后，该账号将同时停用并退出登录；账号资料和历史记录保留。重新使用时，请先分配角色，再到用户管理启用账号。
    </p>
    <PermissionDifference
      v-if="preview"
      :preview="preview"
      :catalog="catalog"
    />
    <div
      v-else
      class="access-form"
    >
      <p class="access-muted">
        多个角色的权限取并集。管理员角色不能与其他角色混用。
      </p>
      <label
        v-for="role in roles.filter(r => r.status === 'ENABLED' || selected.includes(r.id))"
        :key="role.id"
        class="role-choice"
      ><input
        type="checkbox"
        :checked="selected.includes(role.id)"
        @change="toggle(role.id)"
      >{{ role.name }}{{ role.status !== 'ENABLED' ? '（已停用，请移除）' : '' }}<span class="access-muted">{{ role.kind === 'BUILTIN' ? '系统预置' : '自定义' }}</span></label>
      <label>变更原因（选填）<textarea
        v-model="reason"
        maxlength="200"
        rows="2"
        placeholder="可补充本次调整原因，最多200字"
      /></label>
    </div>
    <template #footer>
      <button
        v-if="preview"
        :disabled="busy"
        @click="preview = undefined"
      >
        返回修改
      </button><button
        class="primary-button"
        :disabled="busy || !rolesChanged || reason.length > 200"
        @click="preview ? save() : review()"
      >
        {{ busy ? '处理中…' : preview ? (selected.length ? '确认分配角色' : '移除角色并停用账号') : '预览权限变化' }}
      </button>
    </template>
  </AccessDialog>
</template>
