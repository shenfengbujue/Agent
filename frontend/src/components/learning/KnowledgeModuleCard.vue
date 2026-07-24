<script setup>
import MarkdownRenderer from '../common/MarkdownRenderer.vue';

defineProps({
  module: { type: Object, required: true },
  index: { type: Number, default: 1 },
  defaultCollapsed: { type: Boolean, default: false }
});
</script>

<template>
  <div class="knowledge-card">
    <div class="card-header">
      <span class="card-num">{{ index }}</span>
      <h3>{{ module.name }}</h3>
    </div>
    <div v-if="module.basicKnowledge" class="card-body">
      <MarkdownRenderer :content="module.basicKnowledge" />
    </div>
    <div v-if="module.corePoints?.length" class="core-points">
      <h4>🎯 核心重点</h4>
      <ul>
        <li v-for="(p, pi) in module.corePoints" :key="pi">{{ p }}</li>
      </ul>
    </div>
    <div v-if="module.commonMistakes?.length" class="mistakes-box">
      <h4>⚠️ 易错混淆</h4>
      <ul>
        <li v-for="(m, mi) in module.commonMistakes" :key="mi">{{ m }}</li>
      </ul>
    </div>
  </div>
</template>

<style scoped>
.knowledge-card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 16px;
}
.card-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.card-num {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 0.9em;
  flex-shrink: 0;
}
.card-header h3 {
  margin: 0;
  font-size: 1.1em;
}
.card-body {
  line-height: 1.7;
  color: #2d3748;
}
.core-points {
  margin-top: 12px;
  padding: 12px 16px;
  background: #f0fff4;
  border-radius: 8px;
}
.core-points h4 {
  margin: 0 0 6px;
  color: #276749;
  font-size: 0.95em;
}
.core-points ul {
  margin: 0;
  padding-left: 18px;
}
.core-points li {
  margin: 4px 0;
  font-size: 0.9em;
}
.mistakes-box {
  margin-top: 12px;
  padding: 12px 16px;
  background: #fff5f5;
  border-radius: 8px;
  border-left: 3px solid #fc8181;
}
.mistakes-box h4 {
  margin: 0 0 6px;
  color: #9b2c2c;
  font-size: 0.95em;
}
.mistakes-box ul {
  margin: 0;
  padding-left: 18px;
}
.mistakes-box li {
  margin: 4px 0;
  font-size: 0.9em;
}
</style>
