<script setup>
import { ref, onMounted, computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { goalApi } from '../api/index';
import axios from 'axios';
import MarkdownRenderer from '../components/common/MarkdownRenderer.vue';
import ExerciseCard from '../components/learning/ExerciseCard.vue';

const API = '/api/daily';
const route = useRoute();
const router = useRouter();
const token = localStorage.getItem('token');

const goalId = ref(Number(route.params.goalId));
const stageIndex = ref(Number(route.params.stageIndex));
const goal = ref(null);
const stageDays = ref([]);
const selectedDay = ref(null);
const dayContent = ref(null);
const generating = ref(false);
const genStatus = ref('');
const loading = ref(true);

// 从goal获取阶段信息
const stageInfo = computed(() => {
  if (!goal.value) return null;
  const raw = goal.value.learningPath || goal.value._rawRes;
  try {
    const data = typeof raw === 'string' ? JSON.parse(raw) : raw;
    const stages = data?.learningPath?.stages || data?.stages || [];
    return stages[stageIndex.value] || null;
  } catch { return null; }
});

const totalDays = computed(() => stageInfo.value?.days || 15);
// 已生成/已完成的天数
const generatedCount = computed(() => stageDays.value.filter(d => d.status !== 'PENDING' && !d.status?.startsWith('GENERATING')).length);
const completedCount = computed(() => stageDays.value.filter(d => d.status === 'COMPLETED').length);

onMounted(async () => {
  await loadGoal();
  await loadStageDays();
  loading.value = false;
  // 默认选中第1天或第一个未完成的天
  const firstIncomplete = stageDays.value.find(d => d.status !== 'COMPLETED');
  if (firstIncomplete) selectDay(firstIncomplete.dayIndex);
  else if (stageDays.value.length > 0) selectDay(1);
});

async function loadGoal() {
  try {
    const res = await goalApi.getGoalById(goalId.value);
    const g = res.data?.data || res.data;
    if (g) {
      let raw = g.learningPath || g.resources;
      goal.value = { ...g, _rawRes: raw };
    }
  } catch (e) { console.error(e); }
}

async function loadStageDays() {
  try {
    const res = await axios.get(`${API}/goals/${goalId.value}/stage/${stageIndex.value}`,
      { headers: { Authorization: `Bearer ${token}` } });
    const list = (res.data?.data || []);
    // 确保所有天都有条目
    const map = {};
    list.forEach(d => map[d.dayIndex] = d);
    const all = [];
    for (let i = 1; i <= totalDays.value; i++) {
      all.push(map[i] || { dayIndex: i, status: 'PENDING', score: null });
    }
    stageDays.value = all;
  } catch { /* 首次加载可能为空 */ }
}

async function selectDay(dayIndex) {
  selectedDay.value = dayIndex;
  dayContent.value = null;
  try {
    const res = await axios.get(`${API}/goals/${goalId.value}/stage/${stageIndex.value}/day/${dayIndex}`,
      { headers: { Authorization: `Bearer ${token}` } });
    const data = res.data?.data || res.data;
    // 只有真正有内容时才设置，否则保持null以显示生成按钮
    if (data && data.status && data.status !== 'PENDING') {
      dayContent.value = data;
    }
  } catch { dayContent.value = null; }
}

async function generateDay(dayIndex) {
  generating.value = true;
  genStatus.value = '';
  try {
    const res = await axios.post(`${API}/goals/${goalId.value}/stage/${stageIndex.value}/day/${dayIndex}/generate`,
      {}, { headers: { Authorization: `Bearer ${token}` } });
    const data = res.data?.data || res.data;

    // 如果已经有内容（已缓存），直接显示
    if (data && data.knowledge) {
      dayContent.value = data;
      const item = stageDays.value.find(d => d.dayIndex === dayIndex);
      if (item) item.status = 'GENERATED';
      selectedDay.value = dayIndex;
      generating.value = false;
      return;
    }

    // 后台生成中（支持 GENERATING / GENERATING_KNOWLEDGE / GENERATING_EXERCISES / GENERATING_TEST）
    if (data && data.status && data.status.startsWith('GENERATING')) {
      const item = stageDays.value.find(d => d.dayIndex === dayIndex);
      if (item) item.status = data.status;
      genStatus.value = data.status;
      // 轮询直到生成完成（最多等300秒）
      for (let i = 0; i < 150; i++) {
        await new Promise(r => setTimeout(r, 2000));
        try {
          const pollRes = await axios.get(
            `${API}/goals/${goalId.value}/stage/${stageIndex.value}/day/${dayIndex}`,
            { headers: { Authorization: `Bearer ${token}` } });
          const pollData = pollRes.data?.data || pollRes.data;
          // 更新进度状态
          if (pollData && pollData.status && pollData.status.startsWith('GENERATING') && pollData.status !== genStatus.value) {
            genStatus.value = pollData.status;
            if (item) item.status = pollData.status;
            if (pollData.knowledge) dayContent.value = pollData; // 展示已生成的部分
          }
          if (pollData && pollData.status === 'GENERATED' && pollData.knowledge) {
            dayContent.value = pollData;
            if (item) item.status = 'GENERATED';
            selectedDay.value = dayIndex;
            generating.value = false;
            return;
          }
        } catch (e) { /* 继续轮询 */ }
      }
      alert('生成超时，请稍后刷新页面查看');
      if (item) item.status = 'PENDING';
      generating.value = false;
      return;
    }

    alert('生成失败: ' + (data?.message || '后端返回异常'));
  } catch (e) {
    alert('生成失败: ' + (e.response?.data?.message || e.message));
  }
  generating.value = false;
}

async function completeDay() {
  if (!confirm('确认完成今天的学习？')) return;
  try {
    const wp = [...weakPoints.value];
    await axios.post(`${API}/goals/${goalId.value}/stage/${stageIndex.value}/day/${selectedDay.value}/complete`,
      { score: wp.length > 0 ? 50 : 100, timeSpent: 0, weakPoints: wp },
      { headers: { Authorization: `Bearer ${token}` } });
    // 更新当前天+刷新整个列表
    await loadStageDays();
    dayContent.value.status = 'COMPLETED';
    selectedDay.value = null;
    dayContent.value = null;
  } catch (e) { alert('标记失败'); }
}

// 阶段测试
const stageTest = ref(null);
const testResult = ref(null);
const testAnswers = ref({});
const testGenerating = ref(false);
const extraAdded = ref(false);
// 错题收集
const answeredSet = ref(new Set());
const weakPoints = ref(new Set());
function onExerciseAnswer(result) {
  if (answeredSet.value.has(result.exerciseIndex)) return;
  answeredSet.value.add(result.exerciseIndex);
  if (!result.correct && result.knowledgePoint) {
    weakPoints.value.add(result.knowledgePoint);
  }
}
function resetExercises() {
  answeredSet.value = new Set();
  weakPoints.value = new Set();
  // 强制重新渲染ExerciseCard
  const key = Date.now();
  exerciseKey.value = key;
}
const exerciseKey = ref(0);

async function addExtraDays() {
  try {
    await axios.post(`${API}/goals/${goalId.value}/stage/${stageIndex.value}/extra`,
      { weakTopics: testResult.value.wrongTopics, extraDays: testResult.value.extraDays },
      { headers: { Authorization: `Bearer ${token}` } });
    extraAdded.value = true;
    await loadStageDays();
  } catch(e) { alert('追加失败: '+e.message); }
}

async function generateStageTest() {
  testGenerating.value = true;
  try {
    const res = await axios.post(`${API}/goals/${goalId.value}/stage/${stageIndex.value}/test/generate`,
      { subject: goal.value?.title || '学习', stageName: stageInfo.value?.name || '',
        stageGoal: stageInfo.value?.goal || '', daysLearned: completedCount.value },
      { headers: { Authorization: `Bearer ${token}` } });
    stageTest.value = res.data?.data || res.data;
    testAnswers.value = {};
    testResult.value = null;
  } catch(e) { alert('生成测试失败: '+e.message); }
  testGenerating.value = false;
}

async function submitStageTest() {
  try {
    const res = await axios.post(`${API}/goals/${goalId.value}/stage/${stageIndex.value}/test/submit`,
      { test: stageTest.value, answers: testAnswers.value },
      { headers: { Authorization: `Bearer ${token}` } });
    testResult.value = res.data?.data || res.data;
  } catch(e) { alert('提交失败: '+e.message); }
}

function scrollToSec(id) {
  const el = document.getElementById(id);
  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' });
}
function goBack() { router.push(`/learning/content/${goalId.value}`); }

function getKnowledgeList() {
  if (!dayContent.value?.knowledge) return [];
  try { return typeof dayContent.value.knowledge === 'string' ? JSON.parse(dayContent.value.knowledge) : dayContent.value.knowledge; } catch { return []; }
}
function getExercises() {
  if (!dayContent.value?.exercises) return [];
  try { return typeof dayContent.value.exercises === 'string' ? JSON.parse(dayContent.value.exercises) : dayContent.value.exercises; } catch { return []; }
}
function getComprehensiveTest() {
  if (!dayContent.value?.comprehensiveTest) return [];
  try { return typeof dayContent.value.comprehensiveTest === 'string' ? JSON.parse(dayContent.value.comprehensiveTest) : dayContent.value.comprehensiveTest; } catch { return []; }
}
</script>

<template>
  <div class="stage-detail-page">
    <!-- Header -->
    <div class="sd-header">
      <button class="back-btn" @click="goBack">← 返回</button>
      <div class="sd-header-info">
        <h1>{{ stageInfo?.name || '学习阶段' }}</h1>
        <div class="sd-meta">
          <span v-if="stageInfo?.difficulty" class="meta-tag">🎯 {{ stageInfo.difficulty }}</span>
          <span class="meta-tag">⏱️ {{ totalDays }}天</span>
          <span class="meta-tag">📊 已完成 {{ completedCount }}/{{ totalDays }}天</span>
        </div>
        <p v-if="stageInfo?.goal" class="sd-goal">{{ stageInfo.goal }}</p>
      </div>
    </div>

    <div class="sd-body">
      <!-- 左侧：天数列表 -->
      <aside class="sd-sidebar">
        <h3>📅 学习日程</h3>
        <div class="day-list">
          <div
            v-for="day in stageDays" :key="day.dayIndex"
            :class="['day-item', {
              active: selectedDay === day.dayIndex,
              completed: day.status === 'COMPLETED',
              generated: day.status === 'GENERATED',
              generating: day.status && day.status.startsWith('GENERATING')
            }]"
            @click="selectDay(day.dayIndex)"
          >
            <span class="day-num">{{ day.dayIndex }}</span>
            <span class="day-label">第{{ day.dayIndex }}天</span>
            <span v-if="day.status === 'COMPLETED'" class="day-check">✓</span>
            <span v-else-if="day.status === 'GENERATED'" class="day-dot">●</span>
            <span v-else-if="day.status && day.status.startsWith('GENERATING')" class="day-spin">⏳</span>
            <span v-else class="day-pending">—</span>
          </div>
        </div>
      </aside>

      <!-- 右侧：内容区 -->
      <main class="sd-content">
        <template v-if="!selectedDay">
          <div class="empty-state">
            <span style="font-size:64px">📖</span>
            <p>选择左侧天数开始学习</p>
          </div>
        </template>

        <!-- 未生成：显示生成按钮 -->
        <template v-else-if="!dayContent || dayContent.status === 'PENDING'">
          <div class="generate-area">
            <h2>第{{ selectedDay }}天</h2>
            <div class="stage-modules" v-if="stageInfo?.modules">
              <span v-for="m in (stageInfo.modules || [])" :key="m" class="mod-tag">{{ m }}</span>
            </div>
            <p class="generate-desc">AI将根据阶段目标和教材知识体系，为今天生成专属学习内容：</p>
            <ul class="generate-checklist">
              <li>📖 2个知识点讲解（含典型例题）</li>
              <li>✍️ 4道随堂练习题（每知识点2题）</li>
              <li>📝 3道综合测试题（覆盖全天内容）</li>
            </ul>
            <button class="generate-btn" @click="generateDay(selectedDay)" :disabled="generating">
              {{ generating ? '⏳ 生成中...' : '🚀 开始生成第' + selectedDay + '天内容' }}
            </button>
            <p v-if="generating" class="generate-hint">
              <template v-if="genStatus === 'GENERATING_KNOWLEDGE'">📖 正在生成知识点讲解...</template>
              <template v-else-if="genStatus === 'GENERATING_EXERCISES'">✍️ 正在生成练习题...</template>
              <template v-else-if="genStatus === 'GENERATING_TEST'">📝 正在生成综合测试...</template>
              <template v-else>⏳ 正在准备生成...</template>
            </p>
          </div>
        </template>

        <!-- 已生成：展示内容 -->
        <template v-else>
          <div class="day-header">
            <h2>第{{ selectedDay }}天</h2>
            <span v-if="dayContent.status === 'COMPLETED'" class="done-badge">✅ 已完成</span>
          </div>

          <!-- 快速跳转 -->
          <div class="quick-nav">
            <button class="qnav-btn" @click="scrollToSec('sec-knowledge')">📖 知识</button>
            <button class="qnav-btn" @click="scrollToSec('sec-exercises')">✍️ 练习</button>
            <button class="qnav-btn" @click="scrollToSec('sec-test')">📝 测试</button>
          </div>

          <!-- 知识点 -->
          <section id="sec-knowledge" v-if="getKnowledgeList().length" class="content-section">
            <h3>📖 知识讲解</h3>
            <div v-for="(k, i) in getKnowledgeList()" :key="i" class="knowledge-block">
              <h4>{{ i + 1 }}. {{ k.title }}</h4>
              <MarkdownRenderer :content="k.basic || ''" />
              <div v-if="k.keyPoints?.length" class="kp-box">
                <strong>🎯 核心重点</strong>
                <ul><li v-for="p in k.keyPoints" :key="p">{{ p }}</li></ul>
              </div>
              <div v-if="k.pitfalls?.length" class="pf-box">
                <strong>⚠️ 常见错误</strong>
                <ul><li v-for="p in k.pitfalls" :key="p">{{ p }}</li></ul>
              </div>
              <div v-if="k.reference" class="ref-text">📚 {{ k.reference }}</div>
            </div>
          </section>

          <!-- 随堂练习 — 可点击交互 -->
          <section id="sec-exercises" v-if="getExercises().length" class="content-section">
            <h3>✍️ 随堂练习</h3>
            <ExerciseCard v-for="(ex, i) in getExercises()" :key="exerciseKey + '-' + i" :exercise="ex" :index="i+1" @answer="onExerciseAnswer" />
            <button v-if="answeredSet.size > 0" class="redo-btn" @click="resetExercises">🔄 重新做题</button>
          </section>

          <!-- 综合测试 -->
          <section id="sec-test" v-if="getComprehensiveTest().length" class="content-section">
            <h3>📝 综合测试</h3>
            <ExerciseCard v-for="(t, i) in getComprehensiveTest()" :key="'ct-'+i" :exercise="t" :index="i+1" />
          </section>

          <!-- 完成按钮 -->
          <div v-if="dayContent && dayContent.status === 'GENERATED' && getKnowledgeList().length > 0" class="complete-area">
            <button class="complete-btn" @click="completeDay">✅ 标记完成，解锁下一天</button>
          </div>
        </template>

        <!-- 阶段测试（所有天完成后显示） -->
        <div v-if="completedCount >= totalDays" class="stage-test-area">
          <h3>{{ testResult?.passed ? '✅ 阶段测试已通过' : '📝 阶段综合测试' }}</h3>
          <p>{{ completedCount }}/{{ totalDays }}天已完成 — 验证本阶段学习成果</p>

          <!-- 未生成测试 -->
          <button v-if="!stageTest && !testGenerating" class="gen-test-btn" @click="generateStageTest">
            📝 开始阶段测试
          </button>
          <p v-if="testGenerating" class="gen-hint">⏳ 生成中，预计20-30秒...</p>

          <!-- 测试答题区 -->
          <div v-if="stageTest && !testResult" class="test-questions">
            <div v-for="q in stageTest.questions" :key="q.id" class="test-q-item">
              <p><strong>{{ q.id }}.</strong> {{ q.question }} <span class="q-diff">({{ q.difficulty }})</span></p>
              <div class="test-options">
                <label v-for="(o, oi) in q.options" :key="oi" class="test-opt"
                  :class="{ selected: testAnswers[q.id] === String.fromCharCode(65+oi) }"
                  @click="testAnswers[q.id] = String.fromCharCode(65+oi)">
                  <input type="radio" :name="'q'+q.id" :value="String.fromCharCode(65+oi)" v-model="testAnswers[q.id]" />
                  {{ o }}
                </label>
              </div>
            </div>
            <button class="submit-test-btn" @click="submitStageTest" :disabled="Object.keys(testAnswers).length < stageTest.totalQuestions">
              📤 提交答案
            </button>
          </div>

          <!-- 测试结果 -->
          <div v-if="testResult" class="test-result">
            <div class="result-score" :class="{ passed: testResult.passed, failed: !testResult.passed }">
              <span class="score-num">{{ testResult.percentage }}%</span>
              <span class="score-label">{{ testResult.passed ? '通过！' : '未通过' }}</span>
            </div>
            <p>{{ testResult.message }}</p>
            <div v-if="!testResult.passed && testResult.wrongTopics?.length" class="wrong-topics">
              <strong>薄弱知识点：</strong>
              <span v-for="t in testResult.wrongTopics" :key="t" class="weak-tag">{{ t }}</span>
            </div>
            <!-- 未通过：追加强化练习 -->
            <button v-if="!testResult.passed && testResult.extraDays > 0 && !extraAdded" class="extra-btn" @click="addExtraDays">
              🏋️ 追加{{ testResult.extraDays }}天强化练习
            </button>
            <p v-if="extraAdded" class="extra-ok">✅ 已追加强化练习，请返回日程继续学习</p>
            <button v-if="!testResult.passed && (!testResult.extraDays || extraAdded)" class="retry-btn" @click="stageTest=null;testResult=null;testAnswers={}">🔄 重新测试</button>
          </div>
        </div>
      </main>
    </div>
  </div>
