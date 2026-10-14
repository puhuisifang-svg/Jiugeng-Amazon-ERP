const apiBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim()
const authMode = import.meta.env.VITE_AUTH_MODE?.trim() ?? (import.meta.env.PROD ? 'password' : 'mock')

if (!apiBaseUrl) {
  throw new Error('VITE_API_BASE_URL must be configured for the active environment')
}
if (authMode !== 'mock' && authMode !== 'password') {
  throw new Error('VITE_AUTH_MODE must be either mock or password')
}

export const runtimeConfig = Object.freeze({
  apiBaseUrl: apiBaseUrl.replace(/\/$/, ''),
  authMode,
})
