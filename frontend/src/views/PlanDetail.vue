<script setup>
import { ref, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { knowledgeApi, goalApi } from '../api/index';
import MarkdownRenderer from '../components/common/MarkdownRenderer.vue';
import ExerciseCard from '../components/learning/ExerciseCard.vue';
import LearningPathTimeline from '../components/learning/LearningPathTimeline.vue';
import KnowledgeModuleCard from '../components/learning/KnowledgeModuleCard.vue';
import KnowledgeGraphViewer from '../components/learning/KnowledgeGraphViewer.vue';
import { convertPlanToStudyFormat } from '../utils/planConverter.js';

const route = useRoute();
const router = useRouter();

const plan = ref(null);
const loading = ref(true);
const activeTab = ref('summary');

const tabs = [
  { key: 'summary', label: '📋 总览', icon: '📋' },
  { key: 'path', label: '🗺️ 学习路径', icon: '🗺️' },
  { key: 'knowledge', label: '📖 知识模块', icon: '📖' },
  { key: 'graph', label: '🧠 思维导图', icon: '🧠' },
  { key: 'exercises', label: '✍️ 练习题', icon: '✍️' },
  { key: 'resources', label: '🌐 拓展阅读', icon: '🌐' }
];

const exerciseStats = ref({ total: 0, answered: 0, correct: 0 });

function handleAnswer(result) {
  exerciseStats.value.answered++;
  if (result.correct) exerciseStats.value.correct++;
}

const addingToGoal = ref(false);
async function addToStudyGoal() {
  if (!plan.value?.planData) return;
  addingToGoal.value = true;
  try {
    const studyData = convertPlanToStudyFormat(plan.value.planData);
    const user = JSON.parse(localStorage.getItem('user') || '{}');
    const userId = user.id;
    if (!userId) { alert('请先登录'); return; }

    // 检查是否已存在同名目标
    try {
      const existingRes = await goalApi.getGoalsByUserId(userId);
      const existingGoals = existingRes.data?.data || [];
      const duplicate = existingGoals.find(g => g.title === plan.value.title);
      if (duplicate) {
        if (!confirm(`「${plan.value.title}」已经在个性化学习中了，确定要重复添加吗？`)) {
          addingToGoal.value = false;
          return;
        }
      }
    } catch (e) { /* 检查失败不影响主流程 */ }

    const goal = {
      title: plan.value.title,
      category: 'AI生成',
      icon: '🤖',
      color: '#667eea',
      progress: 0,
      resources: JSON.stringify(studyData),
      learningPath: JSON.stringify(plan.value.planData),
      currentStageIndex: 0
    };

    const res = await goalApi.createGoal(userId, goal);
    if (res.data?.code === 200 || res.data?.code === 0) {
      alert('✅ 已加入个性化学习！\n可在"个性化学习"页面查看并按阶段推进。');
    } else {
      alert('加入失败: ' + (res.data?.message || '请重试'));
    }
  } catch (e) {
    console.error('加入学习目标失败:', e);
    alert('加入失败: ' + (e.message || '请重试'));
  }
  addingToGoal.value = false;
}

onMounted(async () => {
  const id = route.query.id || route.params.id;
  if (!id) { loading.value = false; return; }

  try {
    const res = await knowledgeApi.getKnowledgeItemById(id);
    const item = res.data?.data || res.data;
    if (item) {
      let planData = null;
      try {
        planData = typeof item.planData === 'string' ? JSON.parse(item.planData) : item.planData;
      } catch (e) { /* ignore */ }

      plan.value = {
        id: item.id,
        title: item.title || '学习方案',
        content: item.content || '',
        planData: planData,
        difficulty: item.difficulty || planData?.requirements?.level || '基础',
        duration: item.duration || ''
      };

      if (planData?.exercises?.exercises) {
        exerciseStats.value.total = planData.exercises.exercises.length;
      }
    }
  } catch (e) {
    console.error('加载方案失败:', e);
  }
  loading.value = false;
});

</script>

<template>
  <div class="plan-detail-page">
    <!-- Loading -->
    <div v-if="loading" class="loading-state">加载中...</div>

    <!-- Not found -->
    <div v-else-if="!plan" class="empty-state">
      <span style="font-size:64px">📭</span>
      <p>方案不存在或已被删除</p>
      <button @click="router.back()">返回</button>
    </div>

    <!-- Plan content -->
    <template v-else>
      <!-- Header -->
      <div class="plan-header">
        <button class="back-btn" @click="router.back()">← 返回</button>
        <h1>{{ plan.title }}</h1>
        <div class="plan-meta">
          <span class="meta-tag" v-if="plan.difficulty">🎯 {{ plan.difficulty }}</span>
          <span class="meta-tag" v-if="plan.duration">⏱️ {{ plan.duration }}</span>
          <span class="meta-tag" v-if="exerciseStats.total > 0">✍️ {{ exerciseStats.total }}题</span>
          <span class="meta-tag" v-if="exerciseStats.answered > 0">
            ✅ {{ exerciseStats.correct }}/{{ exerciseStats.answered }}
          </span>
        </div>
        <button class="add-goal-btn" @click="addToStudyGoal" :disabled="addingToGoal || !plan?.planData">
          {{ addingToGoal ? '加入中...' : '📥 加入个性化学习' }}
        </button>
      </div>

      <!-- Tab bar -->
      <div class="tab-bar">
        <button v-for="tab in tabs" :key="tab.key"
          :class="['tab-btn', { active: activeTab === tab.key }]"
          @click="activeTab = tab.key">
          {{ tab.label }}
        </button>
      </div>

      <!-- Tab contents -->
      <div class="tab-body">
        <!-- Summary -->
        <div v-if="activeTab === 'summary'" class="tab-panel">
          <div v-if="plan.planData?.requirements" class="req-tags">
            <span class="req-tag" v-if="plan.planData.requirements.subject">📚 {{ plan.planData.requirements.subject }}</span>
            <span class="req-tag" v-if="plan.planData.requirements.level">📊 {{ plan.planData.requirements.level }}</span>
            <span class="req-tag" v-if="plan.planData.requirements.goal">🎯 {{ plan.planData.requirements.goal }}</span>
          </div>
          <MarkdownRenderer :content="plan.content || '暂无内容'" />
        </div>

        <!-- Learning Path -->
        <div v-if="activeTab === 'path'" class="tab-panel">
          <LearningPathTimeline
            :stages="plan.planData?.learningPath?.stages || []"
            :current-stage-index="-1"
          />
        </div>

        <!-- Knowledge Modules -->
        <div v-if="activeTab === 'knowledge'" class="tab-panel">
          <template v-if="plan.planData?.knowledge?.length">
            <KnowledgeModuleCard
              v-for="(mod, i) in plan.planData.knowledge"
              :key="i"
              :module="mod"
              :index="i + 1"
            />
          </template>
          <div v-else class="empty-tab">暂无知识模块数据</div>
        </div>

        <!-- Knowledge Graph -->
        <div v-if="activeTab === 'graph'" class="tab-panel">
          <KnowledgeGraphViewer
            :nodes="plan.planData?.graph?.nodes || []"
            :edges="plan.planData?.graph?.edges || []"
            :text-outline="plan.planData?.graph?.textOutline || ''"
            height="500px"
            :show-legend="true"
          />
        </div>

        <!-- Exercises — 可交互做题 -->
        <div v-if="activeTab === 'exercises'" class="tab-panel">
          <div v-if="plan.planData?.exercises?.exercises?.length">
            <div class="exercise-stats" v-if="exerciseStats.total > 0">
              答题进度：{{ exerciseStats.answered }}/{{ exerciseStats.total }}
              <span v-if="exerciseStats.answered > 0">
                · 正确率：{{ Math.round(exerciseStats.correct / exerciseStats.answered * 100) }}%
              </span>
            </div>
            <ExerciseCard
              v-for="(ex, i) in plan.planData.exercises.exercises"
              :key="i"
              :exercise="ex"
              :index="i + 1"
              @answer="handleAnswer"
            />
          </div>
          <div v-else class="empty-tab">暂无练习题数据</div>
        </div>

        <!-- Extended Reading -->
        <div v-if="activeTab === 'resources'" class="tab-panel">
          <div v-if="plan.planData?.webSearch?.resources?.length">
            <div v-for="(r, i) in plan.planData.webSearch.resources" :key="i" class="resource-card">
              <h4>{{ r.title }}</h4>
              <p>{{ r.summary }}</p>
              <div class="res-meta">
                <span class="tag">{{ r.type }}</span>
                <span class="tag">{{ r.difficulty }}</span>
                <span class="tag">👍 {{ r.recommendReason }}</span>
              </div>
            </div>
          </div>
          <div v-else class="empty-tab">暂无拓展阅读数据</div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.plan-detail-page {
  max-width: 900px; margin: 0 auto; padding: 24px; min-height: 100vh;
}
.loading-state, .empty-state { text-align: center; padding: 60px; color: #a0aec0; }
.plan-header { margin-bottom: 20px; }
.back-btn { background: none; border: none; color: #667eea; cursor: pointer; font-size: 0.95em; padding: 0; margin-bottom: 8px; }
.plan-header h1 { font-size: 1.6em; margin: 4px 0 8px; }
.plan-meta { display: flex; gap: 8px; flex-wrap: wrap; }
.meta-tag {
  background: #f7f8ff; color: #667eea; padding: 4px 12px;
  border-radius: 20px; font-size: 0.85em;
}
/* Tabs */
.tab-bar {
  display: flex; gap: 4px; border-bottom: 2px solid #e2e8f0; margin-bottom: 20px;
  overflow-x: auto;
}
.tab-btn {
  padding: 10px 16px; border: none; background: none; cursor: pointer;
  font-size: 0.9em; color: #718096; white-space: nowrap;
  border-bottom: 2px solid transparent; margin-bottom: -2px; transition: all 0.2s;
}
.tab-btn.active { color: #667eea; border-bottom-color: #667eea; font-weight: 600; }
.tab-panel { padding: 8px 0; }
.empty-tab { text-align: center; padding: 40px; color: #a0aec0; }
/* Exercise stats */
.exercise-stats {
  text-align: center; padding: 10px; background: #f7f8ff; border-radius: 8px;
  margin-bottom: 16px; color: #667eea; font-weight: 600;
}
/* Requirements */
.req-tags { display: flex; gap: 8px; margin-bottom: 16px; flex-wrap: wrap; }
.req-tag { background: #f7f8ff; color: #667eea; padding: 6px 14px; border-radius: 20px; font-size: 0.9em; }
.add-goal-btn {
  margin-top: 12px;
  padding: 10px 24px;
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: white;
  border: none;
  border-radius: 10px;
  cursor: pointer;
  font-size: 0.95em;
  font-weight: 600;
}
.add-goal-btn:hover { opacity: 0.9; transform: translateY(-1px); }
.add-goal-btn:disabled { opacity: 0.5; cursor: not-allowed; transform: none; }
/* Resources */
.resource-card { background: #fff; border: 1px solid #e2e8f0; border-radius: 12px; padding: 16px; margin-bottom: 12px; }
.resource-card h4 { margin: 0 0 6px; }
.res-meta { display: flex; gap: 8px; margin-top: 8px; flex-wrap: wrap; }
.tag { font-size: 0.75em; padding: 2px 8px; border-radius: 10px; background: #f7fafc; color: #718096; }
</style>
