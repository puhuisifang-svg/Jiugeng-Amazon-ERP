<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { domainApi } from '@/api/domain'
import type { MaterialDto } from '@/api/resources'
import SyntheticNotice from '@/components/SyntheticNotice.vue'
import { session } from '@/state/session'

const items = ref<MaterialDto[]>([]); const loading = ref(true); const saving = ref(false); const error = ref(''); const success = ref('')
const materialCode = ref(''); const name = ref(''); const unit = ref('PCS')
const writable = computed(() => session.canAccess(['materials.write']))
async function load() { loading.value = true; try { items.value = (await domainApi.materials()).items } catch { error.value = '辅料模拟数据加载失败。' } finally { loading.value = false } }
async function create() {
  saving.value = true; error.value = ''; success.value = ''
  try { const response = await domainApi.createMaterial({ materialCode: materialCode.value.trim(), name: name.value.trim(), unit: unit.value.trim() }); items.value.unshift(response.item); materialCode.value = ''; name.value = ''; success.value = '模拟辅料已创建。' }
  catch { error.value = '创建失败：请检查编码唯一性和 mock 后端状态。' } finally { saving.value = false }
}
onMounted(load)
</script>
<template>
  <section class="page-stack">
    <SyntheticNotice />
    <form
      v-if="writable"
      class="panel operation-form"
      @submit.prevent="create"
    >
      <h2>新增模拟辅料</h2><label>辅料编码<input
        v-model="materialCode"
        required
      ></label><label>辅料名称<input
        v-model="name"
        required
      ></label><label>单位<input
        v-model="unit"
        required
      ></label><button
        :disabled="saving"
        type="submit"
      >
        {{ saving ? '保存中…' : '新增辅料' }}
      </button>
    </form>
    <p
      v-if="success"
      class="data-state data-state--success"
      role="status"
    >
      {{ success }}
    </p><p
      v-if="error"
      class="data-state data-state--error"
      role="alert"
    >
      {{ error }}
    </p><p
      v-if="loading"
      class="data-state"
    >
      正在加载…
    </p>
    <article
      v-else
      class="panel table-panel"
    >
      <table class="data-table">
        <thead><tr><th>编码</th><th>名称</th><th>单位</th><th>状态</th></tr></thead><tbody>
          <tr
            v-for="item in items"
            :key="item.id"
          >
            <td>{{ item.materialCode }}</td><td>{{ item.name }}</td><td>{{ item.unit }}</td><td>{{ item.status }}</td>
          </tr>
        </tbody>
      </table>
    </article>
  </section>
</template>
