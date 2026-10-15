<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { ApiError } from '@/api/client'
import { domainApi, type ProductDetailDto, type ProductInput } from '@/api/domain'
import AuthenticatedCatalogImage from '@/components/AuthenticatedCatalogImage.vue'
import ProductForm from '@/components/ProductForm.vue'
import SyntheticNotice from '@/components/SyntheticNotice.vue'
import { runtimeConfig } from '@/config/runtime'
import { session } from '@/state/session'

const route = useRoute(); const product = ref<ProductDetailDto | null>(null); const loading = ref(true); const busy = ref(false); const editing = ref(false); const error = ref(''); const success = ref('')
const section = computed(() => String(route.params.section ?? 'overview'))
const writable = computed(() => runtimeConfig.authMode === 'password' && session.canAccess(['products.write']))
const formValue = computed<ProductInput | undefined>(() => product.value ? ({
  productCode: product.value.productCode, productName: product.value.productName, productType: product.value.productType,
  description: product.value.description, status: product.value.status, version: product.value.version,
  variants: product.value.variants.map(item => ({ id: item.id, skuCode: item.skuCode, variantName: item.variantName,
    itemDimensions: item.itemDimensions, packageDimensions: item.packageDimensions, packageWeightGrams: item.packageWeightGrams,
    status: item.status, imageAssetId: item.imageAssetId, imageUrl: item.imageUrl, version: item.version,
    suppliers: item.suppliers ?? [], materials: item.materials ?? [] })),
}) : undefined)
async function load() { loading.value = true; error.value = ''; try { product.value = await domainApi.product(String(route.params.productId)) } catch (exception) { error.value = exception instanceof ApiError ? exception.message : '产品详情加载失败。' } finally { loading.value = false } }
async function save(input: ProductInput) { if (!product.value) return; busy.value = true; error.value = ''; success.value = ''; try { product.value = await domainApi.updateProduct(product.value.id, input); editing.value = false; success.value = '产品、SKU 及关联资料已更新。' } catch (exception) { error.value = exception instanceof ApiError ? exception.message : '产品更新失败。' } finally { busy.value = false } }
async function disable() { if (!product.value || !window.confirm('确认停用该产品？历史 SKU 和引用将保留。')) return; busy.value = true; error.value = ''; try { product.value = await domainApi.disableProduct(product.value.id); editing.value = false; success.value = '产品已停用。' } catch (exception) { error.value = exception instanceof ApiError ? exception.message : '产品停用失败。' } finally { busy.value = false } }
onMounted(load)
</script>

