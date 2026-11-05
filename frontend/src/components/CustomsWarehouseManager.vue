<script setup lang="ts">
import { computed,onMounted,ref } from 'vue'
import { confirmAction } from '@/state/uiConfirmation'
import AccessDialog from './AccessDialog.vue'
import WarehousePageShell from './WarehousePageShell.vue'
import DatePicker from './DatePicker.vue'
import TableViewport from './TableViewport.vue'
import ListPagination from './ListPagination.vue'
import { customsWarehousesApi,type CustomsWarehouse } from '@/api/customsDocuments'
const props=defineProps<{writable:boolean;initialCode?:string;standalone?:boolean}>()
const emit=defineEmits<{close:[];saved:[CustomsWarehouse]}>()
const rows=ref<CustomsWarehouse[]>([]),query=ref(''),page=ref(1),size=ref(20),busy=ref(false),error=ref(''),notice=ref(''),draft=ref<CustomsWarehouse>(),editing=ref(false),baseline=ref(''),importing=ref(false),paste=ref('')
const blank=(code=''):CustomsWarehouse=>({code,country:'US',region:'',city:'',postalCode:'',address:'',source:'',verifiedOn:''})
const dirty=computed(()=>draft.value?JSON.stringify(draft.value)!==baseline.value:importing.value&&!!paste.value)
const filtered=computed(()=>rows.value.filter(r=>[r.code,r.city,r.address].join(' ').toLowerCase().includes(query.value.toLowerCase())))
const visible=computed(()=>filtered.value.slice((page.value-1)*size.value,page.value*size.value))
const imported=computed(()=>paste.value.trim().split(/\r?\n/).filter(Boolean).filter((line,i)=>!(i===0&&line.startsWith('仓库编码\t'))).map(line=>{const [code='',region='',city='',postalCode='',address='',source='',verifiedOn='']=line.split('\t');return {...blank(),code:code.trim().toUpperCase(),region,city,postalCode,address,source,verifiedOn}}))
function edit(row?:CustomsWarehouse){editing.value=!!row;draft.value=row?{...row}:blank();baseline.value=JSON.stringify(draft.value);error.value='';notice.value=''}
async function back(){if(dirty.value&&!await confirmAction('放弃尚未保存的仓库资料并返回列表？'))return;draft.value=undefined;importing.value=false;paste.value='';error.value=''}
async function load(){busy.value=true;error.value='';try{rows.value=await customsWarehousesApi.list()}catch(e){error.value=e instanceof Error?e.message:'加载失败，请重试'}finally{busy.value=false}}
async function save(){if(!draft.value||!props.writable||busy.value)return;busy.value=true;error.value='';try{draft.value.code=draft.value.code.trim().toUpperCase();const saved=await customsWarehousesApi.save(draft.value,editing.value);emit('saved',saved);draft.value=undefined;notice.value='仓库地址已保存，历史单据保持不变';await load()}catch(e){error.value=e instanceof Error?e.message:'保存失败'}finally{busy.value=false}}
async function importRows(){if(!props.writable||busy.value)return;busy.value=true;error.value='';try{await customsWarehousesApi.import(imported.value);notice.value=`已导入 ${imported.value.length} 个仓库`;paste.value='';importing.value=false;await load()}catch(e){error.value=e instanceof Error?e.message:'导入失败'}finally{busy.value=false}}
onMounted(async()=>{await load();if(props.initialCode){const found=rows.value.find(r=>r.code===props.initialCode!.trim().toUpperCase());edit(found);if(!found){draft.value!.code=props.initialCode.trim().toUpperCase();baseline.value=JSON.stringify(draft.value)}}})
</script>
<template>
  <component
    :is="standalone ? WarehousePageShell : AccessDialog"
    :actions-visible="!!draft || importing"
    title="FBA仓库地址"
    wide
    :busy="busy"
    :dirty="dirty"
    @close="emit('close')"
  >
    <p
      v-if="error"
      role="alert"
      class="data-state--error"
    >
      {{ error }} <button
        v-if="!draft && !importing"
        type="button"
        @click="load"
      >
        重试
      </button>
    </p>
    <p
      v-if="notice"
      role="status"
    >
      {{ notice }}
    </p>
    <form
      v-if="draft"
      id="warehouse-form"
      class="warehouse-form"
      @submit.prevent="save"
    >
      <p class="wide muted">
        用于自动带入报关单据及货代单据的收货人地址；更新档案不会修改已保存单据。
      </p>
      <label>仓库编码 *<input
        v-model="draft.code"
        required
        :readonly="editing"
        :disabled="busy"
        maxlength="20"
        pattern="[A-Za-z0-9][A-Za-z0-9-]{1,19}"
        aria-label="仓库编码"
      ></label>
      <label>国家<input
        value="美国（US）"
        readonly
      ></label>
      <label>州<input
        v-model="draft.region"
        :disabled="busy"
        maxlength="100"
      ></label>
      <label>城市<input
        v-model="draft.city"
        :disabled="busy"
        maxlength="100"
      ></label>
      <label>邮编<input
        v-model="draft.postalCode"
        :disabled="busy"
        maxlength="20"
      ></label>
      <label>核对日期<DatePicker
        v-model="draft.verifiedOn"
        :disabled="busy"
      /></label>
      <label class="wide">完整收货地址 *<textarea
        v-model="draft.address"
        required
        :disabled="busy"
        maxlength="1000"
        rows="3"
        placeholder="按货件 Ship to 地址填写，包含街道、城市、州、邮编及国家"
      /></label>
      <label class="wide">地址来源 *<input
        v-model="draft.source"
        required
        :disabled="busy"
        maxlength="1000"
        placeholder="例如：亚马逊货件地址、官方地址清单链接"
      ></label>
    </form>
    <section
      v-else-if="importing"
      class="warehouse-import"
    >
      <p>从 Excel 复制以下七列并粘贴，每行一个仓库（最多500条）。国家统一美国；重复编码将整批拒绝，不覆盖已有资料。</p>
      <p>仓库编码 → 州 → 城市 → 邮编 → 完整收货地址 → 地址来源 → 核对日期（YYYY-MM-DD，可空）</p>
      <textarea
        v-model="paste"
        :disabled="busy"
        rows="8"
        aria-label="批量导入仓库资料"
        placeholder="粘贴以制表符分隔的仓库数据"
      />
      <p>待导入 {{ paste.trim()?imported.length:0 }} 条</p>
      <TableViewport v-if="paste.trim()">
        <table class="data-table">
          <thead><tr><th>仓库编码</th><th>完整收货地址</th><th>来源</th></tr></thead><tbody>
            <tr
              v-for="(r,i) in imported"
              :key="i"
            >
              <td>{{ r.code }}</td><td>{{ r.address || '缺少地址' }}</td><td>{{ r.source || '缺少来源' }}</td>
            </tr>
          </tbody>
        </table>
      </TableViewport>
    </section>
    <template v-else>
      <div class="warehouse-toolbar">
        <input
          v-model="query"
          aria-label="搜索仓库"
          placeholder="编码、城市或地址"
          @input="page=1"
        ><button
          v-if="writable"
          type="button"
          :disabled="busy"
          @click="edit()"
        >
          新增仓库
        </button><button
          v-if="writable"
          type="button"
          :disabled="busy"
          @click="importing=true;error='';notice=''"
        >
          批量导入
        </button>
      </div>
      <TableViewport>
        <table class="data-table warehouse-table">
          <thead>
            <tr>
              <th>仓库编码</th><th>国家 / 州 / 城市</th><th>邮编</th><th>完整收货地址</th><th>来源</th><th>核对日期</th><th v-if="writable">
                操作
              </th>
            </tr>
          </thead><tbody>
            <tr
              v-for="r in visible"
              :key="r.code"
            >
              <td>{{ r.code }}</td><td>{{ r.country }} / {{ r.region }} / {{ r.city }}</td><td>{{ r.postalCode || '—' }}</td><td>{{ r.address }}</td><td>{{ r.source }}</td><td>{{ r.verifiedOn || '待核对' }}</td><td v-if="writable">
                <button
                  type="button"
                  class="text-action"
                  :disabled="busy"
                  @click="edit(r)"
                >
                  编辑
                </button>
              </td>
            </tr><tr v-if="!visible.length">
              <td :colspan="writable?7:6">
                {{ busy?'正在加载…':'暂无匹配仓库' }}
              </td>
            </tr>
          </tbody>
        </table>
      </TableViewport>
      <ListPagination
        v-model:page="page"
        v-model:page-size="size"
        :total="filtered.length"
        :disabled="busy"
      />
    </template>
    <template #footer>
      <button
        v-if="draft || importing"
        type="button"
        :disabled="busy"
        @click="back"
      >
        返回列表
      </button>
      <button
        v-if="draft && writable"
        form="warehouse-form"
        type="submit"
        class="ui-button-primary"
        :disabled="busy"
      >
        {{ busy?'保存中…':editing?'保存修改':'创建仓库' }}
      </button>
      <button
        v-else-if="importing"
        type="button"
        class="ui-button-primary"
        :disabled="busy || !paste.trim() || imported.length>500"
        @click="importRows"
      >
        {{ busy?'导入中…':'确认导入' }}
      </button>
    </template>
  </component>
