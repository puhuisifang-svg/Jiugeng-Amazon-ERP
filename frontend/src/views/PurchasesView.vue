<script setup lang="ts">
import { costsApi } from '@/api/costs'
import { confirmAction } from '@/state/uiConfirmation'
import AccessDialog from '@/components/AccessDialog.vue'
import SingleFileUpload from '@/components/SingleFileUpload.vue'
import { catalogApi } from '@/api/catalog'
import PurchaseItemImage from '@/components/PurchaseItemImage.vue'
import PurchaseLogisticsDialog from '@/components/PurchaseLogisticsDialog.vue'
import { vSingleSelect } from '@/directives/singleSelect'
import DatePicker from '@/components/DatePicker.vue'
import ListPagination from '@/components/ListPagination.vue'
import SuccessToast from "@/components/SuccessToast.vue"
import TableViewport from "@/components/TableViewport.vue"
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ApiError } from '@/api/client'
import { domainApi, type SupplierDto } from '@/api/domain'
import { purchaseApi, type PurchaseOrder, type PurchaseOrderInput, type PurchaseOrderType, type PurchaseReferenceItem, type PurchaseStatus } from '@/api/purchases'
import { session } from '@/state/session'

const route = useRoute(); const router = useRouter()
const orders = ref<PurchaseOrder[]>([])
const suppliers = ref<SupplierDto[]>([]); const references = ref<PurchaseReferenceItem[]>([])
const loading = ref(false); const referenceLoading = ref(false); const supplierLoading = ref(false); const saving = ref(false)
const error = ref(''); const formError = ref(''); const success = ref('')
const type = computed<PurchaseOrderType>(() => route.query.type === 'MANUAL' ? 'MANUAL' : route.query.type === 'MATERIAL' ? 'MATERIAL' : 'PRODUCT')
const status = ref<PurchaseStatus | ''>(''); const keyword = ref(''); const delivery = ref(''); const page = ref(1); const size = ref(20)
const editing = ref<PurchaseOrder>(); const showOrderForm = ref(false)
const form = reactive<PurchaseOrderInput>({ orderType: 'PRODUCT', supplierId: '', currency: 'CNY', remark: '', lines: [] })
const logisticsOrder = ref<PurchaseOrder>()
function logisticsSaved(order: PurchaseOrder) { orders.value = orders.value.map(item => item.id === order.id ? order : item); logisticsOrder.value = undefined; success.value = '运单资料已保存。' }
const deleting = ref('')
const removable = computed(() => session.canAccess(['purchases.write', 'purchases.cancel']))
const hasReceipts = (order: PurchaseOrder) => ['PARTIALLY_RECEIVED', 'COMPLETED'].includes(order.status) || order.lines.some(line => line.receivedQuantity > 0)
async function removeOrder(order: PurchaseOrder) {
  if (deleting.value || !removable.value || hasReceipts(order)) return
  deleting.value = order.id
  try {
    if (!await confirmAction(`确认删除采购单「${order.orderNo}」？删除后将移出采购列表及待接收清单，并解除该单据的供应商关联。此操作不可恢复。`)) return
    error.value = ''; success.value = ''
    await purchaseApi.remove(order.id, order.version)
    orders.value = orders.value.filter(item => item.id !== order.id)
    page.value = Math.min(page.value, pageCount.value)
    success.value = '采购单已删除。'
  } catch (e) { error.value = message(e, '删除结果未确认，请刷新列表核对后再操作。') }
  finally { deleting.value = '' }
}
const writable = computed(() => session.canAccess(['purchases.write']))
const statusLabels: Record<PurchaseStatus, string> = { DRAFT: '草稿', SUBMITTED: '待收货', PARTIALLY_RECEIVED: '部分收货', COMPLETED: '已完成', CANCELLED: '已取消 / 余量关闭' }
const typeLabels = { PRODUCT: '产品采购', MATERIAL: '辅料采购', MANUAL: '日常物品采购' }
const uploading = ref(false), uploadTarget = ref<number>()
async function uploadItem(file:File,index:number){uploading.value=true;try{const image=await catalogApi.uploadImage('PURCHASE_ITEM',file);if(form.lines[index])form.lines[index]!.imageAssetId=image.id;uploadTarget.value=undefined}catch(e){formError.value=message(e,'图片上传失败');throw e}finally{uploading.value=false}}
function moveType(step:number){const types:PurchaseOrderType[]=['PRODUCT','MATERIAL','MANUAL'];void changeType(types[(types.indexOf(type.value)+step+3)%3]!)}
const today = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai' }).format(new Date())
const overdue = (order: PurchaseOrder) => ['SUBMITTED', 'PARTIALLY_RECEIVED'].includes(order.status) && !!order.expectedDeliveryDate && order.expectedDeliveryDate < today
const typedOrders = computed(() => orders.value.filter(order => order.orderType === type.value))
const filtered = computed(() => typedOrders.value.filter(order => {
  const q = keyword.value.trim().toLowerCase()
  return (!status.value || order.status === status.value)
    && (!q || [order.orderNo, order.supplierName, ...order.lines.flatMap(line => [line.itemCode, line.itemName])].some(value => value.toLowerCase().includes(q)))
    && (!delivery.value || (delivery.value === 'overdue' ? overdue(order) : !order.expectedDeliveryDate))
}))
const pageCount = computed(() => Math.max(1, Math.ceil(filtered.value.length / size.value)))
const visibleOrders = computed(() => filtered.value.slice((page.value - 1) * size.value, page.value * size.value))
const total = (order: PurchaseOrder) => order.lines.reduce((sum, line) => sum + line.orderedQuantity * line.unitPrice, 0).toFixed(2)
const formTotal = computed(() => form.lines.reduce((sum, line) => sum + Number(line.quantity || 0) * Number(line.unitPrice || 0), 0).toFixed(2))
const requestedMaterials = ref<string[]>([])
let referenceSequence = 0
function message(exception: unknown, fallback: string) { return exception instanceof ApiError ? exception.message : fallback }
function resetFilters() { status.value = ''; keyword.value = ''; delivery.value = ''; page.value = 1 }
async function changeType(value: PurchaseOrderType) { resetFilters(); await router.replace({ query: { type: value } }) }
function closeForm() { if (saving.value || uploading.value) return; referenceSequence++; showOrderForm.value = false; requestedMaterials.value = []; references.value = []; formError.value = '' }
async function load() {
  loading.value = true; error.value = ''
  try { orders.value = await purchaseApi.orders() }
  catch (exception) { error.value = message(exception, '采购数据加载失败，请重试。') }
  finally { loading.value = false }
}
async function loadSuppliers() {
  supplierLoading.value = true
  try {
    const result: SupplierDto[] = []; let currentPage = 0
    while (true) {
      const response = await domainApi.suppliers({ status: 'ACTIVE', size: 100, page: currentPage++ })
      result.push(...response.items)
      if (!response.items.length || result.length >= response.totalCount) break
    }
    suppliers.value = result
  } catch (exception) { formError.value = message(exception, '供应商加载失败，请关闭后重试。') }
  finally { supplierLoading.value = false }
}
async function loadReferences(resetLines = true) {
  const sequence = ++referenceSequence
  references.value = []; formError.value = ''
  if (resetLines) form.lines = [{ itemId: '', quantity: 1, unitPrice: 0 }]
  if (form.orderType === 'MANUAL' || !form.supplierId) { referenceLoading.value = false; return }
  referenceLoading.value = true
  try { const result = await purchaseApi.references(form.orderType, form.supplierId); if (sequence === referenceSequence) {
    references.value = result
    if(resetLines && requestedMaterials.value.length && form.orderType === 'MATERIAL') {
      const selected=requestedMaterials.value.map(id=>result.find(item=>item.id===id))
      if(selected.some(item=>!item)) {
        formError.value='该供应商未关联当前辅料（或部分所选辅料），请换共同供应商或分批采购。'
        return
      }
      const lines=selected.map(item=>({itemId:item!.id,quantity:1,unitPrice:0}))
      const warnings:string[]=[]
      if(session.canAccess(['costs.read'])) {
        await Promise.all(selected.map(async (item,index)=>{
          try {
            const price=await costsApi.price(item!.id)
            if(!price) warnings.push(item!.name+'尚未维护成本价')
            else if(price.unit!==item!.unit || form.currency!=='CNY') warnings.push(item!.name+'：单位或币制与采购单不一致')
            else if(!validNumber(Number(price.netPrice),false)) warnings.push(item!.name+'：成本价超出四位小数')
            else lines[index]!.unitPrice=Number(price.netPrice)
          }catch {warnings.push(item!.name+'：成本价读取失败')}
        }))
      }else warnings.push('当前账号无成本价查看权限')
      if(sequence!==referenceSequence)return
      form.lines=lines
      if(warnings.length)formError.value=warnings.join('；')+'，请手动填写本次采购单价。'
    }
  } }
  catch (exception) { if (sequence === referenceSequence) formError.value = message(exception, '可采购项目加载失败，请重新选择供应商。') }
  finally { if (sequence === referenceSequence) referenceLoading.value = false }
}
async function openCreate() {
  requestedMaterials.value=[]; editing.value = undefined; formError.value = ''; references.value = []; referenceSequence++
  Object.assign(form, { orderType: type.value, supplierId: '', supplierName: '', currency: 'CNY', remark: '', version: undefined, expectedDeliveryDate: '', deliveryAddress: '', lines: [{ itemId: '', quantity: 1, unitPrice: 0 }] })
  showOrderForm.value = true; if(type.value!=='MANUAL')await loadSuppliers()
}
async function createFromMaterials(ids:string[]) {
  if(!writable.value || !session.canAccess(['materials.read','suppliers.read']))return
  await openCreate()
  if(!showOrderForm.value || form.orderType!=='MATERIAL')return
  if(ids.length>200){formError.value='每张采购单最多 200 项，请分批选择。';return}
  requestedMaterials.value=ids
  try {
    const groups=await Promise.all(ids.map(id=>domainApi.materialSupplierLinks(id)))
    if(!showOrderForm.value || requestedMaterials.value.join(',')!==ids.join(','))return
    const available=(groups[0]??[]).filter(link=>suppliers.value.some(s=>s.id===link.supplierId)&&groups.every(group=>group.some(l=>l.supplierId===link.supplierId)))
    const selected=available.find(link=>groups.every(group=>group.some(l=>l.supplierId===link.supplierId&&l.primary)))??available[0]
    if(selected){form.supplierId=selected.supplierId;await loadReferences()}
    else formError.value=ids.length===1?'当前辅料未关联启用的供应商，请先回到辅料管理编辑供应商关系，再发起采购。':'所选辅料没有共同的启用供应商，请按供应商分批勾选采购，或先维护供应商关系。'
  }catch(e){formError.value=message(e,'辅料供应商关系加载失败，请关闭后从辅料列表重试。')}
}
watch(()=>[route.query.materialId,route.query.materialIds], async ([id,batch])=>{
  const value=typeof batch==='string'?batch:typeof id==='string'?id:''
  if(!value||type.value!=='MATERIAL')return
  await createFromMaterials([...new Set(value.split(',').filter(Boolean))])
  if(route.query.materialId===id&&route.query.materialIds===batch){const query={...route.query};delete query.materialId;delete query.materialIds;await router.replace({query})}
},{immediate:true})
async function openEdit(order: PurchaseOrder) {
  editing.value = order; formError.value = ''; showOrderForm.value = true
  Object.assign(form, { orderType: order.orderType, supplierId: order.supplierId, supplierName: order.supplierName, currency: order.currency, remark: order.remark ?? '', version: order.version, expectedDeliveryDate: order.expectedDeliveryDate ?? '', deliveryAddress: order.deliveryAddress ?? '', lines: order.lines.map(line => ({ itemId: line.itemId, quantity: line.orderedQuantity, unitPrice: line.unitPrice, itemName:line.itemName, unit:line.unit, specification:line.specification??'', imageAssetId:line.imageAssetId })) })
  if(order.orderType!=='MANUAL')await Promise.all([loadSuppliers(), loadReferences(false)])
}
function addLine() { if (form.lines.length < 200) form.lines.push({ itemId: '', quantity: 1, unitPrice: 0 }) }
function validNumber(value: number, positive: boolean) { return Number.isFinite(Number(value)) && (positive ? Number(value) > 0 : Number(value) >= 0) && /^\d+(\.\d{1,4})?$/.test(String(value)) }
async function saveOrder() {
  if (saving.value || uploading.value || referenceLoading.value) return
  formError.value = ''; success.value = ''
  if (form.orderType !== 'MANUAL' && (!form.supplierId || !form.lines.length || form.lines.some(line => !references.value.some(item => item.id === line.itemId)))) { formError.value = '请选择供应商及有效采购项目。'; return }
  if(form.orderType==='MANUAL' && form.lines.some(line=>!line.itemName?.trim()||!line.unit?.trim())){formError.value='请填写物品名称及单位。';return}
  if (form.orderType !== 'MANUAL' && new Set(form.lines.map(line => line.itemId)).size !== form.lines.length) { formError.value = '采购项目不可重复，请合并相同项目的数量。'; return }
  if (form.lines.some(line => !validNumber(line.quantity, true) || !validNumber(line.unitPrice, false))) { formError.value = '数量必须大于 0，单价不可为负数，最多支持四位小数。'; return }
  saving.value = true
  try {
    const payload = { ...form, expectedDeliveryDate: form.expectedDeliveryDate || null, lines: form.lines.map(line => ({ ...line })) }
    if (editing.value) await purchaseApi.update(editing.value.id, payload); else await purchaseApi.create(payload)
    success.value = editing.value ? '采购单草稿已更新。' : '采购单草稿已保存，请进入详情核对并确认下单。'
    saving.value = false; closeForm(); await load()
  } catch (exception) { formError.value = message(exception, '保存结果未确认，请先刷新列表检查是否已保存，避免重复建单。') }
  finally { saving.value = false }
}
watch([type, status, keyword, delivery, size], () => { page.value = 1 })
watch(pageCount, value => { if (page.value > value) page.value = value })
onMounted(load)
</script>

