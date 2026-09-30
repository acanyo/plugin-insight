<script setup lang="ts">
defineProps<{
  title: string
  description?: string
}>()
</script>

<template>
  <header class="section-heading">
    <div class="section-heading__copy">
      <h2>{{ title }}</h2>
      <p v-if="description">{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="section-heading__actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<style scoped>
.section-heading {
  display: flex;
  min-height: 58px;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 15px;
  border-bottom: 1px solid var(--insight-border);
}

.section-heading__copy {
  min-width: 0;
}

h2 {
  margin: 0;
  color: var(--insight-ink);
  font-size: 14px;
  font-weight: 680;
  line-height: 20px;
}

p {
  margin: 2px 0 0;
  color: var(--insight-muted);
  font-size: var(--insight-text-xs);
  line-height: 16px;
}

.section-heading__actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
}

@media (max-width: 640px) {
  .section-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .section-heading__actions {
    width: 100%;
  }
}
</style>
