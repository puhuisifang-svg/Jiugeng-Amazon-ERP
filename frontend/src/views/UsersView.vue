<script setup lang="ts">
import SuccessToast from "@/components/SuccessToast.vue"
import TableViewport from "@/components/TableViewport.vue"
import { onMounted, ref } from 'vue'

import { usersApi, type AppUser } from '@/api/auth'
import { ApiError } from '@/api/client'
import PasswordField from '@/components/PasswordField.vue'
import { runtimeConfig } from '@/config/runtime'
import type { AppRole } from '@/router/meta'

const users = ref<AppUser[]>([])
const loading = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const resetTarget = ref<AppUser | null>(null)
const resetPasswordValue = ref('')
const contactTarget = ref<AppUser | null>(null)
const contactForm = ref({ email: '', phone: '' })
const form = ref({ loginName: '', displayName: '', email: '', phone: '', initialPassword: '', role: 'PRODUCT_MATERIAL' as AppRole })

function message(error: unknown) {
  return error instanceof ApiError ? error.message : '操作失败，请稍后重试。'
}

async function load() {
  if (runtimeConfig.authMode === 'mock') return
  loading.value = true
  try {
    users.value = await usersApi.list()
  }
  catch (error) {
    errorMessage.value = message(error)
  }
  finally {
    loading.value = false
  }
}

async function createUser() {
  errorMessage.value = ''
  successMessage.value = ''
  loading.value = true
  try {
    await usersApi.create(form.value)
    form.value = { loginName: '', displayName: '', email: '', phone: '', initialPassword: '', role: 'PRODUCT_MATERIAL' }
    successMessage.value = '账号已创建，首次登录必须修改初始密码。'
    await load()
  }
  catch (error) {
    errorMessage.value = message(error)
  }
  finally {
    loading.value = false
  }
}

async function update(action: 'disable' | 'enable' | 'unlock', user: AppUser) {
  if (action === 'disable' && !window.confirm(`确定停用 ${user.loginName}？该账号的活动会话会立即失效。`)) return
  errorMessage.value = ''
  successMessage.value = ''
  try {
    await usersApi[action](user.id)
    successMessage.value = '账号状态已更新。'
    await load()
  }
  catch (error) {
    errorMessage.value = message(error)
  }
}

function beginResetPassword(user: AppUser) {
  resetTarget.value = user
  resetPasswordValue.value = ''
}

function beginRecoveryContacts(user: AppUser) {
  contactTarget.value = user
  contactForm.value = { email: user.email ?? '', phone: user.phone ?? '' }
}

async function saveRecoveryContacts() {
  if (!contactTarget.value) return
  errorMessage.value = ''
  successMessage.value = ''
  try {
    await usersApi.updateRecoveryContacts(contactTarget.value.id, contactForm.value.email, contactForm.value.phone)
    successMessage.value = '找回密码联系方式已更新。'
    contactTarget.value = null
    await load()
  }
  catch (error) {
    errorMessage.value = message(error)
  }
}

async function resetPassword() {
  if (!resetTarget.value) return
  errorMessage.value = ''
  successMessage.value = ''
  try {
    await usersApi.resetPassword(resetTarget.value.id, resetPasswordValue.value)
    successMessage.value = '密码已重置，原会话已失效；该账号下次登录必须改密。'
    resetTarget.value = null
    resetPasswordValue.value = ''
    await load()
  }
  catch (error) {
    errorMessage.value = message(error)
  }
}

async function changeRole(user: AppUser, event: Event) {
  const select = event.target as HTMLSelectElement
  const role = select.value as AppRole
  if (role === user.role) return
  if (!window.confirm(`确定修改 ${user.loginName} 的角色？该账号的活动会话会立即失效。`)) {
    select.value = user.role
    return
  }
  errorMessage.value = ''
  successMessage.value = ''
  try {
    await usersApi.setRole(user.id, role)
    successMessage.value = '角色已更新，该账号的原会话已失效。'
    await load()
  }
  catch (error) {
    errorMessage.value = message(error)
  }
}

onMounted(load)
</script>

