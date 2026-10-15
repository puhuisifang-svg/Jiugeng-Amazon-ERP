import { computed, reactive } from 'vue'

import { loadCurrentUser, login, logout as logoutRemote, type AppUser, type LoginInput } from '@/api/auth'
import { clearSessionToken, getSessionToken, SESSION_EXPIRED_EVENT, SESSION_TOKEN_KEY, setSessionToken } from '@/api/client'
import { runtimeConfig } from '@/config/runtime'
import type { AppRole, Capability } from '@/router/meta'

const STORAGE_KEY = 'jiugeng.session.user'

function restoreUser(): AppUser | null {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY) ?? window.sessionStorage.getItem(STORAGE_KEY)
    return raw ? JSON.parse(raw) as AppUser : null
  }
  catch {
    return null
  }
}

const state = reactive<{ user: AppUser | null }>({ user: restoreUser() })

function persist(user: AppUser, token?: string, persistent?: boolean) {
  state.user = user
  const usePersistentStorage = persistent ?? Boolean(window.localStorage.getItem(SESSION_TOKEN_KEY))
  const storage = usePersistentStorage ? window.localStorage : window.sessionStorage
  window.localStorage.removeItem(STORAGE_KEY)
  window.sessionStorage.removeItem(STORAGE_KEY)
  storage.setItem(STORAGE_KEY, JSON.stringify(user))
  if (token) setSessionToken(token, usePersistentStorage)
}

function clear() {
  state.user = null
  window.localStorage.removeItem(STORAGE_KEY)
  window.sessionStorage.removeItem(STORAGE_KEY)
  clearSessionToken()
  window.localStorage.removeItem('jiugeng.mock.session.user')
  window.localStorage.removeItem('jiugeng.mock.session.token')
}

window.addEventListener(SESSION_EXPIRED_EVENT, clear)

export const session = {
  user: computed(() => state.user),
  isAuthenticated: computed(() => Boolean(state.user && getSessionToken())),
  async login(input: LoginInput | AppRole) {
    const normalized = typeof input === 'string' ? { role: input } : input
    const response = await login(normalized)
    persist(response.user, response.token, runtimeConfig.authMode === 'mock' || Boolean(normalized.staySignedIn))
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
    if (!state.user || !getSessionToken()) {
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
