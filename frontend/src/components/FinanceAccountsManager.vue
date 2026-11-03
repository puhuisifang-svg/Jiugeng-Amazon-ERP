<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AccessDialog from './AccessDialog.vue'
import ListPagination from './ListPagination.vue'
import { financeApi, type FinanceAccount, type FinanceShop } from '@/api/finance'
const props=defineProps<{shop:FinanceShop}>()
defineEmits<{close:[]}>()
const rows=ref<FinanceAccount[]>([]),editing=ref<FinanceAccount>(),name=ref(''),busy=ref(false),loading=ref(true),error=ref(''),notice=ref(''),page=ref(1),size=ref(10)
const dirty=computed(()=>name.value!==(editing.value?.name??''))
const shown=computed(()=>rows.value.slice((page.value-1)*size.value,page.value*size.value))
const message=(e:unknown)=>e instanceof Error?e.message:'操作失败，请重试'
function edit(a:FinanceAccount){editing.value=a;name.value=a.name;error.value=''}
function cancel(){editing.value=undefined;name.value='';error.value=''}
async function save(a?:FinanceAccount){if(busy.value)return;busy.value=true;error.value='';notice.value='';try{const target=a??editing.value;const saved=await financeApi.saveAccount(props.shop.id,{name:a?a.name:name.value.trim(),active:a?!a.active:(editing.value?.active??true),...(target?{version:target.version}:{})},target?.id);rows.value=target?rows.value.map(r=>r.id===saved.id?saved:r):[saved,...rows.value];if(!target)page.value=1;if(!a)cancel();notice.value=a?(saved.active?'账户已启用':'账户已停用'):'账户已保存'}catch(e){error.value=message(e)}finally{busy.value=false}}
onMounted(async()=>{try{rows.value=await financeApi.accounts(props.shop.id)}catch(e){error.value=message(e)}finally{loading.value=false}})
</script>
<template>
  <AccessDialog
    :title="`${shop.name} · 收支账户`"
    :dirty="dirty"
    :busy="busy"
    dialog-class="finance-accounts-dialog"
    @close="$emit('close')"
  >
    <p class="access-muted">
      账户仅用于当前店铺。更名、停用不会改变历史账单。
    </p>
    <form
      class="account-editor"
      @submit.prevent="save()"
    >
      <label>{{ editing?'编辑账户':'新增账户' }}<input
        v-model="name"
        required
        maxlength="100"
        placeholder="填写账户名称"
        :disabled="busy || loading"
      ></label>
      <button
        type="submit"
        class="primary-button"
        :disabled="busy || loading || !name.trim() || !dirty"
      >
        {{ busy?'保存中…':editing?'保存':'添加' }}
      </button>
      <button
        v-if="editing"
        type="button"
        :disabled="busy"
        @click="cancel"
      >
        取消
      </button>
    </form>
    <p
      v-if="error"
      role="alert"
      class="access-danger"
    >
      {{ error }}
    </p>
    <p
      v-if="notice"
      role="status"
      class="access-muted"
    >
      {{ notice }}
    </p>
    <div class="account-table">
      <table class="data-table">
        <thead><tr><th>账户名称</th><th>状态</th><th>操作</th></tr></thead><tbody>
          <tr
            v-for="a in shown"
            :key="a.id"
          >
            <td>{{ a.name }}</td><td>{{ a.active?'启用':'停用' }}</td><td>
              <button
                type="button"
                :disabled="busy || dirty"
                @click="edit(a)"
              >
                编辑
              </button><button
                type="button"
                :disabled="busy || dirty"
                @click="save(a)"
              >
                {{ a.active?'停用':'启用' }}
              </button>
            </td>
          </tr>
          <tr v-if="!shown.length">
            <td colspan="3">
              {{ loading?'加载中…':'暂无账户，请在上方添加' }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <ListPagination
      :page="page"
      :page-size="size"
      :total="rows.length"
      @update:page="page=$event"
      @update:page-size="size=$event;page=1"
    />
  </AccessDialog>
</template>
<style>
.finance-accounts-dialog .account-editor{display:flex;align-items:flex-end;gap:8px;margin:16px 0}.account-editor label{display:grid;gap:6px;flex:1;font-size:13px;min-width:0}#app .account-editor input{height:34px;width:100%;padding:6px 10px;border:1px solid var(--line);border-radius:5px;font-size:13px}.finance-accounts-dialog .account-table{overflow:auto;max-height:45vh}.finance-accounts-dialog table{width:100%}.finance-accounts-dialog th{position:sticky;top:0;background:#f6f8fa}.finance-accounts-dialog td{padding:8px;overflow-wrap:anywhere}.finance-accounts-dialog td button{border:0;background:transparent;color:#2864c5}.finance-accounts-dialog .account-editor button{height:34px;white-space:nowrap}.finance-accounts-dialog .account-editor .primary-button{background:#21834a;border-color:#21834a;color:white}
</style>
