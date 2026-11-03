<script setup lang="ts">
import { confirmAction } from '@/state/uiConfirmation'
import SuccessToast from "@/components/SuccessToast.vue"
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'

import { changePassword, securitySettingsApi, userDefaultsApi, type AppUser } from '@/api/auth'
import { ApiError } from '@/api/client'
import PasswordField from '@/components/PasswordField.vue'
import { runtimeConfig } from '@/config/runtime'
import { session } from '@/state/session'

const isAdmin = computed(() => session.canAccess(['system.users.manage']))
const editor = ref<'password' | 'security' | 'reset' | 'defaults' | null>(null)
const editorDialog = ref<HTMLDialogElement>()
const editorError = ref('')
const initialEditor = ref('')
const savedDays = ref(7)
const defaultPassword=ref('')
function snapshot() {
  return JSON.stringify([currentPassword.value, newPassword.value, passwordConfirmation.value, temporaryPassword.value, staySignedInDays.value, defaultPassword.value])
}
async function openEditor(kind: 'password' | 'security' | 'reset' | 'defaults', user?: AppUser) {
  if(kind==='defaults') { loading.value=true; errorMessage.value=''; try { defaultPassword.value=(await userDefaultsApi.get()).initialPassword } catch(e) { errorMessage.value=errorText(e); return } finally { loading.value=false } }
  editor.value = kind
  resetTarget.value = user ?? null
  currentPassword.value = newPassword.value = passwordConfirmation.value = temporaryPassword.value = ''
  staySignedInDays.value = savedDays.value
  editorError.value = ''
  initialEditor.value = snapshot()
  await nextTick()
  editorDialog.value?.showModal()
  editorDialog.value?.querySelector<HTMLInputElement>('input')?.focus()
}
function closeEditor() {
  editorDialog.value?.close()
  editor.value = null
  defaultPassword.value=''
  resetTarget.value = null
  currentPassword.value = newPassword.value = passwordConfirmation.value = temporaryPassword.value = ''
}
async function cancelEditor() {
  if (loading.value) return
  if (snapshot() !== initialEditor.value && !await confirmAction('放弃尚未保存的填写内容？')) return
  closeEditor()
}
onBeforeUnmount(() => editorDialog.value?.close())
const staySignedInDays = ref<number | string>(7)
const currentPassword = ref('')
const newPassword = ref('')
const passwordConfirmation = ref('')
const resetTarget = ref<AppUser | null>(null)
const temporaryPassword = ref('')
const loading = ref(false)
const errorMessage = ref('')
const successMessage = ref('')

function errorText(error: unknown) {
  return error instanceof ApiError ? error.message : '操作失败，请稍后重试。'
}

function begin() {
  errorMessage.value = ''
  editorError.value = ''
  successMessage.value = ''
  loading.value = true
}

async function loadAdminSettings() {
  if (!isAdmin.value || runtimeConfig.authMode === 'mock') return
  const settings = await securitySettingsApi.get()
  savedDays.value = settings.staySignedInDays
  if (!editor.value) staySignedInDays.value = savedDays.value
}

async function initialize() {
  loading.value = true
  try {
    await loadAdminSettings()
  }
  catch (error) {
    errorMessage.value = errorText(error)
  }
  finally { loading.value = false }
}

async function savePassword() {
  if (newPassword.value !== passwordConfirmation.value) {
    editorError.value = '两次输入的新密码不一致。'
    return
  }
  begin()
  try {
    await changePassword(currentPassword.value, newPassword.value)
    currentPassword.value = ''
    newPassword.value = ''
    passwordConfirmation.value = ''
    await session.refresh()
    successMessage.value = '密码已更新。'
    closeEditor()
  }
  catch (error) {
    editorError.value = errorText(error)
  }
  finally {
    loading.value = false
  }
}