<template>
  <section class="page-stack purchase-page">
    <header class="panel purchase-header">
      <div>
        <p class="eyebrow">
          PURCHASE ORDERS
        </p><h1>采购管理</h1><p>建单 → 确认下单 → 分批收货 → 完成，采购与到货进度在一处管理。</p>
      </div>
      <button
        v-if="writable"
        class="primary-link"
        type="button"
        :disabled="showOrderForm"
        @click="openCreate"
      >
        新建{{ typeLabels[type] }}单
      </button>
    </header>
    <div
      class="purchase-tabs"
      role="tablist"
      aria-label="采购类型"
    >
      <button
        v-for="(label, key) in typeLabels"
        :id="`tab-${key}`"
        :key="key"
        type="button"
        role="tab"
        :aria-selected="type === key"
        aria-controls="purchase-orders-panel"
        @click="changeType(key)"
        @keydown.right.prevent="moveType(1)"
        @keydown.left.prevent="moveType(-1)"
      >
        {{ label }} <span>{{ orders.filter(order => order.orderType === key).length }}</span>
      </button>
    </div>
    <p
      v-if="error"
      class="error-message"
      role="alert"
    >
      {{ error }}
    </p><SuccessToast :message="success" />
    <section class="panel filter-panel purchase-order-filters">
      <label>搜索<input
        v-model="keyword"
        placeholder="采购单号 / 供应商 / SKU / 辅料"
        aria-label="搜索采购单"
      ></label>
      <label>状态<select
        v-model="status"
        v-single-select
      ><option value="">全部状态</option><option
        v-for="(label,key) in statusLabels"
        :key="key"
        :value="key"
      >{{ label }}</option></select></label>
      <label>交期<select
        v-model="delivery"
        v-single-select
      ><option value="">全部交期</option><option value="overdue">已逾期未收齐</option><option value="missing">未填写交期</option></select></label>
      <button
        type="button"
        @click="resetFilters"
      >
        重置
      </button><button
        type="button"
        :disabled="loading"
        @click="load"
      >
        {{ loading ? '刷新中…' : '刷新' }}
      </button>
    </section>
    <section
      id="purchase-orders-panel"
      class="panel table-panel"
      role="tabpanel"
      :aria-labelledby="`tab-${type}`"
      :aria-busy="loading"
    >
      <p class="purchase-list-note">
        {{ type === 'MANUAL' ? '直接录入未建档的日常物品，支持图片及分批收货，不计入产品或辅料库存。' : type === 'PRODUCT' ? '产品按 SKU 采购，收货登记到货数量；暂不生成成品库存。' : '辅料按档案单位采购，登记收货后同步增加固定仓库存。' }} 收货与历史记录在采购单详情查看。
      </p>
      <p
        v-if="loading"
        class="purchase-list-note"
      >
        正在加载采购单…
      </p>
      <div
        v-else-if="!filtered.length"
        class="empty-state"
      >
        <span class="empty-state__icon">单</span><h2>暂无符合条件的{{ typeLabels[type] }}单</h2><p>可调整筛选条件，或先维护供应商供货关系后新建采购单。</p>
      </div>
      <div
        v-else
        class="table-scroll"
      >
        <TableViewport>
          <table class="data-table ui-action-table">
            <thead><tr><th>采购单 / 供应商</th><th>商品</th><th>物流</th><th>采购金额</th><th>收货进度</th><th>预计到货</th><th>状态</th><th>操作</th></tr></thead>
            <tbody>
              <tr
                v-for="order in visibleOrders"
                :key="order.id"
              >
                <td>
                  <RouterLink
                    class="text-link"
                    :to="`/purchases/orders/${order.id}`"
                  >
                    <strong>{{ order.orderNo }}</strong>
                  </RouterLink><small>{{ order.supplierName }}</small><small>{{ new Date(order.createdAt).toLocaleDateString() }} 创建</small>
                </td>
                <td>
                  <div
                    v-for="line in order.lines"
                    :key="line.id"
                    class="purchase-list-product"
                  >
                    <PurchaseItemImage
                      :image-asset-id="line.imageAssetId"
                      :image-url="line.imageUrl"
                      :alt="line.itemName"
                    />
                    <div><span>{{ line.itemName }}</span><small>{{ line.itemCode || '—' }}</small></div>
                  </div>
                  <small>共 {{ order.lines.length }} 项</small>
                </td>
                <td>
                  <span>{{ order.carrier || '未填写物流公司' }}</span><small>{{ order.trackingNumber || '未填写运单号' }}</small><button
                    v-if="writable || order.trackingNumber"
                    type="button"
                    class="text-link"
                    @click="logisticsOrder = order"
                  >
                    {{ order.trackingNumber ? '物流详情' : '填写运单' }}
                  </button>
                </td>
                <td>{{ order.currency }} {{ total(order) }}</td>
                <td>{{ order.lines.filter(line => line.remainingQuantity === 0).length }} / {{ order.lines.length }} 项已收齐<small>{{ order.status === 'CANCELLED' ? '未收余量已关闭' : '分项数量见详情' }}</small></td>
                <td :class="{ 'purchase-delivery-date': !!order.expectedDeliveryDate }">
                  {{ order.expectedDeliveryDate || '未约定' }}<small v-if="overdue(order)">逾期未收齐</small>
                </td>
                <td>
                  <span
                    class="purchase-status"
                    :data-status="order.status"
                  >{{ statusLabels[order.status] }}</span>
                </td>
                <td class="table-actions">
                  <RouterLink
                    class="text-link"
                    :to="`/purchases/orders/${order.id}`"
                  >
                    {{ order.status === 'DRAFT' ? '核对 / 下单' : '查看详情' }}
                  </RouterLink><button
                    v-if="writable && order.status === 'DRAFT'"
                    type="button"
                    class="secondary-button"
                    @click="openEdit(order)"
                  >
                    编辑
                  </button><button
                    v-if="removable"
                    type="button"
                    class="danger-link"
                    :disabled="!!deleting || hasReceipts(order)"
                    :title="hasReceipts(order) ? '已有收货记录，不能删除' : '删除采购单'"
                    @click="removeOrder(order)"
                  >
                    {{ deleting === order.id ? '处理中…' : '删除' }}
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </TableViewport>
      </div>
      <ListPagination
        v-model:page-size="size"
        class="purchase-pagination"
        :page="page"
        :total="filtered.length"
        :disabled="loading"
        @update:page="page = $event"
      />
    </section>
    <div
      v-if="showOrderForm"
      class="operation-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="purchase-form-title"
      @keydown.esc="closeForm"
    >
      <form
        class="panel operation-form operation-dialog__card"
        @submit.prevent="saveOrder"
      >
        <button
          type="button"
          class="operation-dialog__close"
          aria-label="关闭"
          :disabled="saving || uploading"
          @click="closeForm"
        >
          ×
        </button>
        <h2 id="purchase-form-title">
          {{ editing ? '编辑' : '新建' }}{{ typeLabels[form.orderType] }}单
        </h2>
        <p class="form-wide purchase-form-hint">
          {{ form.orderType==='MANUAL' ? '直接填写物品及图片，无需维护 SKU 或辅料；供应商 / 购买渠道可选填。' : '一单一供应商，产品与辅料不混单。' }}保存为草稿后，请在详情确认下单。
        </p>
        <p
          v-if="formError"
          class="error-message form-wide"
          role="alert"
        >
          {{ formError }}
        </p>
        <fieldset
          class="purchase-form-fields form-wide"
          :disabled="saving || uploading"
        >
          <label v-if="form.orderType==='MANUAL'">供应商 / 购买渠道<input
            v-model="form.supplierName"
            maxlength="200"
            placeholder="选填，如五金店、采购平台"
          ></label>
          <label v-else>供应商<select
            v-model="form.supplierId"
            v-single-select
            class="ui-supplier-select"
            required
            :disabled="supplierLoading"
            @change="loadReferences()"
          ><option
            value=""
            disabled
          >{{ supplierLoading ? '正在加载供应商…' : '请选择启用供应商' }}</option><option
            v-for="supplier in suppliers"
            :key="supplier.id"
            :value="supplier.id"
          >{{ supplier.name }}</option></select></label>
          <label>币种<input
            v-model="form.currency"
            required
            maxlength="3"
            pattern="[A-Za-z]{3}"
            placeholder="CNY"
          ></label>
          <label>预计到货日期<DatePicker
            v-model="form.expectedDeliveryDate"
            type="date"
            min="2000-01-01"
            max="9999-12-31"
          /></label>
          <label>收货信息<input
            v-model="form.deliveryAddress"
            maxlength="500"
            placeholder="收货地址、联系人或内部仓库说明"
          ></label>
          <p
            v-if="form.orderType!=='MANUAL' && form.supplierId && !referenceLoading && !references.length"
            class="form-wide empty-inline"
          >
            该供应商尚未关联启用的{{ form.orderType === 'PRODUCT' ? 'SKU' : '辅料' }}，请先到对应档案维护供货关系。
          </p>
          <div class="form-wide table-scroll">
            <TableViewport>
              <table
                class="data-table purchase-edit-lines"
                :class="{'manual-purchase-lines':form.orderType==='MANUAL'}"
              >
                <thead>
                  <tr>
                    <th v-if="form.orderType==='MANUAL'">
                      图片
                    </th><th>采购项目</th><th>数量</th><th>单位</th><th>单价</th><th>金额</th><th>操作</th>
                  </tr>
                </thead><tbody>
                  <tr
                    v-for="(line,index) in form.lines"
                    :key="index"
                  >
                    <td
                      v-if="form.orderType==='MANUAL'"
                      class="manual-image-cell"
                    >
                      <PurchaseItemImage
                        v-if="line.imageAssetId"
                        :image-asset-id="line.imageAssetId"
                        :alt="line.itemName||'采购物品'"
                      />
                      <button
                        v-if="line.imageAssetId"
                        type="button"
                        class="ui-image-remove"
                        @click="line.imageAssetId=null"
                      >
                        移除图片
                      </button>
                      <button
                        type="button"
                        class="secondary-button"
                        @click="uploadTarget=index"
                      >
                        {{ line.imageAssetId ? '更换图片' : '上传图片' }}
                      </button>
                    </td>
                    <td>
                      <template v-if="form.orderType==='MANUAL'">
                        <input
                          v-model="line.itemName"
                          required
                          maxlength="200"
                          :aria-label="`物品名称 ${index+1}`"
                          placeholder="名称，如纸巾、灯泡"
                        ><input
                          v-model="line.specification"
                          maxlength="500"
                          aria-label="物品规格"
                          placeholder="规格 / 型号（选填）"
                        >
                      </template>
                      <select
                        v-else
                        v-model="line.itemId"
                        v-single-select
                        required
                        :aria-label="`采购项目 ${index + 1}`"
                        :disabled="referenceLoading || !references.length"
                      >
                        <option
                          value=""
                          disabled
                        >
                          {{ referenceLoading ? '正在加载…' : '选择可采购项目' }}
                        </option><option
                          v-for="item in references"
                          :key="item.id"
                          :value="item.id"
                          :disabled="form.lines.some((other, otherIndex) => otherIndex !== index && other.itemId === item.id)"
                        >
                          {{ item.code }} · {{ item.name }}
                        </option>
                      </select>
                    </td>
                    <td>
                      <input
                        v-model.number="line.quantity"
                        required
                        type="number"
                        min="0.0001"
                        step="0.0001"
                        aria-label="采购数量"
                      >
                    </td>
                    <td>
                      <input
                        v-if="form.orderType==='MANUAL'"
                        v-model="line.unit"
                        required
                        maxlength="32"
                        aria-label="物品单位"
                        placeholder="个 / 包 / 箱"
                      ><template v-else>
                        {{ references.find(item => item.id === line.itemId)?.unit || '—' }}
                      </template>
                    </td>
                    <td>
                      <input
                        v-model.number="line.unitPrice"
                        required
                        type="number"
                        min="0"
                        step="0.0001"
                        aria-label="采购单价"
                      >
                    </td>
                    <td>{{ (Number(line.quantity || 0) * Number(line.unitPrice || 0)).toFixed(2) }}</td>
                    <td>
                      <button
                        type="button"
                        class="secondary-button"
                        :disabled="form.lines.length <= 1"
                        @click="form.lines.splice(index, 1)"
                      >
                        移除
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </TableViewport>
          </div>
          <div class="form-wide purchase-section-heading">
            <button
              type="button"
              class="secondary-button"
              :disabled="(form.orderType!=='MANUAL' && !references.length) || form.lines.length >= 200"
              @click="addLine"
            >
              增加明细
            </button><strong>合计 {{ form.currency.toUpperCase() }} {{ formTotal }}</strong>
          </div>
          <label class="form-wide">采购备注<textarea
            v-model="form.remark"
            maxlength="500"
            placeholder="包装要求、供货约定等（单行文本）"
          /></label>
        </fieldset>
        <div class="operation-form__actions ui-dialog-actions">
          <button
            type="button"
            class="secondary-button"
            :disabled="saving || uploading"
            @click="closeForm"
          >
            取消
          </button><button
            type="submit"
            :disabled="saving || uploading || (form.orderType!=='MANUAL' && (referenceLoading || supplierLoading || !references.length))"
          >
            {{ saving ? '保存中…' : '保存草稿' }}
          </button>
        </div>
      </form>
    </div>
    <AccessDialog
      v-if="uploadTarget!==undefined"
      title="采购物品图片"
      :busy="uploading"
      @close="uploadTarget=undefined"
    >
      <SingleFileUpload
        title="物品图片"
        accept=".png,.jpg,.jpeg,.webp"
        :max-mb="5"
        :disabled="saving||uploading"
        :upload="file=>uploadItem(file,uploadTarget!)"
      />
    </AccessDialog>
  </section>
  <PurchaseLogisticsDialog
    v-if="logisticsOrder"
    :order="logisticsOrder"
    :writable="writable && logisticsOrder.status !== 'CANCELLED'"
    @close="logisticsOrder = undefined"
    @saved="logisticsSaved"
  />
</template>

<style scoped>
.purchase-delivery-date { color: #dc2626; }
.manual-image-cell { min-width:110px; max-width:130px; white-space:normal; }.manual-image-cell > button { display:block; margin:5px 0; }.manual-purchase-lines { min-width:850px; }.manual-purchase-lines td:nth-child(2) { min-width:230px; }.manual-purchase-lines td:nth-child(2) input { display:block; width:100%; max-width:none; }.manual-purchase-lines td:nth-child(4) input { width:70px; }.purchase-edit-lines td input + input { margin-top:6px; }
.purchase-list-product{display:flex;align-items:center;gap:10px;min-width:240px;max-width:420px;white-space:normal;margin-bottom:8px}.purchase-list-product>div{min-width:0;overflow-wrap:anywhere}.purchase-list-product>:first-child{flex:none}
</style>
