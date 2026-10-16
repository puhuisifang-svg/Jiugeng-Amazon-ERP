<script setup lang="ts">
import SuccessToast from "@/components/SuccessToast.vue"
import TableViewport from "@/components/TableViewport.vue"
import { computed, onMounted, ref } from 'vue'

import { changePassword, securitySettingsApi, usersApi, type AppUser } from '@/api/auth'
import { ApiError } from '@/api/client'
import PasswordField from '@/components/PasswordField.vue'
import { runtimeConfig } from '@/config/runtime'
import { session } from '@/state/session'

const isAdmin = computed(() => session.canAccess(['system.users.manage']))
const users = ref<AppUser[]>([])
const staySignedInDays = ref(7)
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
  successMessage.value = ''
  loading.value = true
}

async function loadAdminSettings() {
  if (!isAdmin.value || runtimeConfig.authMode === 'mock') return
  const [settings, accountList] = await Promise.all([securitySettingsApi.get(), usersApi.list()])
  staySignedInDays.value = settings.staySignedInDays
  users.value = accountList
}

async function initialize() {
  try {
    await loadAdminSettings()
  }
  catch (error) {
    errorMessage.value = errorText(error)
  }
}

async function savePassword() {
  if (newPassword.value !== passwordConfirmation.value) {
    errorMessage.value = '两次输入的新密码不一致。'
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
  }
  catch (error) {
    errorMessage.value = errorText(error)
  }
  finally {
    loading.value = false
  }
}

async function saveSessionPolicy() {
  begin()
  try {
    staySignedInDays.value = (await securitySettingsApi.update(staySignedInDays.value)).staySignedInDays
    successMessage.value = `登录页免登录期限已更新为 ${staySignedInDays.value} 天。`
  }
  catch (error) {
    errorMessage.value = errorText(error)
  }
  finally {
    loading.value = false
  }
}

async function changeLock(user: AppUser) {
  const action = user.lockedAt ? 'unlock' : 'lock'
  const verb = user.lockedAt ? '恢复' : '锁定'
  if (!window.confirm(`确定${verb}账号 ${user.loginName}？`)) return
  begin()
  try {
    await usersApi[action](user.id)
    await loadAdminSettings()
    successMessage.value = `账号已${verb}。`
  }
  catch (error) {
    errorMessage.value = errorText(error)
  }
  finally {
    loading.value = false
  }
}

async function resetPassword() {
  if (!resetTarget.value) return
  begin()
  try {
    await usersApi.resetPassword(resetTarget.value.id, temporaryPassword.value)
    successMessage.value = '密码已重置，原会话已失效；该账号下次登录必须修改密码。'
    resetTarget.value = null
    temporaryPassword.value = ''
    await loadAdminSettings()
  }
  catch (error) {
    errorMessage.value = errorText(error)
  }
  finally {
    loading.value = false
  }
}

onMounted(initialize)
</script>

<template>
  <!-- eslint-disable vue/max-attributes-per-line, vue/singleline-html-element-content-newline -->
  <section class="page-stack">
    <div v-if="runtimeConfig.authMode === 'mock'" class="notice">
      <strong>SYNTHETIC</strong><span>模拟模式不修改真实账号与安全设置。</span>
    </div>
    <template v-else>
      <p v-if="errorMessage" class="data-state data-state--error" role="alert">{{ errorMessage }}</p>
      <SuccessToast :message="successMessage" />

      <form class="panel operation-form" @submit.prevent="savePassword">
        <h2>修改本人密码</h2>
        <label>当前密码<PasswordField id="settings-current-password" v-model="currentPassword" autocomplete="current-password" required /></label>
        <label>新密码<PasswordField id="settings-new-password" v-model="newPassword" autocomplete="new-password" :minlength="6" pattern="(?=.*[A-Za-z])(?=.*\d).{6,128}" required /></label>
        <label>确认新密码<PasswordField id="settings-confirm-password" v-model="passwordConfirmation" autocomplete="new-password" :minlength="6" pattern="(?=.*[A-Za-z])(?=.*\d).{6,128}" required /></label>
        <button :disabled="loading" type="submit">保存新密码</button>
      </form>

      <form v-if="isAdmin" class="panel operation-form" @submit.prevent="saveSessionPolicy">
        <h2>登录安全设置</h2>
        <label>免登录天数
          <input v-model.number="staySignedInDays" max="30" min="1" required type="number">
        </label>
        <p class="form-wide">用户勾选登录页的免登录选项后，会话将在此期限内保持有效；未勾选时使用普通短会话。</p>
        <button :disabled="loading" type="submit">保存设置</button>
      </form>

      <form v-if="resetTarget" class="panel operation-form" @submit.prevent="resetPassword">
        <h2>重置 {{ resetTarget.loginName }} 的密码</h2>
        <label class="form-wide">临时密码<PasswordField id="settings-temporary-password" v-model="temporaryPassword" autocomplete="new-password" :minlength="6" pattern="(?=.*[A-Za-z])(?=.*\d).{6,128}" required /></label>
        <button :disabled="loading" type="submit">确认重置</button>
        <button class="secondary-button" type="button" @click="resetTarget = null">取消</button>
      </form>

      <div v-if="isAdmin" class="panel table-panel">
        <h2 class="table-title">账号锁定与恢复</h2>
        <div class="table-scroll">
          <TableViewport>
            <table class="data-table">
              <thead><tr><th>登录名</th><th>显示名称</th><th>角色</th><th>状态</th><th>操作</th></tr></thead>
              <tbody>
                <tr v-for="user in users" :key="user.id">
                  <td>{{ user.loginName }}</td>
                  <td>{{ user.displayName }}</td>
                  <td>{{ user.role }}</td>
                  <td>{{ user.lockedAt ? '已锁定' : user.status === 'ENABLED' ? '正常' : '已停用' }}</td>
                  <td class="table-actions">
                    <button v-if="user.status === 'ENABLED' && user.id !== session.user.value?.id" :disabled="loading" type="button" @click="changeLock(user)">
                      {{ user.lockedAt ? '恢复账号' : '锁定账号' }}
                    </button>
                    <button v-if="user.id !== session.user.value?.id" :disabled="loading" type="button" @click="resetTarget = user; temporaryPassword = ''">重置密码</button>
                  </td>
                </tr>
              </tbody>
            </table>
          </TableViewport>
        </div>
      </div>
    </template>
  </section>
</template>
