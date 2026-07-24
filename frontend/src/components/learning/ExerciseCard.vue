<script setup>
import { ref } from 'vue';
import MarkdownRenderer from '../common/MarkdownRenderer.vue';

const props = defineProps({
  exercise: { type: Object, required: true },
  index: { type: Number, default: 1 },
  showAnswerDefault: { type: Boolean, default: false },
  learningMode: { type: Boolean, default: false }
});

const emit = defineEmits(['answer', 'complete']);

const selectedOption = ref(null);
const showAnswer = ref(props.showAnswerDefault);
const isCorrect = ref(null);

const optionLabels = ['A', 'B', 'C', 'D', 'E', 'F'];

function selectOption(optIndex) {
  if (showAnswer.value) return;
  selectedOption.value = optIndex;
  const letter = optionLabels[optIndex];
  isCorrect.value = letter === props.exercise.answer;
  showAnswer.value = true;
  emit('answer', {
    exerciseIndex: props.index - 1,
    selected: letter,
    correct: isCorrect.value,
    knowledgePoint: props.exercise.knowledgePoint || props.exercise.title || ''
  });
  emit('complete', { index: props.index - 1, correct: isCorrect.value });
}

function toggleAnswer() {
  showAnswer.value = !showAnswer.value;
}

const typeLabels = {
  'multiple-choice': '选择题', '选择题': '选择题',
  'fill-blank': '填空题', '填空题': '填空题',
  'short-answer': '简答题', '简答题': '简答题',
  'programming': '编程题', '编程题': '编程题',
  'true-false': '判断题', '判断题': '判断题'
};

function getTypeLabel(type) {
  return typeLabels[type] || type || '题目';
}
</script>

<template>
  <div class="exercise-card" :class="{ 'answered': showAnswer, 'correct': isCorrect === true, 'incorrect': isCorrect === false }">
    <div class="exercise-header">
      <span class="ex-number">第{{ index }}题</span>
      <span class="ex-type">{{ getTypeLabel(exercise.type) }}</span>
      <span class="ex-difficulty">{{ exercise.difficulty || '' }}</span>
      <span v-if="exercise.module" class="ex-module">{{ exercise.module }}</span>
    </div>

    <p class="ex-question"><strong>{{ index }}.</strong> <MarkdownRenderer :content="exercise.question || ''" /></p>

    <!-- 选项区 — 可点击 -->
    <div v-if="exercise.options?.length" class="ex-options">
      <div
        v-for="(opt, oi) in exercise.options"
        :key="oi"
        :class="['ex-option', {
          'selected': selectedOption === oi,
          'correct-opt': showAnswer && optionLabels[oi] === exercise.answer,
          'wrong-opt': showAnswer && selectedOption === oi && optionLabels[oi] !== exercise.answer
        }]"
        @click="selectOption(oi)"
      >
        <span class="option-text"><MarkdownRenderer :content="(opt || '').replace(/^[A-F][.、]\\s*/, '')" /></span>
        <span v-if="showAnswer && optionLabels[oi] === exercise.answer" class="check-mark">✓</span>
        <span v-if="showAnswer && selectedOption === oi && optionLabels[oi] !== exercise.answer" class="cross-mark">✗</span>
      </div>
    </div>

    <!-- 无选项的题目（填空/简答） -->
    <div v-else-if="learningMode" class="ex-input-area">
      <textarea v-model="selectedOption" placeholder="请输入你的答案..." rows="3" class="ex-textarea"></textarea>
      <button v-if="!showAnswer" @click="toggleAnswer" class="ex-submit-btn">提交</button>
    </div>

    <!-- 答案与解析 -->
    <div v-if="showAnswer" class="ex-answer">
      <div class="answer-header">
        <span v-if="isCorrect === true" class="result-badge correct-badge">✅ 回答正确</span>
        <span v-else-if="isCorrect === false" class="result-badge incorrect-badge">❌ 回答错误</span>
        <button class="toggle-btn" @click="toggleAnswer">{{ showAnswer ? '收起' : '查看答案' }}</button>
      </div>
      <p><strong>答案：</strong><MarkdownRenderer :content="exercise.answer || ''" /></p>
      <p v-if="exercise.analysis"><strong>解析：</strong><MarkdownRenderer :content="exercise.analysis || ''" /></p>
      <p v-if="exercise.commonMistake" class="mistake-hint"><strong>⚠️ 易错提醒：</strong>{{ exercise.commonMistake }}</p>
      <p v-if="exercise.knowledgePoint" class="knowledge-hint"><strong>📌 知识点：</strong>{{ exercise.knowledgePoint }}</p>
    </div>

    <div v-if="!showAnswer && !learningMode" class="ex-toggle">
      <button @click="toggleAnswer" class="toggle-btn">查看答案与解析</button>
    </div>
  </div>
