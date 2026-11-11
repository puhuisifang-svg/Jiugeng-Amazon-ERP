<script setup lang="ts">
import SingleFileUpload from './SingleFileUpload.vue'
import PurchaseItemImage from './PurchaseItemImage.vue'
import { catalogApi } from '@/api/catalog'
import { computed, ref } from 'vue'
import { purchaseApi, type PurchaseOrder } from '@/api/purchases'
import AccessDialog from './AccessDialog.vue'
const props = defineProps<{ order: PurchaseOrder; writable: boolean }>()
const emit = defineEmits<{ close: []; saved: [order: PurchaseOrder] }>()
const dialog = ref<InstanceType<typeof AccessDialog>>()
const carrier = ref(props.order.carrier ?? ''), tracking = ref(props.order.trackingNumber ?? ''), busy = ref(false), error = ref('')
const waybill=ref(props.order.waybillImageId??null),uploading=ref(false)
async function upload(file:File){uploading.value=true;error.value='';try{waybill.value=(await catalogApi.uploadImage('PURCHASE_WAYBILL',file)).id}catch(e){error.value=e instanceof Error?e.message:'上传失败';throw e}finally{uploading.value=false}}
const dirty = computed(() => waybill.value!==(props.order.waybillImageId??null) || carrier.value !== (props.order.carrier ?? '') || tracking.value !== (props.order.trackingNumber ?? ''))
async function save() {
  if (busy.value || uploading.value || !props.writable) return
  if(!tracking.value.trim()&&!waybill.value){error.value='请填写运单号或上传物流单图片';return}
  busy.value = true; error.value = ''
  try { emit('saved', await purchaseApi.logistics(props.order.id, props.order.version, carrier.value, tracking.value, waybill.value)) }
  catch (e) { error.value = e instanceof Error ? e.message : '物流资料保存失败，请重试' }
  finally { busy.value = false }
}
</script>
<template>
  <AccessDialog
    ref="dialog"
    title="采购物流"
    :busy="busy || uploading"
    :dirty="dirty"
    @close="emit('close')"
  >
    <p class="access-muted">
      {{ order.orderNo }} · {{ order.supplierName }}
    </p>
    <form
      id="purchase-logistics-form"
      class="access-form"
      @submit.prevent="save"
    >
      <label>物流公司<input
        v-model="carrier"
        maxlength="100"
        placeholder="例如：顺丰、中通、德邦"
        :disabled="busy || !writable"
      ></label><label>运单号<input
        v-model="tracking"
        maxlength="64"
        pattern="[A-Za-z0-9-]{1,64}"
        placeholder="填写完整运单号"
        :disabled="busy || !writable"
      ></label>
      <div class="form-wide">
        <p class="access-muted">
          运单号与物流单图片至少提供一项，零担物流可只上传图片。
        </p>
        <PurchaseItemImage
          v-if="waybill"
          :image-asset-id="waybill"
          alt="物流单"
        />
        <button
          v-if="waybill && writable"
          type="button"
          class="ui-image-remove"
          :disabled="busy||uploading"
          @click="waybill=null"
        >
          移除图片
        </button>
        <SingleFileUpload
          v-if="writable"
          title="物流单图片"
          accept=".png,.jpg,.jpeg,.webp"
          :max-mb="5"
          :disabled="busy||uploading"
          :upload="upload"
        />
      </div>
    </form>
    <p
      v-if="error"
      role="alert"
      class="access-danger"
    >
      {{ error }}
    </p>
    <section class="purchase-tracking-state">
      <strong>物流轨迹</strong><p>物流查询服务尚未接入，当前可保存运单资料；接入后将在此展示真实物流轨迹。</p>
    </section>
    <template #footer>
      <button
        type="button"
        :disabled="busy"
        @click="dialog?.requestClose()"
      >
        关闭
      </button><button
        v-if="writable"
        type="submit"
        form="purchase-logistics-form"
        class="primary-button"
        :disabled="busy || uploading || (!tracking.trim() && !waybill)"
      >
        {{ busy ? '保存中…' : '保存运单' }}
      </button>
    </template>
  </AccessDialog>
</template>
<style scoped>
.purchase-tracking-state{margin-top:16px;padding:12px;background:#f6f8fb;border:1px solid var(--line);border-radius:5px;font-size:13px}.purchase-tracking-state p{color:var(--muted);line-height:1.6;margin-bottom:0}
</style>
