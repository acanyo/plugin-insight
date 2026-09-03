<script setup lang="ts">
export interface ResourceDetail {
  label: string
  value: string
  danger?: boolean
}

defineProps<{ items: ResourceDetail[] }>()
</script>

<template>
  <dl class="resource-details">
    <div v-for="item in items" :key="item.label">
      <dt>{{ item.label }}</dt>
      <dd class="insight-mono" :class="{ danger: item.danger }">{{ item.value }}</dd>
    </div>
  </dl>
</template>

<style scoped>
.resource-details {
  display: grid;
  margin: 0;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.resource-details div {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 9px 0;
  border-bottom: 1px solid var(--insight-border);
}

.resource-details div:nth-last-child(-n + 2) {
  border-bottom: 0;
}

.resource-details div:nth-child(odd) {
  padding-right: 14px;
}

.resource-details div:nth-child(even) {
  padding-left: 14px;
  border-left: 1px solid var(--insight-border);
}

.resource-details dt {
  overflow: hidden;
  color: var(--insight-muted);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.resource-details dd {
  margin: 0;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.danger {
  color: var(--insight-red);
}

@media (max-width: 620px) {
  .resource-details {
    grid-template-columns: 1fr;
  }

  .resource-details div,
  .resource-details div:nth-child(odd),
  .resource-details div:nth-child(even) {
    padding: 9px 0;
    border-right: 0;
    border-bottom: 1px solid var(--insight-border);
    border-left: 0;
  }

  .resource-details div:last-child {
    border-bottom: 0;
  }
}
</style>
