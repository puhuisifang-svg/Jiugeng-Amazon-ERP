<script setup lang="ts">
import {nextTick,onMounted,ref} from 'vue'
import {useAnchoredPopover} from '@/composables/useAnchoredPopover'
import UnsavedChangesDialog from './UnsavedChangesDialog.vue'
const props=defineProps<{value:string;anchor?:HTMLElement|undefined}>()
const emit=defineEmits<{save:[value:string];close:[]}>()
const dialog=ref<HTMLDialogElement>(),draft=ref(props.value),unsaved=ref(false)
const {position,reposition}=useAnchoredPopover(dialog,()=>props.anchor)
onMounted(async()=>{dialog.value?.showModal();await nextTick();reposition();dialog.value?.querySelector('textarea')?.focus()})
function close(){if(draft.value!==props.value)unsaved.value=true;else discard()}
function discard(){dialog.value?.close();props.anchor?.focus();emit('close')}
function save(){dialog.value?.close();props.anchor?.focus();emit('save',draft.value.trim())}
</script>
<template>
  <dialog
    ref="dialog"
    class="purchase-remark-popover"
    :style="position"
    aria-label="编辑商品备注"
    @cancel.prevent="close"
    @click.self="close"
  >
    <header>
      <strong>商品备注</strong><button
        type="button"
        aria-label="关闭备注"
        @click="close"
      >
        ×
      </button>
    </header>
    <form @submit.prevent="save">
      <textarea
        v-model="draft"
        aria-label="商品备注"
        maxlength="1000"
        placeholder="填写此商品的包装要求、采购说明等"
      />
      <small>{{ draft.length }} / 1000</small>
      <footer>
        <button
          type="button"
          @click="close"
        >
          取消
        </button><button
          type="submit"
          class="remark-save"
        >
          确定
        </button>
      </footer>
    </form>
  </dialog>
  <UnsavedChangesDialog
    v-if="unsaved"
    @keep="unsaved=false"
    @discard="discard"
  />
</template>
<style>
#app .purchase-remark-popover{position:fixed;inset:auto;margin:0;padding:12px;width:min(320px,calc(100vw - 16px));box-sizing:border-box;max-height:calc(100dvh - 16px);overflow:auto;border:1px solid #dce2ea;border-radius:8px;box-shadow:0 6px 24px #0f172a26;color:#24334b;background:white}
.purchase-remark-popover::backdrop{background:transparent}
.purchase-remark-popover header{display:flex;align-items:center;justify-content:space-between;margin-bottom:8px;font-size:14px}
#app .purchase-remark-popover header button{border:0;background:transparent;color:#64748b;font-size:20px;min-height:24px;padding:0 4px}
#app .purchase-remark-popover textarea{width:100%;min-height:100px;max-height:180px;resize:vertical;box-sizing:border-box;font-size:13px;line-height:1.6;padding:8px;border:1px solid #dce2ea;border-radius:4px}
.purchase-remark-popover small{display:block;text-align:right;margin:4px 0 10px;color:#8390a2;font-size:11px}
.purchase-remark-popover footer{display:flex;justify-content:flex-end;gap:8px}
#app .purchase-remark-popover footer button{font-size:13px;min-height:32px;padding:4px 14px;border:1px solid #dce2ea;border-radius:4px;background:white;color:#475569}
#app .purchase-remark-popover footer .remark-save{background:#21834a;border-color:#21834a;color:white}
</style>
