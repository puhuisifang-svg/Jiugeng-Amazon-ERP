<script setup lang="ts">
import './cartonLabels.css'
import SuccessToast from './SuccessToast.vue'
import { onMounted, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { cartonLabelsApi, defaultLabelConfig, type LabelTemplate, type LabelTemplateInput } from '@/api/cartonLabels'
const props = defineProps<{ customerId: string; customerName: string }>()
const emit = defineEmits<{ close: [] }>()
const dialog = ref<HTMLDialogElement>(), templates = ref<LabelTemplate[]>([]), busy = ref(false), error = ref(''), success = ref('')
const id = ref<string>(), previewUrl = ref(''), discard = ref(false)
const blank = (): LabelTemplateInput => ({ name: '100×100 外箱标', config: defaultLabelConfig(), enabled: true, defaultTemplate: true })
const form = reactive(blank()), initial = ref('')
function clearPreview() { if (previewUrl.value) URL.revokeObjectURL(previewUrl.value); previewUrl.value = '' }
function choose(item?: LabelTemplate) { clearPreview(); id.value = item?.id; Object.assign(form, item ? { ...JSON.parse(JSON.stringify(item)), config: { ...defaultLabelConfig(), ...JSON.parse(JSON.stringify(item.config)) } } : { ...blank(), defaultTemplate: templates.value.length === 0 }); initial.value = JSON.stringify(form); error.value = ''; discard.value = false }
function requestChoose(item?: LabelTemplate) { if (initial.value && JSON.stringify(form) !== initial.value) { error.value = '请先保存当前修改，或关闭并放弃修改后重新打开。'; return } choose(item) }
function requestClose() { if (busy.value) return; if (JSON.stringify(form) !== initial.value) discard.value = true; else emit('close') }
async function load() { templates.value = await cartonLabelsApi.templates(props.customerId) }
async function save() {
  if (busy.value) return; busy.value = true; error.value = ''; success.value = ''
  try { const item = await cartonLabelsApi.saveTemplate(props.customerId, id.value, JSON.parse(JSON.stringify(form))); choose(item); success.value = '模板已保存，旧外箱标 PDF 不受影响。'; try { await load() } catch { error.value = '模板已保存，但列表刷新失败，请重新打开查看。' } }
  catch (e) { error.value = e instanceof Error ? e.message : '保存失败' } finally { busy.value = false }
}
async function preview() {
  if (busy.value) return; busy.value = true; error.value = ''; clearPreview()
  try { previewUrl.value = URL.createObjectURL(await cartonLabelsApi.templatePreview(form.config)) }
  catch (e) { error.value = e instanceof Error ? e.message : '预览失败' } finally { busy.value = false }
}
function move(index: number, delta: number) {
  const next = index + delta
  if (next < 0 || next >= form.config.rows.length) return
  const row = form.config.rows.splice(index, 1)[0]!
  form.config.rows.splice(next, 0, row)
}
onMounted(async () => { dialog.value?.showModal(); busy.value = true; try { await load(); choose(templates.value[0]) } catch (e) { error.value = String(e) } finally { busy.value = false } })
watch(form, clearPreview, { deep: true })
onBeforeUnmount(clearPreview)
</script>
<template>
  <dialog
    ref="dialog"
    class="label-modal"
    aria-label="外箱标模板"
    @cancel.prevent="requestClose"
  >
    <header>
      <div><h2>{{ customerName }} · 外箱标模板</h2><p>100×100 mm · 3 mm 安全边距 · 可按箱数或每条发货仅导出一页</p></div><button
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
      当前修改尚未保存。<button
        type="button"
        @click="discard = false"
      >
        继续编辑
      </button><button
        type="button"
        @click="emit('close')"
      >
        放弃修改并关闭
      </button>
    </div>
    <p
      v-if="error"
      role="alert"
      class="label-error"
    >
      {{ error }}
    </p>
    <SuccessToast :message="success" />
    <nav>
      <button
        v-for="item in templates"
        :key="item.id"
        type="button"
        :disabled="busy"
        @click="requestChoose(item)"
      >
        {{ item.name }}{{ item.defaultTemplate ? ' · 默认' : '' }}{{ !item.enabled ? ' · 停用' : '' }}
      </button><button
        type="button"
        :disabled="busy"
        @click="requestChoose()"
      >
        新增模板
      </button>
    </nav>
    <form @submit.prevent="save">
      <fieldset :disabled="busy">
        <div class="label-fields">
          <label>模板名称<input
            v-model="form.name"
            required
            maxlength="100"
          ></label>
          <label>标题<input
            v-model="form.config.title"
            required
            maxlength="100"
          ></label>
          <label>标题字号（pt）<input
            v-model.number="form.config.titleSize"
            type="number"
            min="8"
            max="20"
            required
          ></label>
          <label>导出页数<select
            v-model="form.config.singlePage"
            aria-label="导出页数"
          >
            <option :value="false">按箱数导出（每箱一页）</option>
            <option :value="true">仅导出 1 页（每条发货记录）</option>
          </select></label>
          <label>默认收货地点<input
            v-model="form.config.defaultDestination"
            maxlength="150"
            placeholder="可选，发货时可修改"
          ></label>
          <label><input
            v-model="form.enabled"
            type="checkbox"
          >启用模板</label><label><input
            v-model="form.defaultTemplate"
            type="checkbox"
          >设为默认</label>
          <label><input
            v-model="form.config.color"
            type="checkbox"
          >黄色标题栏（黑白标签机建议关闭）</label>
        </div>
        <div
          v-for="(row, index) in form.config.rows"
          :key="row.key"
          class="label-template-row"
        >
          <label>字段名称<textarea
            v-model="row.label"
            required
            maxlength="60"
            rows="2"
          /></label>
          <label>行高权重<input
            v-model.number="row.height"
            type="number"
            min="10"
            max="30"
            required
          ></label>
          <label>数值字号（pt）<input
            v-model.number="row.fontSize"
            type="number"
            min="8"
            max="22"
            required
          ></label>
          <button
            type="button"
            :disabled="index === 0"
            @click="move(index, -1)"
          >
            上移
          </button><button
            type="button"
            :disabled="index === 3"
            @click="move(index, 1)"
          >
            下移
          </button>
        </div><p>字段行高按权重分配，长文字会自动换行并适度缩小；过长无法清晰打印时会提示修改。模板预览使用合成示例数据。</p>
        <footer>
          <button
            type="button"
            @click="preview"
          >
            预览模板 PDF
          </button><button
            type="submit"
            class="primary-button"
          >
            保存模板
          </button>
        </footer>
      </fieldset>
    </form>
    <iframe
      v-if="previewUrl"
      :src="previewUrl"
      title="外箱标模板 PDF 预览"
      class="label-pdf"
    />
  </dialog>
</template>