</template>

<style scoped>
.stage-detail-page { max-width:1100px; margin:0 auto; padding:20px; min-height:100vh; }
.sd-header { display:flex; gap:16px; align-items:flex-start; margin-bottom:20px; }
.back-btn { background:none; border:none; color:#667eea; cursor:pointer; font-size:1em; padding:8px 0; white-space:nowrap; }
.sd-header-info h1 { font-size:1.4em; margin:0 0 6px; }
.sd-meta { display:flex; gap:8px; margin:6px 0; }
.meta-tag { background:#f7f8ff; color:#667eea; padding:3px 10px; border-radius:12px; font-size:0.82em; }
.sd-goal { color:#4a5568; font-size:0.9em; margin:4px 0; }
.sd-body { display:flex; gap:20px; }
/* 侧边栏 */
.sd-sidebar { width:180px; flex-shrink:0; background:#f8fafc; border-radius:12px; padding:14px; }
.sd-sidebar h3 { font-size:0.9em; margin-bottom:10px; }
.day-list { display:flex; flex-direction:column; gap:4px; }
.day-item { display:flex; align-items:center; gap:8px; padding:8px 10px; border-radius:8px; cursor:pointer; font-size:0.85em; transition:0.15s; }
.day-item:hover { background:#edf2f7; }
.day-item.active { background:#667eea; color:white; }
.day-item.completed { background:#f0fff4; color:#48bb78; }
.day-num { width:22px; height:22px; border-radius:50%; background:#e2e8f0; display:flex; align-items:center; justify-content:center; font-weight:600; font-size:0.8em; }
.day-item.active .day-num { background:rgba(255,255,255,0.3); color:white; }
.day-item.completed .day-num { background:#48bb78; color:white; }
.day-check { color:#48bb78; font-weight:700; }
.day-dot { color:#667eea; }
.day-pending { color:#cbd5e0; }
/* 主内容区 */
.sd-content { flex:1; min-width:0; }
.empty-state { text-align:center; padding:60px; color:#a0aec0; }
.generate-area { text-align:center; padding:40px 20px; }
.generate-area h2 { font-size:1.3em; margin-bottom:12px; }
.generate-desc { color:#4a5568; margin:10px 0; }
.generate-checklist { text-align:left; display:inline-block; margin:10px 0 20px; color:#4a5568; font-size:0.9em; }
.generate-checklist li { margin:4px 0; }
.generate-btn { padding:14px 40px; background:linear-gradient(135deg,#667eea,#764ba2); color:white; border:none; border-radius:12px; font-size:1.1em; font-weight:600; cursor:pointer; }
.generate-btn:hover { transform:translateY(-1px); opacity:0.95; }
.generate-btn:disabled { opacity:0.5; cursor:not-allowed; }
.generate-hint { color:#a0aec0; font-size:0.85em; margin-top:8px; }
.stage-modules { display:flex; gap:6px; flex-wrap:wrap; justify-content:center; margin:10px 0; }
.mod-tag { background:#ebf4ff; color:#3182ce; padding:3px 10px; border-radius:10px; font-size:0.8em; }
/* 内容区 */
.day-header { display:flex; align-items:center; gap:10px; margin-bottom:16px; }
.day-header h2 { margin:0; }
.quick-nav { display:flex; gap:8px; margin:10px 0 16px; }
.qnav-btn { padding:6px 16px; border:1px solid #e2e8f0; border-radius:20px; background:#fff; cursor:pointer; font-size:0.85em; transition:0.15s; }
.qnav-btn:hover { border-color:#667eea; color:#667eea; background:#f7f8ff; }
.done-badge { background:#48bb78; color:white; padding:4px 12px; border-radius:12px; font-size:0.85em; }
.content-section { margin-bottom:24px; }
.content-section h3 { font-size:1.1em; margin-bottom:12px; color:#2d3748; }
.knowledge-block { background:#fff; border:1px solid #e2e8f0; border-radius:10px; padding:16px; margin-bottom:14px; }
.knowledge-block h4 { margin:0 0 8px; font-size:1.05em; color:#667eea; }
.kp-box { margin-top:10px; padding:10px 14px; background:#f0fff4; border-radius:8px; font-size:0.9em; }
.kp-box ul { margin:4px 0 0 18px; }
.pf-box { margin-top:8px; padding:10px 14px; background:#fff5f5; border-radius:8px; border-left:3px solid #fc8181; font-size:0.9em; }
.pf-box ul { margin:4px 0 0 18px; }
.ref-text { font-size:0.8em; color:#a0aec0; margin-top:8px; }
.exercise-item { background:#fff; border:1px solid #e2e8f0; border-radius:10px; padding:14px; margin-bottom:10px; }
.ex-tag { display:inline-block; background:#ebf4ff; color:#3182ce; padding:1px 8px; border-radius:8px; font-size:0.75em; margin-left:6px; }
.ex-opts { display:flex; gap:10px; flex-wrap:wrap; margin:6px 0; }
.ex-opt { background:#f7fafc; padding:4px 10px; border-radius:6px; font-size:0.9em; }
.ex-answer { margin-top:8px; font-size:0.9em; }
.test-item { background:#fff; border:1px solid #e2e8f0; border-radius:10px; padding:14px; margin-bottom:10px; }
.complete-area { text-align:center; margin-top:20px; padding:20px; }
.complete-btn { padding:12px 32px; background:#48bb78; color:white; border:none; border-radius:10px; font-size:1em; font-weight:600; cursor:pointer; }
.complete-btn:hover { background:#38a169; }
/* 阶段测试 */
.stage-test-area { margin-top:30px; padding:20px; background:#f8fafc; border-radius:12px; border:2px solid #e2e8f0; }
.stage-test-area h3 { margin:0 0 8px; }
.gen-test-btn { padding:12px 32px; background:linear-gradient(135deg,#667eea,#764ba2); color:white; border:none; border-radius:10px; font-size:1em; cursor:pointer; }
.gen-hint { color:#a0aec0; font-size:0.85em; }
.test-questions { margin-top:14px; }
.test-q-item { margin-bottom:16px; padding:12px; background:#fff; border-radius:8px; border:1px solid #e2e8f0; }
.q-diff { font-size:0.75em; color:#a0aec0; }
.test-options { display:flex; flex-direction:column; gap:4px; margin-top:8px; }
.test-opt { display:flex; align-items:center; gap:8px; padding:8px 12px; border-radius:6px; cursor:pointer; font-size:0.9em; }
.test-opt:hover { background:#f0f4ff; }
.test-opt.selected { background:#ebf0ff; border:1px solid #667eea; }
.test-opt input { display:none; }
.submit-test-btn { margin-top:12px; padding:10px 28px; background:#48bb78; color:white; border:none; border-radius:8px; cursor:pointer; }
.submit-test-btn:disabled { opacity:0.5; cursor:not-allowed; }
.test-result { margin-top:16px; padding:16px; background:#fff; border-radius:10px; }
.result-score { text-align:center; padding:16px; border-radius:10px; }
.result-score.passed { background:#f0fff4; }
.result-score.failed { background:#fff5f5; }
.score-num { font-size:2em; font-weight:700; }
.score-label { display:block; font-size:1.1em; margin-top:4px; }
.result-score.passed .score-label { color:#48bb78; }
.result-score.failed .score-label { color:#e53e3e; }
.wrong-topics { margin-top:10px; }
.weak-tag { display:inline-block; background:#fff5f5; color:#e53e3e; padding:2px 10px; border-radius:10px; font-size:0.82em; margin:2px; }
.retry-btn { margin-top:10px; padding:8px 20px; background:#edf2f7; border:none; border-radius:8px; cursor:pointer; }
.extra-btn { margin-top:10px; padding:10px 24px; background:linear-gradient(135deg,#f59e0b,#d97706); color:white; border:none; border-radius:10px; cursor:pointer; font-weight:600; display:block; }
.extra-ok { color:#48bb78; font-weight:600; margin-top:10px; }
</style>
