<script setup>
defineProps({
  stages: { type: Array, required: true },
  currentStageIndex: { type: Number, default: -1 },
  compact: { type: Boolean, default: false }
});
</script>

<template>
  <div v-if="stages?.length" class="path-timeline" :class="{ compact }">
    <div v-for="(stage, i) in stages" :key="i" class="path-stage" :class="{ current: i === currentStageIndex, completed: i < currentStageIndex }">
      <div class="stage-dot">{{ i + 1 }}</div>
      <div class="stage-card">
        <h4>{{ stage.name }}
          <span v-if="stage.difficulty" class="diff-badge">{{ stage.difficulty }}</span>
          <span v-if="i === currentStageIndex" class="current-badge">当前</span>
          <span v-if="i < currentStageIndex" class="done-badge">✓ 完成</span>
        </h4>
        <p v-if="stage.goal" class="stage-goal">{{ stage.goal }}</p>
        <div class="stage-meta">
          <span v-if="stage.days">⏱️ {{ stage.days }}天</span>
          <span v-if="stage.dailyMinutes">· {{ stage.dailyMinutes }}分钟/天</span>
        </div>
        <div v-if="stage.modules?.length" class="stage-tags">
          <span v-for="mod in stage.modules" :key="mod" class="mod-tag">{{ mod }}</span>
        </div>
        <div v-if="stage.weakPointFocus && stage.weakPointFocus !== 'null'" class="weak-hint">
          ⚡ 弱项突破：{{ stage.weakPointFocus }}
        </div>
      </div>
    </div>
  </div>
  <div v-else class="empty-path">暂无学习路径数据</div>
</template>

<style scoped>
.path-timeline {
  position: relative;
  padding-left: 36px;
}
.path-timeline::before {
  content: '';
  position: absolute;
  left: 15px;
  top: 0;
  bottom: 0;
  width: 2px;
  background: linear-gradient(to bottom, #667eea, #764ba2);
}
.path-stage {
  position: relative;
  margin-bottom: 20px;
}
.path-stage.completed .stage-dot {
  background: #48bb78;
}
.path-stage.current .stage-dot {
  box-shadow: 0 0 0 4px rgba(102,126,234,0.3);
}
.stage-dot {
  position: absolute;
  left: -36px;
  top: 6px;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 0.85em;
  z-index: 1;
}
.stage-card {
  background: #f8fafc;
  border-radius: 12px;
  padding: 14px 18px;
  border: 1px solid #e2e8f0;
}
.stage-card h4 {
  margin: 0 0 4px;
  font-size: 1.05em;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.stage-goal {
  color: #4a5568;
  font-size: 0.9em;
  margin: 4px 0;
}
.stage-meta {
  color: #718096;
  font-size: 0.82em;
  margin: 4px 0;
}
.stage-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: 6px;
}
.mod-tag {
  background: #ebf4ff;
  color: #3182ce;
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 0.78em;
}
.diff-badge {
  font-size: 0.72em;
  padding: 2px 8px;
  border-radius: 10px;
  background: #fefcbf;
  color: #975a16;
  font-weight: 400;
}
.current-badge {
  font-size: 0.72em;
  padding: 2px 8px;
  border-radius: 10px;
  background: #667eea;
  color: white;
}
.done-badge {
  font-size: 0.72em;
  padding: 2px 8px;
  border-radius: 10px;
  background: #48bb78;
  color: white;
}
.weak-hint {
  margin-top: 6px;
  font-size: 0.82em;
  color: #c05621;
  background: #fffbeb;
  padding: 4px 10px;
  border-radius: 6px;
}
.empty-path {
  text-align: center;
  padding: 30px;
  color: #a0aec0;
}
.compact .stage-card {
  padding: 10px 14px;
}
.compact .stage-dot {
  width: 26px;
  height: 26px;
  font-size: 0.75em;
  left: -32px;
}
</style>
