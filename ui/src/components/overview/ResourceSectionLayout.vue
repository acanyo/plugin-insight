<template>
  <div class="resource-section-layout">
    <div class="resource-section-layout__meters">
      <slot name="meters" />
    </div>
    <slot />
  </div>
</template>

<style scoped>
.resource-section-layout {
  padding: 14px;
}

.resource-section-layout__meters {
  display: grid;
  gap: 14px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

@media (max-width: 620px) {
  .resource-section-layout__meters {
    grid-template-columns: 1fr;
  }
}
</style>
