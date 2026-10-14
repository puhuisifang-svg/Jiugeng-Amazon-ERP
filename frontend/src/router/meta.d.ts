import 'vue-router'

export type AppRole = 'ADMIN' | 'PRODUCT_MATERIAL' | 'SELECTION'
export type Capability =
  | 'system.users.manage' | 'products.read' | 'products.write' | 'materials.read' | 'materials.write'
  | 'inventory.read' | 'inventory.write' | 'selection.read' | 'selection.write' | 'watch.read' | 'watch.write'
  | 'imports.products' | 'imports.materials' | 'imports.inventory' | 'imports.selection' | 'imports.watch'
  | 'audit.read.all' | 'audit.read.self' | 'audit.export'

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
