import 'vue-router'

export type AppRole = 'ADMIN' | 'PRODUCT_MATERIAL' | 'SELECTION'

declare module 'vue-router' {
  interface RouteMeta {
    title: string
    group?: string
    icon?: string
    hidden?: boolean
    roles?: AppRole[]
  }
}

export {}
