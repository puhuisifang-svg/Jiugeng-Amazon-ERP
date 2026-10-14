import { computed, reactive } from 'vue'

import { loadCurrentUser, login, logout as logoutRemote, type AppUser, type LoginInput } from '@/api/auth'
import { SESSION_EXPIRED_EVENT, SESSION_TOKEN_KEY } from '@/api/client'
import type { AppRole, Capability } from '@/router/meta'

const STORAGE_KEY = 'jiugeng.session.user'

function restoreUser(): AppUser | null {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    return raw ? JSON.parse(raw) as AppUser : null
  }
  catch {
    return null
  }
}

const state = reactive<{ user: AppUser | null }>({ user: restoreUser() })

function persist(user: AppUser, token?: string) {
  state.user = user
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(user))
  if (token) window.localStorage.setItem(SESSION_TOKEN_KEY, token)
}

function clear() {
  state.user = null
  window.localStorage.removeItem(STORAGE_KEY)
  window.localStorage.removeItem(SESSION_TOKEN_KEY)
  window.localStorage.removeItem('jiugeng.mock.session.user')
  window.localStorage.removeItem('jiugeng.mock.session.token')
}

window.addEventListener(SESSION_EXPIRED_EVENT, clear)

export const session = {
  user: computed(() => state.user),
  isAuthenticated: computed(() => Boolean(state.user && window.localStorage.getItem(SESSION_TOKEN_KEY))),
  async login(input: LoginInput | AppRole) {
    const response = await login(typeof input === 'string' ? { role: input } : input)
    persist(response.user, response.token)
  },
  async refresh() {
    const user = await loadCurrentUser()
    persist(user)
  },
  async logout() {
    try {
      await logoutRemote()
    }
    finally {
      clear()
    }
  },
  clear,
  async initialize() {
    if (!state.user || !window.localStorage.getItem(SESSION_TOKEN_KEY)) {
      clear()
      return
    }
    try {
      await this.refresh()
    }
    catch {
      clear()
    }
  },
  canAccess(capabilities?: Capability[]) {
    return !capabilities?.length || Boolean(state.user && capabilities.every(item => state.user?.capabilities.includes(item)))
  },
}