</template>
<style scoped>
.warehouse-form{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px 16px}.warehouse-form label{font-size:13px;display:flex;flex-direction:column;gap:6px;min-width:0}.warehouse-form input,.warehouse-form textarea,.warehouse-import textarea,.warehouse-toolbar input{box-sizing:border-box;width:100%;border:1px solid var(--line);border-radius:4px;padding:8px 10px;font-size:13px;min-height:40px}.wide{grid-column:1/-1}.muted,.warehouse-import p{font-size:12px;color:var(--muted);line-height:1.7}.warehouse-toolbar{display:flex;gap:8px;margin-bottom:12px}.warehouse-toolbar input{max-width:300px;min-height:32px}.data-table td{max-width:300px;white-space:normal;overflow-wrap:anywhere;font-size:13px}.warehouse-import textarea{width:100%}@media(max-width:600px){.warehouse-form{grid-template-columns:1fr}.warehouse-toolbar{flex-wrap:wrap}}
.warehouse-table{table-layout:fixed;width:100%;min-width:900px}.warehouse-table th:nth-child(1){width:8%}.warehouse-table th:nth-child(2){width:16%}.warehouse-table th:nth-child(3){width:8%}.warehouse-table th:nth-child(4){width:25%}.warehouse-table th:nth-child(5){width:25%}.warehouse-table th:nth-child(6){width:11%}.warehouse-table th:nth-child(7){width:7%}
.warehouse-table .text-action{min-height:28px;padding:4px 6px;background:transparent;border:0;color:#2864c5;border-radius:4px;cursor:pointer}
</style>