<template>
  <section class="page-stack">
    <SyntheticNotice v-if="runtimeConfig.authMode === 'mock'" />
    <p
      v-if="error"
      class="data-state data-state--error"
      role="alert"
    >
      {{ error }}
    </p><p
      v-if="success"
      class="data-state data-state--success"
      role="status"
    >
      {{ success }}
    </p><p
      v-if="loading"
      class="data-state"
    >
      正在加载…
    </p>
    <ProductForm
      v-else-if="editing && formValue"
      :key="product?.updatedAt ?? product?.version ?? 'product'"
      :initial="formValue"
      :busy="busy"
      submit-label="保存修改"
      @submit="save"
    />
    <template v-else-if="product">
      <article class="panel compact-panel">
        <div class="card-heading">
          <div><strong>{{ product.productCode }}</strong><h2>{{ product.productName }}</h2></div><span class="placeholder-badge">{{ product.status }}</span>
        </div>
        <p>{{ product.productType || '未填写产品类型' }} · {{ product.skuCount }} 个 SKU</p><p>{{ product.description || '暂无描述' }}</p>
        <div
          v-if="writable"
          class="table-actions"
        >
          <button
            type="button"
            @click="editing = true"
          >
            编辑
          </button><button
            type="button"
            class="danger-button"
            :disabled="busy || product.status === 'DISABLED'"
            @click="disable"
          >
            停用
          </button>
        </div>
      </article>
      <nav
        class="detail-tabs"
        aria-label="产品详情"
      >
        <RouterLink :to="`/products/${product.id}/overview`">
          基础信息
        </RouterLink><RouterLink :to="`/products/${product.id}/variants`">
          SKU
        </RouterLink><RouterLink :to="`/products/${product.id}/materials`">
          关联辅料
        </RouterLink><RouterLink :to="`/products/${product.id}/changes`">
          变更记录
        </RouterLink>
      </nav>
      <article
        v-if="section === 'overview'"
        class="panel compact-panel"
      >
        <h2>基础信息</h2><p>产品名称：{{ product.productName }}</p><p>更新时间：{{ product.updatedAt || '—' }}</p>
      </article>
      <template v-else-if="section === 'variants'">
        <article
          v-for="variant in product.variants"
          :key="variant.id ?? variant.skuCode"
          class="panel compact-panel sku-detail-card"
        >
          <AuthenticatedCatalogImage
            v-if="variant.imageAssetId"
            :image-id="variant.imageAssetId"
            :alt="`${variant.variantName} 主图`"
          /><img
            v-else-if="variant.imageUrl"
            class="catalog-image"
            :src="variant.imageUrl"
            :alt="`${variant.variantName} 主图`"
            loading="lazy"
            referrerpolicy="no-referrer"
          ><span
            v-else
            class="catalog-image catalog-image--placeholder"
            role="img"
            :aria-label="`${variant.variantName} 主图（无可用图片）`"
          >图</span>
          <div>
            <div class="card-heading">
              <div><strong>{{ variant.skuCode }}</strong><h2>{{ variant.variantName }}</h2></div><span>{{ variant.status }}</span>
            </div>
            <p>{{ variant.packageWeightGrams == null ? '未填写包装重量' : `${variant.packageWeightGrams} 克` }} · {{ variant.itemDimensions || '未填写产品尺寸' }} · {{ variant.packageDimensions || '未填写包装尺寸' }}</p>
            <p v-if="product.supplierDetailsAvailable">
              供应商：{{ variant.suppliers.length ? `${variant.suppliers.length} 个` : '未关联' }}
            </p><p v-else>
              供应商：无查看权限
            </p>
            <details v-if="variant.attributes?.length">
              <summary>历史动态属性（只读）</summary><span
                v-for="attribute in variant.attributes"
                :key="attribute.attributeCode"
                class="placeholder-badge"
              >{{ attribute.attributeCode }} = {{ attribute.attributeValue }}</span>
            </details>
          </div>
        </article>
      </template>
      <article
        v-else-if="section === 'materials'"
        class="panel table-panel"
      >
        <h2>关联辅料汇总</h2><table class="data-table">
          <thead><tr><th>SKU</th><th>辅料</th><th>标准用量</th><th>损耗率</th><th>状态</th></tr></thead><tbody>
            <template
              v-for="variant in product.variants"
              :key="variant.id"
            >
              <tr
                v-for="material in variant.materials"
                :key="material.materialId"
              >
                <td>{{ variant.skuCode }}</td><td>{{ material.materialId }}</td><td>{{ material.standardQuantity }} {{ material.unit }}</td><td>{{ material.lossRate }}</td><td>{{ material.enabled ? '启用' : '停用' }}</td>
              </tr>
            </template>
          </tbody>
        </table><p
          v-if="product.variants.every(item => !item.materials.length)"
          class="data-state"
        >
          尚未关联辅料。
        </p>
      </article>
      <article
        v-else
        class="panel compact-panel"
      >
        <h2>变更记录</h2><p>产品、SKU、图片、供应商和辅料关系的写操作均已进入审计日志。</p><RouterLink to="/system/audit">
          查看审计日志
        </RouterLink>
      </article>
    </template>
  </section>
</template>
