<script setup lang="ts">
defineProps<{ title: string; description?: string; loading?: boolean; error?: boolean }>()
defineEmits<{ retry: [] }>()
</script>
<template>
  <div
    class="product-ui-state"
    :class="{ 'product-ui-state--error': error }"
    :role="error ? 'alert' : 'status'"
    :aria-busy="loading"
  >
    <span
      class="product-ui-state-icon"
      :class="{ 'is-loading': loading }"
      aria-hidden="true"
    >{{ loading ? '' : error ? '!' : '—' }}</span>
    <div>
      <strong>{{ title }}</strong><p v-if="description">
        {{ description }}
      </p>
    </div>
    <button
      v-if="error"
      type="button"
      class="product-text-button"
      @click="$emit('retry')"
    >
      重试
    </button>
    <slot />
  </div>
</template>
