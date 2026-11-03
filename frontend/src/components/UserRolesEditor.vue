<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { rolesApi, type Role, type Permission, type PermissionPreview } from '@/api/roles'
import type { AppUser } from '@/api/auth'
import AccessDialog from './AccessDialog.vue'
import PermissionDifference from './PermissionDifference.vue'
const props = defineProps<{ user: AppUser; roles: Role[]; catalog: Permission[]; scopedRole?: Role }>()
const emit = defineEmits<{ close: []; saved: [] }>()
const selected = ref<string[]>([]), reason = ref(''), error = ref(''), busy = ref(false), preview = ref<PermissionPreview>()
const dialog = ref<InstanceType<typeof AccessDialog>>()
const removing = computed(() => !!props.scopedRole && (props.user.roles ?? []).some(r => r.id === props.scopedRole!.id))
const title = computed(() => props.scopedRole ? `${removing.value ? '移除成员' : '添加成员'} · ${props.scopedRole.name}` : `分配角色 · ${props.user.displayName}`)
onMounted(() => {
  const existing = (props.user.roles ?? []).map(r => r.id)
  selected.value = !props.scopedRole ? existing : removing.value ? existing.filter(id => id !== props.scopedRole!.id) : [...existing, props.scopedRole.id]
})
const input = computed(() => ({ roleIds: selected.value, version: props.user.authorizationVersion ?? 0, reason: reason.value }))
const rolesChanged = computed(() => [...selected.value].sort().join(',') !== (props.user.roles ?? []).map(r => r.id).sort().join(','))
const dirty = computed(() => rolesChanged.value || !!reason.value)
function toggle(id: string) { if (selected.value.includes(id)) selected.value = selected.value.filter(r => r !== id); else selected.value = id === 'ADMIN' ? ['ADMIN'] : [...selected.value.filter(r => r !== 'ADMIN'), id]; preview.value = undefined }
async function review() { busy.value = true; error.value = ''; try { preview.value = await rolesApi.previewAssignment(props.user.id, input.value) } catch (e) { error.value = e instanceof Error ? e.message : '预览失败' } finally { busy.value = false } }
async function save() { busy.value = true; error.value = ''; try { await rolesApi.assign(props.user.id, input.value); emit('saved') } catch (e) { error.value = e instanceof Error ? e.message : '保存失败'; preview.value = undefined } finally { busy.value = false } }
</script>
<template>
  <AccessDialog
    ref="dialog"
    :title="title"
    dialog-class="assignment-dialog"
    :dirty="dirty"
    :busy="busy"
    @close="emit('close')"
  >
    <div class="assignment-summary">
      <strong>{{ user.displayName }}</strong><span>{{ user.loginName }}</span><p>{{ scopedRole ? `本次仅${removing ? '移除' : '添加'}「${scopedRole.name}」，其他角色保持不变。` : '选择岗位角色，核对权限变化后保存。' }}</p>
    </div>
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
      移除全部角色后，账号状态保持不变，现有登录会话将失效。启用的账号仍可登录，但暂无功能权限；重新分配角色后即可使用。账号资料和历史记录保留。
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
      <p
        v-if="!scopedRole"
        class="access-muted"
      >
        多个角色的权限取并集。管理员角色不能与其他角色混用。
      </p>
      <label
        v-for="role in (scopedRole ? [] : roles.filter(r => r.status === 'ENABLED' || selected.includes(r.id)))"
        :key="role.id"
        class="role-choice"
        :class="{'role-choice-selected': selected.includes(role.id)}"
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
      <span class="assignment-step">{{ preview ? '确认变更' : scopedRole ? '核对成员' : `已选择 ${selected.length} 个角色` }}</span>
      <button
        v-if="!preview"
        class="assignment-secondary"
        :disabled="busy"
        @click="dialog?.requestClose()"
      >
        取消
      </button>
      <button
        v-if="preview"
        class="assignment-secondary"
        :disabled="busy"
        @click="preview = undefined"
      >
        返回修改
      </button><button
        class="primary-button assignment-primary"
        :disabled="busy || !rolesChanged || reason.length > 200"
        @click="preview ? save() : review()"
      >
        {{ busy ? '处理中…' : preview ? (scopedRole ? (removing ? '确认移除成员' : '确认添加成员') : selected.length ? '确认分配角色' : '确认移除全部角色') : '预览权限变化' }}
      </button>
    </template>
  </AccessDialog>
</template>