</template>

<style scoped>
.exercise-card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 16px;
  transition: border-color 0.2s;
}
.exercise-card.answered.correct { border-color: #48bb78; }
.exercise-card.answered.incorrect { border-color: #fc8181; }
.exercise-header {
  display: flex; gap: 8px; align-items: center; margin-bottom: 12px;
}
.ex-number { font-weight: 700; color: #667eea; font-size: 0.9em; }
.ex-type, .ex-difficulty, .ex-module {
  font-size: 0.75em; padding: 2px 8px; border-radius: 10px;
}
.ex-type { background: #ebf4ff; color: #3182ce; }
.ex-difficulty { background: #fefcbf; color: #975a16; }
.ex-module { background: #f0fff4; color: #276749; }
.ex-question { font-size: 1.05em; line-height: 1.6; margin: 8px 0; }
.ex-options { display: flex; flex-direction: column; gap: 6px; margin: 12px 0; }
.ex-option {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 14px; border: 1.5px solid #e2e8f0; border-radius: 8px;
  cursor: pointer; transition: all 0.15s;
}
.ex-option:hover:not(.answered) { border-color: #667eea; background: #f7f8ff; }
.ex-option.selected { border-color: #667eea; background: #ebf0ff; }
.ex-option.correct-opt { border-color: #48bb78; background: #f0fff4; }
.ex-option.wrong-opt { border-color: #fc8181; background: #fff5f5; }
.option-letter {
  width: 28px; height: 28px; border-radius: 50%; background: #f7fafc;
  display: flex; align-items: center; justify-content: center;
  font-weight: 600; font-size: 0.85em; flex-shrink: 0;
}
.option-text { flex: 1; }
.check-mark { color: #48bb78; font-weight: 700; }
.cross-mark { color: #fc8181; font-weight: 700; }
.ex-answer {
  margin-top: 14px; padding: 14px; background: #f8fafc; border-radius: 8px;
  border-left: 3px solid #667eea;
}
.answer-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.result-badge { font-weight: 600; font-size: 0.9em; }
.correct-badge { color: #276749; }
.incorrect-badge { color: #9b2c2c; }
.toggle-btn {
  background: none; border: 1px solid #667eea; color: #667eea;
  padding: 4px 12px; border-radius: 6px; cursor: pointer; font-size: 0.85em;
}
.toggle-btn:hover { background: #667eea10; }
.mistake-hint { color: #c05621; margin-top: 6px; }
.knowledge-hint { color: #2b6cb0; margin-top: 4px; }
.ex-toggle { text-align: center; margin-top: 8px; }
.ex-input-area { margin: 12px 0; }
.ex-textarea { width: 100%; padding: 10px; border: 1px solid #e2e8f0; border-radius: 8px; font-family: inherit; }
.ex-submit-btn {
  margin-top: 8px; padding: 8px 20px; background: #667eea; color: white;
  border: none; border-radius: 8px; cursor: pointer;
}
</style>
