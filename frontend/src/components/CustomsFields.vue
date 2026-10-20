<script setup lang="ts">
import type { CustomsProfile } from '@/api/customs'
const model = defineModel<CustomsProfile>({ required: true })
defineProps<{ disabled?: boolean }>()
const basic = [['nameZh', '中文申报品名', 200], ['nameEn', '英文申报品名', 200], ['composition', '材质 / 成分及含量', 1000], ['usage', '用途 / 功能', 1000], ['brand', '品牌（无品牌请明确填写）', 100], ['model', '规格型号', 200], ['unit', '申报单位', 32]] as const
const codes = [['chinaCode', '中国商品编码（10位）', '[0-9]{10}', 10], ['usCode', '美国 HTS 编码（10位）', '[0-9]{10}', 10], ['euCode', '欧盟 CN / TARIC（8或10位）', '[0-9]{8}|[0-9]{10}', 10], ['ukCode', '英国商品编码（10位）', '[0-9]{10}', 10]] as const
</script>
<template>
  <fieldset
    class="customs-fields operation-form form-wide"
    :disabled="disabled"
  >
    <legend>产品报关资料（SKU级）</legend>
    <p class="form-wide customs-help">
      两处入口维护同一份资料。可先保存草稿，编码、原产国和申报要素应按实际商品及目的地核对，不自动推断。
    </p>
    <label
      v-for="[key, label, max] in basic"
      :key="key"
    >{{ label }}<input
      v-model="model[key]"
      :maxlength="max"
      :required="model.state === 'REVIEWED' && ['nameZh', 'nameEn', 'composition', 'usage', 'unit'].includes(key)"
    ></label>
    <label>原产国（两位国家代码）<input
      v-model="model.originCountry"
      maxlength="2"
      pattern="[A-Z]{2}"
      placeholder="如 CN；请确认实际原产国"
      :required="model.state === 'REVIEWED'"
      @input="model.originCountry = model.originCountry.toUpperCase()"
    ></label>
    <label>单件净重（kg）<input
      v-model="model.netWeightKg"
      type="number"
      min="0.000001"
      max="999999.999999"
      step="0.000001"
    ></label>
    <label>单件毛重（kg）<input
      v-model="model.grossWeightKg"
      type="number"
      min="0.000001"
      max="999999.999999"
      step="0.000001"
    ></label>
    <label
      v-for="[key, label, pattern, max] in codes"
      :key="key"
    >{{ label }}<input
      v-model="model[key]"
      inputmode="numeric"
      :pattern="pattern"
      :maxlength="max"
      :required="model.state === 'REVIEWED' && key === 'chinaCode'"
      placeholder="仅填数字；不确定时留空"
    ></label>
    <label class="form-wide">申报要素（按商品编码对应要求逐项填写）<textarea
      v-model="model.declarationElements"
      rows="4"
      maxlength="4000"
      :required="model.state === 'REVIEWED'"
      placeholder="建议逐行填写：要素名称：具体内容。不同商品编码要求不同，请以现行要求和报关行核对结果为准。"
    /></label>
    <label class="form-wide">目的地合规要求 / 证明资料说明<input
      v-model="model.complianceNotes"
      maxlength="2000"
      placeholder="美国、欧盟、英国分别记录适用要求、证明文件编号或待核对事项"
    ></label>
    <label class="form-wide">备注<input
      v-model="model.remark"
      maxlength="1000"
    ></label>
    <label>资料状态<select v-model="model.state"><option value="DRAFT">草稿 / 待核对</option><option value="REVIEWED">资料已核对</option></select></label>
    <p class="form-wide customs-help">
      “资料已核对”是人工维护标记，不代表已申报、海关审核通过或目的地合规认证。目的地编码仅做格式校验，不校验归类正确性；该页不代替逐票申报、商业发票或装箱单。
    </p>
    <p class="form-wide customs-help">
      官方参考：<a
        href="https://www.help.cbp.gov/s/article/Article-1894?language=en_US"
        target="_blank"
        rel="noopener noreferrer"
      >美国 CBP</a> · <a
        href="https://trade.ec.europa.eu/access-to-markets/en/my-trade-assistant"
        target="_blank"
        rel="noopener noreferrer"
      >欧盟 Access2Markets</a>
    </p>
  </fieldset>
</template>
<style scoped>
.customs-fields { min-width: 0; border: 1px solid #dbe1ea; border-radius: 8px; padding: 16px; }
.customs-help { color: #68758c; font-size: 13px; margin: 0; line-height: 1.6; }
.customs-fields textarea { min-height: 96px; }
</style>