async function saveSessionPolicy() {
  const days = Number(staySignedInDays.value)
  if (!/^[0-9]{1,2}$/.test(String(staySignedInDays.value)) || !Number.isInteger(days) || days < 1 || days > 30) {
    editorError.value = '免登录天数请输入 1 至 30 的整数。'
    return
  }
  begin()
  try {
    staySignedInDays.value = (await securitySettingsApi.update(days)).staySignedInDays
    savedDays.value = staySignedInDays.value
    closeEditor()
    successMessage.value = `登录页免登录期限已更新为 ${staySignedInDays.value} 天。`
  }
  catch (error) {
    editorError.value = errorText(error)
  }
  finally {
    loading.value = false
  }
}

async function saveDefaults() {
  begin()
  try { await userDefaultsApi.save(defaultPassword.value);closeEditor();successMessage.value='默认初始密码已更新，仅用于后续新增用户。' }
  catch(e){editorError.value=errorText(e)}finally{loading.value=false}
}
onMounted(initialize)
</script>

<template>
  <!-- eslint-disable vue/max-attributes-per-line, vue/singleline-html-element-content-newline -->
  <section class="page-stack settings-page">
    <div v-if="runtimeConfig.authMode === 'mock'" class="notice">
      <strong>SYNTHETIC</strong><span>模拟模式不修改真实账号与安全设置。</span>
    </div>
    <template v-else>
      <p v-if="errorMessage" class="data-state data-state--error" role="alert">{{ errorMessage }}</p>
      <SuccessToast :message="successMessage" />

      <div class="settings-toolbar" aria-label="账号与安全操作">
        <button type="button" class="settings-password-button" :disabled="loading" @click="openEditor('password')">修改密码</button>
        <button v-if="isAdmin" type="button" class="settings-security-button" :disabled="loading" @click="openEditor('security')">安全设置</button>
        <button v-if="isAdmin" type="button" class="settings-defaults-button" :disabled="loading" @click="openEditor('defaults')">用户初始密码</button>
        <span v-if="isAdmin" class="settings-policy-summary">免登录期限：{{ savedDays }} 天</span>
      </div>
      <dialog ref="editorDialog" class="settings-dialog" aria-labelledby="settings-editor-title" @cancel.prevent="cancelEditor">
        <p v-if="editorError" class="data-state data-state--error" role="alert">{{ editorError }}</p>
        <form v-if="editor === 'password'" class="settings-form" @submit.prevent="savePassword">
          <h2 id="settings-editor-title">修改本人密码</h2>
          <label>当前密码<PasswordField id="settings-current-password" v-model="currentPassword" autocomplete="current-password" required /></label>
          <label>新密码<PasswordField id="settings-new-password" v-model="newPassword" autocomplete="new-password" :minlength="6" pattern="(?=.*[A-Za-z])(?=.*[0-9]).{6,128}" required /></label>
          <label>确认新密码<PasswordField id="settings-confirm-password" v-model="passwordConfirmation" autocomplete="new-password" :minlength="6" pattern="(?=.*[A-Za-z])(?=.*[0-9]).{6,128}" required /></label>
          <div class="settings-form-actions ui-dialog-actions"><button :disabled="loading" type="submit">保存新密码</button><button :disabled="loading" type="button" class="secondary-button" @click="cancelEditor">取消</button></div>
        </form>

        <form v-if="editor === 'security'" key="session-policy" class="settings-form" autocomplete="off" @submit.prevent="saveSessionPolicy">
          <h2 id="settings-editor-title">登录安全设置</h2>
          <label for="session-duration-days">免登录天数
            <span class="settings-days-input"><input id="session-duration-days" v-model="staySignedInDays" name="session-duration-days" type="text" inputmode="numeric" autocomplete="off" maxlength="2" pattern="[0-9]{1,2}" required aria-describedby="session-duration-hint"><span>天</span></span>
          </label>
          <p id="session-duration-hint">请输入 1 至 30 天。</p>
          <p class="form-wide">用户勾选登录页的免登录选项后，会话将在此期限内保持有效；未勾选时使用普通短会话。</p>
          <div class="settings-form-actions ui-dialog-actions"><button :disabled="loading" type="submit">保存设置</button><button :disabled="loading" type="button" class="secondary-button" @click="cancelEditor">取消</button></div>
        </form>
        <form v-if="editor==='defaults'" class="settings-form" @submit.prevent="saveDefaults">
          <h2 id="settings-editor-title">用户初始密码</h2>
          <label>默认初始密码<PasswordField id="settings-default-password" v-model="defaultPassword" autocomplete="new-password" :minlength="6" pattern="(?=.*[A-Za-z])(?=.*[0-9]).{6,128}" /></label>
          <p>新增用户时自动填入，可在创建时修改。密码需为6至128位，包含字母和数字；留空则不自动填入。首次登录仍需修改密码。</p>
          <div class="settings-form-actions ui-dialog-actions"><button :disabled="loading" type="submit">保存设置</button><button :disabled="loading" type="button" class="secondary-button" @click="cancelEditor">取消</button></div>
        </form>
      </dialog>
    </template>
  </section>
