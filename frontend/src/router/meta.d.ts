import 'vue-router'

export type AppRole = 'ADMIN' | 'PRODUCT_MATERIAL' | 'SELECTION'
export type Capability =
  | 'system.users.manage' | 'products.read' | 'products.write' | 'materials.read' | 'materials.write'
  | 'inventory.read' | 'inventory.write' | 'selection.read' | 'selection.write' | 'watch.read' | 'watch.write'
  | 'imports.read' | 'imports.template.download' | 'imports.validate' | 'imports.commit' | 'imports.cancel'
  | 'imports.result.download' | 'audit.read.all' | 'audit.read.self'

declare module 'vue-router' {
  interface RouteMeta {
    title: string
    group?: string
    icon?: string
    hidden?: boolean
    capabilities?: Capability[]
  }
}

export {}
