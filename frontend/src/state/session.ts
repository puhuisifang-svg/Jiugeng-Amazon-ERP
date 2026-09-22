import { computed, reactive } from 'vue'

import { loadCurrentUser, loginAs, type MockUser } from '@/api/auth'
import type { AppRole } from '@/router/meta'

const STORAGE_KEY = 'jiugeng.mock.session.user'
const TOKEN_KEY = 'jiugeng.mock.session.token'

function restoreUser(): MockUser | null {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    return raw ? JSON.parse(raw) as MockUser : null
  }
  catch {
    return null
  }
}
const state = reactive<{ user: MockUser | null }>({ user: restoreUser() })

function persist(user: MockUser, token?: string) {
  state.user = user
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(user))
  if (token) window.localStorage.setItem(TOKEN_KEY, token)
}

export const session = {
  user: computed(() => state.user),
  isAuthenticated: computed(() => Boolean(state.user)),
  async login(role: AppRole) {
    const response = await loginAs(role)
    persist(response.user, response.token)
  },
  async refresh() {
    const user = await loadCurrentUser()
    persist(user)
  },
  logout() {
    state.user = null
    window.localStorage.removeItem(STORAGE_KEY)
    window.localStorage.removeItem(TOKEN_KEY)
  },
  canAccess(roles?: AppRole[]) {
    return !roles?.length || Boolean(state.user && roles.includes(state.user.role))
  },
}
