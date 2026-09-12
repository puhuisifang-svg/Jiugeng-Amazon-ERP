const apiBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim()

if (!apiBaseUrl) {
  throw new Error('VITE_API_BASE_URL must be configured for the active environment')
}

export const runtimeConfig = Object.freeze({
  apiBaseUrl: apiBaseUrl.replace(/\/$/, ''),
})