<template>
  <!-- eslint-disable vue/max-attributes-per-line, vue/singleline-html-element-content-newline -->
  <section class="page-stack">
    <div v-if="runtimeConfig.authMode === 'mock'" class="notice">
      <strong>SYNTHETIC</strong><span>模拟模式不维护真实账号；请在 password 模式下使用此页面。</span>
    </div>
    <template v-else>
      <form class="panel operation-form" @submit.prevent="createUser">
        <h2>创建内部账号</h2>
        <label>登录名<input v-model.trim="form.loginName" pattern="[A-Za-z0-9._-]{3,128}" required></label>
        <label>显示名称<input v-model.trim="form.displayName" maxlength="128" required></label>
        <label>邮箱<input v-model.trim="form.email" autocomplete="email" maxlength="254" type="email"></label>
        <label>手机号<input v-model.trim="form.phone" autocomplete="tel" maxlength="16"></label>
        <label>角色
          <select v-model="form.role">
            <option value="PRODUCT_MATERIAL">产品与辅料</option>
            <option value="SELECTION">选品</option>
            <option value="ADMIN">管理员</option>
          </select>
        </label>
        <div class="form-wide"><label for="initial-password">初始密码</label><PasswordField id="initial-password" v-model="form.initialPassword" autocomplete="new-password" :minlength="6" pattern="(?=.*[A-Za-z])(?=.*\\d).{6,128}" required /></div>
        <button :disabled="loading" type="submit">创建账号</button>
      </form>
      <p v-if="errorMessage" class="data-state data-state--error" role="alert">{{ errorMessage }}</p>
      <SuccessToast :message="successMessage" />
      <form v-if="resetTarget" class="panel operation-form" @submit.prevent="resetPassword">
        <h2>重置 {{ resetTarget.loginName }} 的密码</h2>
        <div class="form-wide"><label for="temporary-password">临时密码</label><PasswordField id="temporary-password" v-model="resetPasswordValue" autocomplete="new-password" :minlength="6" pattern="(?=.*[A-Za-z])(?=.*\\d).{6,128}" required /></div>
        <button type="submit">确认重置</button>
        <button class="secondary-button" type="button" @click="resetTarget = null">取消</button>
      </form>
      <form v-if="contactTarget" class="panel operation-form" @submit.prevent="saveRecoveryContacts">
        <h2>维护 {{ contactTarget.loginName }} 的找回方式</h2>
        <label>邮箱<input v-model.trim="contactForm.email" autocomplete="email" maxlength="254" type="email"></label>
        <label>手机号<input v-model.trim="contactForm.phone" autocomplete="tel" maxlength="16"></label>
        <p class="form-wide">邮箱和手机号至少填写一项。</p>
        <button type="submit">保存联系方式</button>
        <button class="secondary-button" type="button" @click="contactTarget = null">取消</button>
      </form>
      <div class="panel table-panel">
        <h2 class="table-title">账号列表</h2>
        <p v-if="loading && !users.length" class="data-state">正在加载…</p>
        <div v-else class="table-scroll">
          <TableViewport>
            <table class="data-table">
              <thead><tr><th>登录名</th><th>显示名称</th><th>找回方式</th><th>角色</th><th>状态</th><th>首次改密</th><th>操作</th></tr></thead>
              <tbody>
                <tr v-for="user in users" :key="user.id">
                  <td>{{ user.loginName }}</td><td>{{ user.displayName }}</td>
                  <td>{{ [user.email, user.phone].filter(Boolean).join(' / ') || '未设置' }}</td>
                  <td>
                    <select :value="user.role" aria-label="角色" @change="changeRole(user, $event)">
                      <option value="ADMIN">管理员</option>
                      <option value="PRODUCT_MATERIAL">产品与辅料</option>
                      <option value="SELECTION">选品</option>
                    </select>
                  </td>
                  <td>{{ user.lockedAt ? '已锁定' : user.status === 'ENABLED' ? '启用' : '停用' }}</td>
                  <td>{{ user.mustChangePassword ? '是' : '否' }}</td>
                  <td class="table-actions">
                    <button v-if="user.status === 'ENABLED'" type="button" @click="update('disable', user)">停用</button>
                    <button v-else type="button" @click="update('enable', user)">启用</button>
                    <button v-if="user.lockedAt" type="button" @click="update('unlock', user)">解锁</button>
                    <button type="button" @click="beginResetPassword(user)">重置密码</button>
                    <button type="button" @click="beginRecoveryContacts(user)">联系方式</button>
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
