<script setup lang="ts">
import './cartonLabels.css'
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { cartonLabelsApi, downloadLabelBlob, type LabelDraft, type LabelEntry, type LabelExport } from '@/api/cartonLabels'
const props = defineProps<{ shipmentIds: string[] }>()
const emit = defineEmits<{ close: [] }>()
const dialog = ref<HTMLDialogElement>(), busy = ref(false), error = ref(''), purchaser = ref(''), discard = ref(false)
const drafts = ref<LabelDraft[]>([]), entries = ref<LabelEntry[]>([]), histories = ref<Record<string, LabelExport[]>>({})
const previewUrl = ref(''), saved = ref<LabelExport>(), initial = ref(''), requestKey = ref(crypto.randomUUID())
function entryPages(index: number) {
  const draft = drafts.value[index], entry = entries.value[index]
  if (!draft || !entry) return 0
  const template = draft.templates.find(t => t.id === entry.templateId)
  return template?.config.singlePage ? 1 : draft.boxes
}
const pages = computed(() => drafts.value.reduce((total, _d, index) => total + entryPages(index), 0))
const ready = computed(() => entries.value.length === props.shipmentIds.length && entries.value.length > 0 && pages.value <= 500)
function clearPreview() { if (previewUrl.value) URL.revokeObjectURL(previewUrl.value); previewUrl.value = '' }
function requestClose() { if (busy.value) return; if (!saved.value && JSON.stringify(entries.value) !== initial.value) discard.value = true; else emit('close') }
function selectTemplate(index: number) {
  const entry = entries.value[index]!, template = drafts.value[index]!.templates.find(t => t.id === entry.templateId)
  entry.templateVersion = template?.version ?? 0
  entry.destination = template?.config.defaultDestination ?? ''
}
function applyPurchaser() { for (const entry of entries.value) entry.purchaser = purchaser.value.trim() }
function batch() { return { items: entries.value, requestKey: requestKey.value } }
async function preview() {
  if (busy.value || !ready.value) return; busy.value = true; error.value = ''; clearPreview()
  try { previewUrl.value = URL.createObjectURL(await cartonLabelsApi.preview(batch())) }
  catch (e) { error.value = e instanceof Error ? e.message : '预览失败' } finally { busy.value = false }
}
async function generate() {
  if (busy.value || !previewUrl.value || saved.value) return; busy.value = true; error.value = ''
  try {
    saved.value = await cartonLabelsApi.generate(batch())
    downloadLabelBlob(await cartonLabelsApi.download(saved.value.id), `外箱标_100x100_${saved.value.id.slice(0, 8)}.pdf`)
    for (const d of drafts.value) histories.value[d.shipmentId] = await cartonLabelsApi.history(d.shipmentId)
  } catch (e) { error.value = e instanceof Error ? e.message : '导出失败，可重试；已保存文件可在历史中下载' } finally { busy.value = false }
}
async function download(item: LabelExport) {
  if (busy.value) return; busy.value = true; error.value = ''
  try { downloadLabelBlob(await cartonLabelsApi.download(item.id), `外箱标_100x100_${item.id.slice(0, 8)}.pdf`) }
  catch (e) { error.value = e instanceof Error ? e.message : '下载失败' } finally { busy.value = false }
}
onMounted(async () => {
  dialog.value?.showModal(); busy.value = true
  try {
    if (props.shipmentIds.length > 50) throw new Error('单次最多选择 50 条发货记录')
    const data = await Promise.all(props.shipmentIds.map(id => cartonLabelsApi.draft(id)))
    drafts.value = data
    entries.value = data.map(d => {
      const options = d.templates.filter(t => t.enabled)
      const selected = options.find(t => t.id === d.previous?.templateId) ?? options.find(t => t.defaultTemplate) ?? (options.length === 1 ? options[0] : undefined)
      return { shipmentId: d.shipmentId, shipmentVersion: d.shipmentVersion, templateId: selected?.id ?? '', templateVersion: selected?.version ?? 0,
        productName: d.previous?.productName ?? d.productName, customerCode: d.previous?.customerCode ?? d.customerCode, purchaser: d.previous?.purchaser ?? '',
        contractNo: d.previous?.contractNo ?? d.orderReference ?? '', destination: d.previous?.destination ?? selected?.config.defaultDestination ?? '',
        carrier: d.previous?.carrier ?? '', trackingNo: d.previous?.trackingNo ?? '' }
    })
    initial.value = JSON.stringify(entries.value)
    for (const d of data) histories.value[d.shipmentId] = await cartonLabelsApi.history(d.shipmentId)
  } catch (e) { error.value = e instanceof Error ? e.message : '读取失败' } finally { busy.value = false }
})
watch(entries, () => { clearPreview(); saved.value = undefined; requestKey.value = crypto.randomUUID() }, { deep: true })
onBeforeUnmount(clearPreview)
</script>
<template>
  <dialog
    ref="dialog"
    class="label-modal"
    aria-label="导出客户外箱标"
    @cancel.prevent="requestClose"
  >
    <header>
      <div><h2>导出客户外箱标</h2><p>100×100 mm · 按所选客户模板的页数设置，共 {{ pages }} 页。打印选择「实际大小 / 100%」。</p></div><button
        type="button"
        :disabled="busy"
        @click="requestClose"
      >
        关闭
      </button>
    </header>
    <div
      v-if="discard"
      class="label-warning"
    >
      填写的内容尚未保存。<button
        type="button"
        @click="discard = false"
      >
        继续填写
      </button><button
        type="button"
        @click="emit('close')"
      >
        放弃并关闭
      </button>
    </div>
    <p
      v-if="error"
      role="alert"
      class="label-error"
    >
      {{ error }}
    </p>
    <p
      v-if="busy"
      role="status"
    >
      正在处理，请稍候…
    </p>
    <p
      v-if="saved"
      role="status"
    >
      已生成 {{ saved.pages }} 页并保存快照，可从导出历史再次下载原文件。
    </p>
    <form @submit.prevent="preview">
      <fieldset :disabled="busy">
        <div class="label-fields">
          <label>批量填写采购人<input
            v-model="purchaser"
            maxlength="64"
            placeholder="手动输入采购人姓名"
          ></label><button
            type="button"
            @click="applyPurchaser"
          >
            应用到本批全部记录
          </button>
        </div>
        <article
          v-for="(entry, index) in entries"
          :key="entry.shipmentId"
          class="label-card"
        >
          <h3>{{ drafts[index]?.customerName || '未绑定客户' }} · {{ drafts[index]?.productName }}</h3>
          <p>总数量 {{ drafts[index]?.quantity }} {{ drafts[index]?.unit === 'SET' ? '套' : drafts[index]?.unit }} / {{ drafts[index]?.boxes }} 箱 → {{ entryPages(index) }} 张相同标签</p>
          <p
            v-if="!drafts[index]?.templates.some(t => t.enabled)"
            class="label-error"
          >
            此客户尚无启用的外箱标模板，请先到客户信息维护。
          </p>
          <div class="label-fields">
            <label>外箱标模板<select
              v-model="entry.templateId"
              required
              @change="selectTemplate(index)"
            ><option value="">请选择（未配置请先到客户信息维护模板）</option><option
              v-for="t in drafts[index]?.templates.filter(t => t.enabled)"
              :key="t.id"
              :value="t.id"
            >{{ t.name }} · v{{ t.version + 1 }}</option></select></label>
            <label>SKU 名称<input
              v-model="entry.productName"
              required
              maxlength="150"
              placeholder="默认带出 SKU 名称，可手动修改"
            ></label>
            <label>客户产品编码<input
              v-model="entry.customerCode"
              required
              maxlength="128"
              placeholder="例如客户订单中的 SKU 编码"
            ></label>
            <label>采购人姓名<input
              v-model="entry.purchaser"
              required
              maxlength="64"
              placeholder="手动输入"
            ></label>
            <label>合同号 / 订单号（可选）<input
              v-model="entry.contractNo"
              maxlength="64"
              placeholder="手动输入合同号或订单号"
            ></label><label>收货地点（可选）<input
              v-model="entry.destination"
              maxlength="150"
            ></label>
            <label>快递 / 承运商（可选）<input
              v-model="entry.carrier"
              maxlength="40"
            ></label><label>运单号（可选）<input
              v-model="entry.trackingNo"
              maxlength="80"
            ></label>
          </div>
          <details v-if="histories[entry.shipmentId]?.length">
            <summary>历史导出（修改模板后仍可下载原 PDF）</summary><button
              v-for="item in histories[entry.shipmentId]"
              :key="item.id"
              type="button"
              @click="download(item)"
            >
              {{ new Date(item.createdAt).toLocaleString() }} · 整批 {{ item.pages }} 页 · 下载
            </button>
          </details>
        </article>
        <footer>
          <button
            type="submit"
            :disabled="!ready"
          >
            预览 PDF
          </button><button
            type="button"
            class="primary-button"
            :disabled="!previewUrl || !!saved"
            @click="generate"
          >
            确认生成并下载
          </button><button
            v-if="saved"
            type="button"
            @click="download(saved)"
          >
            再次下载原文件
          </button>
        </footer>
      </fieldset>
    </form>
    <iframe
      v-if="previewUrl"
      :src="previewUrl"
      title="客户外箱标 PDF 预览"
      class="label-pdf"
    />
  </dialog>
</template>