</template>

<style scoped>
.settings-toolbar { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; padding: 4px 10px; border: 1px solid var(--line); border-radius: 6px; background: white; }
.settings-toolbar button { color: var(--ink); background: #f5f6f8; border: 1px solid var(--line); border-radius: 4px; cursor: pointer; min-height: var(--ui-control-height); padding: 4px 12px; font-size: 13px; }
.settings-toolbar .settings-password-button { color: white; background: #c65a0a; border-color: #c65a0a; }
.settings-toolbar .settings-security-button { color: white; background: #16745a; border-color: #16745a; }
.settings-toolbar .settings-defaults-button { color: white; background: #7450ad; border-color: #7450ad; }
.settings-toolbar button:not(:disabled):hover { filter: brightness(.92); }
.settings-toolbar button:focus-visible { outline: 2px solid #24334b; outline-offset: 3px; }
.settings-toolbar button:disabled { opacity: .5; cursor: not-allowed; }
.settings-page .table-actions .delete-account { color: #b42318; }
.settings-page .table-actions .delete-account:hover { background: #fff1f0; }
.settings-policy-summary { color: var(--muted); font-size: 12px; }
.settings-page .data-table td { padding: 6px 12px; }
.settings-page .table-actions button { min-height: 28px; padding: 3px 8px; color: #315d9b; background: transparent; border: 0; font-size: 13px; }
.settings-page .table-actions button:hover { background: #edf3fb; }
.settings-form button { min-height: 36px; border: 1px solid var(--line); border-radius: 5px; padding: 6px 14px; cursor: pointer; }
.settings-form button[type="submit"] { background: var(--accent); color: white; border-color: var(--accent); }
.table-title { margin: 0; padding: 8px 12px; font-size: 14px; }
.settings-dialog { width: min(480px, calc(100vw - 24px)); max-height: calc(100dvh - 24px); padding: 20px; border: 0; border-radius: 10px; overflow: auto; }
.settings-dialog::backdrop { background: #17203366; }
.settings-form { display: grid; gap: 16px; }
.settings-form h2 { margin: 0; font-size: 18px; }
.settings-form label { display: grid; gap: 6px; font-size: 13px; }
.settings-form :deep(input) { min-height: 40px; width: 100%; border: 1px solid var(--line); border-radius: 5px; padding: 8px 10px; }
.settings-form :deep(.password-field input) { padding-right: 44px; }
.settings-form :deep(.password-toggle) { min-height: 40px; height: 40px; width: 40px; border: 0; padding: 10px; }
.settings-form p { margin: 0; color: var(--muted); font-size: 13px; line-height: 1.6; }
.settings-form-actions { display: flex; justify-content: flex-end; gap: 8px; }
.settings-days-input { display:flex; align-items:center; gap:8px; }
.settings-days-input input { flex:1; min-width:0; -webkit-text-security:none; }
.settings-days-input > span { color:var(--muted); }
</style>
