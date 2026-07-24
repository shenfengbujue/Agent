<script setup>
import { ref, computed, onMounted } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { gsap } from 'gsap';
import { knowledgeApi, goalApi } from '../api/index';
import axios from 'axios';
import MarkdownRenderer from '../components/common/MarkdownRenderer.vue';
import LearningPathTimeline from '../components/learning/LearningPathTimeline.vue';
import KnowledgeModuleCard from '../components/learning/KnowledgeModuleCard.vue';
import KnowledgeGraphViewer from '../components/learning/KnowledgeGraphViewer.vue';
import ExerciseCard from '../components/learning/ExerciseCard.vue';

const router = useRouter();
const route = useRoute();

const goalId = ref(null);
const goal = ref(null);
const currentResource = ref(null);
const currentProgress = ref(0);
const isMarkingComplete = ref(false);
const sidebarOpen = ref(false);
const knowledgeItem = ref(null);
const expandedSection = ref(null);
const selectedVideoSection = ref(null);
const activeTab = ref('summary');
const planData = ref(null);
const isAiPlan = ref(false);

const summaryText = computed(() => {
  const s = planData.value?.summary;
  if (!s) return '';
  if (typeof s === 'string') return s;
  if (typeof s === 'object') {
    const sec = s.sections || {};
    return [
      s.query ? `**查询：** ${s.query}` : '',
      sec.learningPath ? `### 学习路径\n${sec.learningPath}` : '',
      sec.overallAdvice ? `### 总体建议\n${sec.overallAdvice}` : '',
      sec.knowledgeModules ? `### 知识模块\n${sec.knowledgeModules}` : '',
      sec.exerciseOverview ? `### 练习概览\n${sec.exerciseOverview}` : '',
      sec.mindMapDescription ? `### 思维导图\n${sec.mindMapDescription}` : '',
      sec.readingRecommendations ? `### 阅读推荐\n${sec.readingRecommendations}` : '',
    ].filter(Boolean).join('\n\n');
  }
  return String(s);
});

// 标准化练习题：处理嵌套 {quizzes, exercises, difficultyRatio} 格式
const flatExercises = computed(() => {
  const ex = planData.value?.exercises;
  if (!ex) return [];
  if (Array.isArray(ex)) return ex;
  if (ex.exercises) return ex.exercises;
  return [];
});

// 规范化resources：version=2嵌套→扁平
function normalizeRes(raw) {
  if (!raw) return [];
  let data;
  try { data = typeof raw === 'string' ? JSON.parse(raw) : raw; } catch { return []; }
  if (data?.version === 2 && data?.stages) {
    const flat = [];
    for (const stage of data.stages) {
      for (const day of (stage.days || [])) {
        for (const task of (day.tasks || [])) {
          flat.push({
            id: task.taskId,
            title: task.title,
            type: task.type === 'knowledge' ? '文章' : task.type === 'exercise' ? '练习' : task.type,
            stageName: stage.name,
            dayIndex: day.dayIndex,
            _task: task
          });
        }
      }
    }
    return flat;
  }
  return Array.isArray(data) ? data : [];
}

onMounted(() => {
 goalId.value = parseInt(route.params.goalId);
 loadGoal();
 animatePage();
});
const loadGoal = async () => {
 const user = localStorage.getItem('user');
 const userId = user ? JSON.parse(user).id : null;
 if (!userId) {
 router.push('/login');
 return;
 }
 try {
 const response = await goalApi.getGoalsByUserId(userId);
 if (response.data && response.data.data) {
 const goals = response.data.data.map(g => ({
 ...g,
 resources: g.resources ? normalizeRes(g.resources) : [],
	 _rawRes: g.resources || '[]',
 completedResources: g.completedResources ? JSON.parse(g.completedResources) : []
 }));
 goal.value = goals.find(g => g.id === goalId.value);
 if (goal.value) {
 // 解析planData（存于learningPath字段）
 const raw = goal.value.learningPath || goal.value._rawRes;
 if (raw) {
   try {
     const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw;
     if (parsed && (parsed.exercises || parsed.graph || parsed.knowledge)) {
       planData.value = parsed;
       isAiPlan.value = true;
     }
   } catch(e) { console.error('planData解析失败:', e, raw?.substring(0,200)); }
 }
 currentResource.value = goal.value.resources[goal.value.currentResourceIndex];
 currentProgress.value = goal.value.progress;
 if (!isAiPlan.value) loadKnowledgeItem();
 }
 }
 } catch (err) {
 console.error('Failed to load goal:', err);
 }
};
const loadKnowledgeItem = async () => {
 if (currentResource.value && currentResource.value.title) {
 try {
 const res = await knowledgeApi.getKnowledgeItemByTitle(currentResource.value.title);
 if (res.data && res.data.data) {
 knowledgeItem.value = res.data.data;
 }
 } catch (err) {
 console.error('Failed to fetch knowledge item:', err);
 }
 }
};
const animatePage = () => {
 gsap.fromTo('.content-header', { opacity: 0, y: -20 }, { opacity: 1, y: 0, duration: 0.6 });
 gsap.fromTo('.main-content', { opacity: 0, y: 20 }, { opacity: 1, y: 0, duration: 0.5, delay: 0.2 });
};
const progressPercent = computed(() => {
 if (!goal.value || goal.value.resources.length === 0)
 return 0;
 const completedCount = goal.value.completedResources.length;
 return Math.round((completedCount / goal.value.resources.length) * 100);
});
const markComplete = () => {
 if (!goal.value || !currentResource.value)
 return;
 isMarkingComplete.value = true;
 setTimeout(() => {
 if (!goal.value.completedResources.includes(currentResource.value.id)) {
 goal.value.completedResources.push(currentResource.value.id);
 }
 const completedCount = goal.value.completedResources.length;
 goal.value.progress = Math.round((completedCount / goal.value.resources.length) * 100);
 if (goal.value.currentResourceIndex < goal.value.resources.length - 1) {
 goal.value.currentResourceIndex++;
 currentResource.value = goal.value.resources[goal.value.currentResourceIndex];
 }
 saveGoal();
 isMarkingComplete.value = false;
 }, 1000);
};
const prevResource = () => {
 if (goal.value && goal.value.currentResourceIndex > 0) {
 goal.value.currentResourceIndex--;
 currentResource.value = goal.value.resources[goal.value.currentResourceIndex];
 loadKnowledgeItem();
 saveGoal();
 }
};
const nextResource = () => {
 if (goal.value && goal.value.currentResourceIndex < goal.value.resources.length - 1) {
 goal.value.currentResourceIndex++;
 currentResource.value = goal.value.resources[goal.value.currentResourceIndex];
 loadKnowledgeItem();
 saveGoal();
 }
};
const selectResource = (index) => {
 goal.value.currentResourceIndex = index;
 currentResource.value = goal.value.resources[index];
 loadKnowledgeItem();
 saveGoal();
};
const saveGoal = async () => {
 const user = localStorage.getItem('user');
 const userId = user ? JSON.parse(user).id : null;
 if (!userId || !goal.value) return;
 try {
 await goalApi.updateGoal(userId, goal.value.id, {
 progress: goal.value.progress,
 currentResourceIndex: goal.value.currentResourceIndex,
 completedResources: JSON.stringify(goal.value.completedResources),
 resources: goal.value._rawRes || JSON.stringify(goal.value.resources)
 });
 } catch (err) {
 console.error('Failed to save goal:', err);
 }
};
const finalExam = ref(null);
const finalAnswers = ref({});
const finalResult = ref(null);
const examLoading = ref(false);
async function startFinalExam() {
  examLoading.value = true;
  try {
    const res = await axios.post(`/api/daily/goals/${goal.value.id}/final-exam`,
      { subject: goal.value.title },
      { headers: { Authorization: `Bearer ${localStorage.getItem('token')}` } });
    finalExam.value = res.data?.data || res.data;
  } catch(e) { alert('生成失败'); }
  examLoading.value = false;
}
async function submitFinalExam() {
  const exam = finalExam.value;
  if (!exam?.questions) return;
  let correct = 0;
  exam.questions.forEach(q => { if (finalAnswers.value[q.id] === q.answer) correct++; });
  finalResult.value = {
    correct, total: exam.totalQuestions || 0,
    pct: exam.totalQuestions ? Math.round(correct / exam.totalQuestions * 100) : 0,
    passed: exam.totalQuestions ? (correct * 100 / exam.totalQuestions) >= (exam.passScore || 75) : false
  };
}
const goBack = () => {
 router.push('/learning');
};
const toggleSidebar = () => {
 sidebarOpen.value = !sidebarOpen.value;
};
const toggleSection = (index) => {
 if (expandedSection.value === index) {
 expandedSection.value = null;
 } else {
 expandedSection.value = index;
 }
};
const playVideoSection = (index) => {
  router.push({ 
    path: '/video/player', 
    query: { resourceId: knowledgeItem.value.id, sectionIndex: index } 
  });
};
</script>

<template>
  <div class="learning-content-page">
    <div class="content-header">
      <button class="back-btn" @click="goBack">
        <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
          <path d="M15 10H5M5 10l5-5M5 10l5 5" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
        </svg>
        返回
      </button>
      <div class="header-info">
        <h1>{{ goal?.title }}</h1>
        <div class="progress-info">
          <span class="progress-label">学习进度</span>
          <div class="mini-progress-bar">
            <div class="mini-progress-fill" :style="{ width: progressPercent + '%' }"></div>
          </div>
          <span class="progress-value">{{ progressPercent }}%</span>
        </div>
      </div>
      <button class="sidebar-toggle" @click="toggleSidebar">
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
          <path d="M4 6h16M4 12h16M4 18h16" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
        </svg>
      </button>
    </div>
    
    <div class="content-wrapper">
      <aside :class="['sidebar', { open: sidebarOpen }]">
        <div class="sidebar-header">
          <h3>{{ isAiPlan ? '🗺️ 学习阶段' : '📚 学习资源列表' }}</h3>
          <button class="close-sidebar" @click="toggleSidebar">
            <svg width="18" height="18" viewBox="0 0 20 20" fill="none">
              <path d="M6 14l4-4-4-4M14 14l-4-4 4-4" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
            </svg>
          </button>
        </div>
        <!-- AI方案：阶段导航 -->
        <div v-if="isAiPlan && planData" class="resource-list">
          <div
            v-for="(stage, si) in (planData.learningPath?.stages || [])"
            :key="'stage-'+si"
            :class="['resource-item', { active: goal?.currentStageIndex === si }]"
            @click="router.push(`/learning/stage/${goal?.id}/${si}`)"
          >
            <span class="resource-number">{{ si + 1 }}</span>
            <div class="resource-content">
              <span class="resource-title">{{ stage.name }}</span>
              <span class="resource-type">{{ stage.difficulty }} · {{ stage.days || '?' }}天</span>
            </div>
          </div>
        </div>
        <!-- 旧格式：扁平资源列表 -->
        <div v-else class="resource-list">
          <div
            v-for="(resource, index) in goal?.resources"
            :key="resource.id"
            :class="['resource-item', {
              active: index === goal?.currentResourceIndex,
              completed: goal?.completedResources.includes(resource.id)
            }]"
            @click="selectResource(index)"
          >
            <span class="resource-number">{{ index + 1 }}</span>
            <div class="resource-content">
              <span class="resource-title">{{ resource.title }}</span>
              <span class="resource-type">{{ resource.type }} · {{ resource.duration }}</span>
            </div>
            <span v-if="goal?.completedResources.includes(resource.id)" class="completed-icon">✓</span>
          </div>
        </div>
        <!-- 综合期末考试 -->
        <div v-if="isAiPlan" class="final-exam-entry" @click="startFinalExam">
          <span>🎓</span>
          <span>综合期末考试</span>
          <span class="final-arrow">›</span>
        </div>
      </aside>

      <main class="main-content">
        <div v-if="currentResource" class="resource-view">
          <div class="resource-header">
            <div class="resource-icon-large">
              {{ currentResource.type === '视频' ? '🎬' : currentResource.type === '文章' ? '📝' : currentResource.type === '练习' ? '✍️' : currentResource.type === '测验' ? '📋' : '📚' }}
            </div>
            <div class="resource-meta">
              <span class="type-badge">{{ currentResource.type }}</span>
              <span class="duration">{{ currentResource.duration }}</span>
              <span :class="['difficulty', currentResource.difficulty]">{{ currentResource.difficulty }}</span>
            </div>
          </div>
          
          <h2 class="resource-title">{{ currentResource.title }}</h2>

          <!-- AI方案：6Tab视图 -->
          <div v-if="isAiPlan && planData" class="content-area">
            <div class="plan-tab-bar">
              <button v-for="t in [
                {k:'summary',l:'📋 总览'},{k:'path',l:'🗺️ 学习路径'},{k:'knowledge',l:'📖 知识模块'},
                {k:'graph',l:'🧠 思维导图'},{k:'exercises',l:'✍️ 练习题'},{k:'resources',l:'🌐 拓展阅读'}
              ]" :key="t.k" :class="['plan-tab-btn', {active:activeTab===t.k}]" @click="activeTab=t.k">{{ t.l }}</button>
            </div>
            <div class="plan-tab-body">
              <div v-show="activeTab==='summary'" class="plan-tab-panel">
                <div v-if="planData.requirements" class="req-tags">
                  <span class="req-tag" v-if="planData.requirements.subject">📚 {{ planData.requirements.subject }}</span>
                  <span class="req-tag" v-if="planData.requirements.level">📊 {{ planData.requirements.level }}</span>
                  <span class="req-tag" v-if="planData.requirements.goal">🎯 {{ planData.requirements.goal }}</span>
                </div>
                <MarkdownRenderer :content="summaryText" />
              </div>
              <div v-show="activeTab==='path'" class="plan-tab-panel">
                <LearningPathTimeline :stages="planData.learningPath?.stages || []" :current-stage-index="goal?.currentStageIndex||0" />
              </div>
              <div v-show="activeTab==='knowledge'" class="plan-tab-panel">
                <KnowledgeModuleCard v-for="(mod,i) in (planData.knowledge||[])" :key="i" :module="mod" :index="i+1" />
                <div v-if="!planData.knowledge?.length" class="empty-tab">暂无知识模块</div>
              </div>
              <div v-show="activeTab==='graph'" class="plan-tab-panel">
                <KnowledgeGraphViewer :nodes="planData.graph?.nodes||[]" :edges="planData.graph?.edges||[]" :text-outline="planData.graph?.textOutline||''" />
              </div>
              <div v-show="activeTab==='exercises'" class="plan-tab-panel">
                <ExerciseCard v-for="(ex,i) in (planData.exercises?.exercises||[])" :key="i" :exercise="ex" :index="i+1" />
                <div v-if="!planData.exercises?.exercises?.length" class="empty-tab">暂无练习题</div>
              </div>
              <div v-show="activeTab==='resources'" class="plan-tab-panel">
                <div v-for="(r,i) in (planData.webSearch?.resources||[])" :key="i" class="res-card">
                  <h4>{{ r.title }}</h4><p>{{ r.summary }}</p>
                  <div class="res-meta"><span class="tag">{{ r.type }}</span><span class="tag">{{ r.difficulty }}</span></div>
                </div>
                <div v-if="!planData.webSearch?.resources?.length" class="empty-tab">暂无拓展阅读</div>
              </div>
            </div>
          </div>

          <!-- 旧格式：扁平资源视图 -->
          <div v-else class="content-area">
            <div class="content-placeholder">
              <div v-if="currentResource.type === '视频'" class="video-chapters">
                <div class="video-intro">
                  <div class="video-icon-large">🎬</div>
                  <h3>{{ currentResource.title }}</h3>
                  <p>{{ knowledgeItem?.content }}</p>
                </div>
                <div class="chapter-list">
                  <h4>📋 课程章节</h4>
                  <div class="chapters-grid">
                    <div 
                      v-for="(section, idx) in knowledgeItem?.sections" 
                      :key="idx"
                      class="chapter-card"
                      @click="playVideoSection(idx)"
                    >
                      <div class="chapter-thumb">
                        <span class="chapter-num">{{ idx + 1 }}</span>
                        <span class="play-icon">▶</span>
                      </div>
                      <div class="chapter-details">
                        <div class="chapter-title">{{ section.title }}</div>
                        <div class="chapter-materials">📖 {{ section.materials }}</div>
                        <div class="chapter-meta">
                          <span v-if="section.startTime">⏱ {{ Math.floor(section.startTime / 60) }}:{{ (section.startTime % 60).toString().padStart(2, '0') }}</span>
                          <span v-if="section.videoUrl"> | 🎬 视频课程</span>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
              <div v-else class="article-content">
                <!-- AI方案任务内容 -->
                <div v-if="currentResource?._task" class="article-intro">
                  <template v-if="currentResource._task.type === 'knowledge'">
                    <MarkdownRenderer :content="currentResource._task.content?.basic || ''" />
                    <div v-if="currentResource._task.content?.keyPoints?.length" class="task-core-points">
                      <h4>🎯 核心重点</h4>
                      <ul><li v-for="p in currentResource._task.content.keyPoints" :key="p">{{ p }}</li></ul>
                    </div>
                    <div v-if="currentResource._task.content?.pitfalls?.length" class="task-pitfalls">
                      <h4>⚠️ 易错混淆</h4>
                      <ul><li v-for="p in currentResource._task.content.pitfalls" :key="p">{{ p }}</li></ul>
                    </div>
                  </template>
                  <template v-else-if="currentResource._task.type === 'exercise'">
                    <div class="exercise-content">
                      <h4>{{ currentResource._task.question }}</h4>
                      <div v-if="currentResource._task.options?.length" class="ex-options">
                        <div v-for="(opt, oi) in currentResource._task.options" :key="oi" class="ex-opt">
                          {{ String.fromCharCode(65 + oi) }}. {{ opt }}
                        </div>
                      </div>
                      <details class="task-answer">
                        <summary>查看答案与解析</summary>
                        <p><strong>答案：</strong>{{ currentResource._task.answer }}</p>
                        <p v-if="currentResource._task.analysis"><strong>解析：</strong>{{ currentResource._task.analysis }}</p>
                      </details>
                    </div>
                  </template>
                  <template v-else-if="currentResource._task.type === 'reading'">
                    <p>{{ currentResource._task.content?.summary || currentResource._task.title }}</p>
                  </template>
                </div>
                <!-- 旧格式：知识库内容 -->
                <div v-else class="article-intro">
                  <MarkdownRenderer :content="knowledgeItem?.content || ''" />
                </div>
                <div v-if="knowledgeItem?.sections && knowledgeItem.sections.length > 0" class="sections-container">
                  <div v-for="(section, idx) in knowledgeItem.sections" :key="idx" 
                       :class="['section-item', { expanded: expandedSection === idx }]"
                       @click="toggleSection(idx)">
                    <div class="section-header">
                      <div class="section-number">{{ idx + 1 }}</div>
                      <div class="section-info">
                        <div class="section-title">{{ section.title }}</div>
                      </div>
                      <div class="section-arrow">{{ expandedSection === idx ? '▼' : '▶' }}</div>
                    </div>
                    <div v-if="expandedSection === idx" class="section-content">
                      <MarkdownRenderer :content="section.content || ''" />
                    </div>
                  </div>
                </div>
              </div>
              <div class="content-info">
                <span>📊 预计时长：{{ currentResource.duration }}</span>
                <span>🎯 难度等级：{{ currentResource.difficulty }}</span>
              </div>
            </div>
          </div>
          
          <div class="action-buttons">
            <button 
              :disabled="goal?.currentResourceIndex === 0"
              class="nav-btn"
              @click="prevResource"
            >
              <svg width="18" height="18" viewBox="0 0 20 20" fill="none">
                <path d="M15 10H5M5 10l5-5M5 10l5 5" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
              </svg>
              上一个
            </button>
            
            <button 
              :disabled="isMarkingComplete"
              class="complete-btn"
              @click="markComplete"
            >
              <span v-if="isMarkingComplete" class="loading-spinner"></span>
              <span v-else>✓ 标记完成</span>
            </button>
            
            <button 
              :disabled="goal?.currentResourceIndex === goal?.resources.length - 1"
              class="nav-btn"
              @click="nextResource"
            >
              下一个              <svg width="18" height="18" viewBox="0 0 20 20" fill="none">
                <path d="M5 10h10M10 5l5 5-5 5" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
              </svg>
            </button>
          </div>
        </div>
        
        <div v-else class="empty-state">
          <div class="empty-icon">📚</div>
          <h3>暂无学习资源</h3>
          <p>请先创建学习目标并生成学习资料</p>
          <button class="empty-btn" @click="goBack">
            返回创建学习目标
          </button>
        </div>
      </main>
    </div>
    
    <div v-if="sidebarOpen" class="overlay" @click="toggleSidebar"></div>
  </div>
</template>

<style scoped>
.learning-content-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #f5f7fa 0%, #e4e8ec 100%);
}

.content-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  background: white;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.05);
}

.back-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  background: #f7fafc;
  border: none;
  border-radius: 12px;
  color: #4a5568;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.back-btn:hover {
  background: #edf2f7;
  color: #667eea;
}

.header-info {
  flex: 1;
  margin-left: 20px;
}

.header-info h1 {
  font-size: 20px;
  font-weight: 600;
  color: #2d3748;
  margin-bottom: 8px;
}

.progress-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.progress-label {
  font-size: 13px;
  color: #718096;
}

.mini-progress-bar {
  width: 120px;
  height: 6px;
  background: #e2e8f0;
  border-radius: 3px;
  overflow: hidden;
}

.mini-progress-fill {
  height: 100%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 3px;
  transition: width 0.3s ease;
}

.progress-value {
  font-size: 14px;
  font-weight: 600;
  color: #667eea;
  min-width: 40px;
}

.sidebar-toggle {
  padding: 10px;
  background: #f7fafc;
  border: none;
  border-radius: 10px;
  color: #4a5568;
  cursor: pointer;
  transition: all 0.3s ease;
}

.sidebar-toggle:hover {
  background: #edf2f7;
}

.content-wrapper {
  display: flex;
  gap: 20px;
  padding: 20px;
  max-width: 1400px;
  margin: 0 auto;
}

.sidebar {
  width: 320px;
  background: white;
  border-radius: 16px;
  padding: 20px;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.05);
  flex-shrink: 0;
  position: fixed;
  left: -340px;
  top: 80px;
  z-index: 100;
  transition: left 0.3s ease;
}

.sidebar.open {
  left: 20px;
}

.sidebar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.sidebar-header h3 {
  font-size: 16px;
  font-weight: 600;
  color: #2d3748;
}

.close-sidebar {
  padding: 6px;
  background: #f7fafc;
  border: none;
  border-radius: 8px;
  color: #718096;
  cursor: pointer;
}
.sidebar-tabs { display:flex; flex-wrap:wrap; gap:4px; padding:8px 12px; border-top:1px solid #e2e8f0; }
.sidebar-tab { padding:6px 10px; font-size:12px; border-radius:6px; cursor:pointer; background:#f7fafc; color:#64748b; white-space:nowrap; }
.sidebar-tab:hover { background:#edf2f7; }
.sidebar-tab.active { background:#667eea; color:white; }

.resource-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.resource-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  background: #f7fafc;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s ease;
  border: 2px solid transparent;
}

.resource-item:hover {
  background: #edf2f7;
}

.resource-item.active {
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.1) 0%, rgba(118, 75, 162, 0.1) 100%);
  border-color: #667eea;
}

.resource-item.completed {
  opacity: 0.7;
}

.resource-number {
  width: 28px;
  height: 28px;
  background: #e2e8f0;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
  color: #4a5568;
  flex-shrink: 0;
}

.resource-item.active .resource-number {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.resource-content {
  flex: 1;
  min-width: 0;
}

.resource-title {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: #2d3748;
  margin-bottom: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.resource-type {
  font-size: 12px;
  color: #718096;
}

.completed-icon {
  width: 20px;
  height: 20px;
  background: #48bb78;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: white;
}

.main-content {
  flex: 1;
  min-width: 0;
}

.resource-view {
  background: white;
  border-radius: 20px;
  padding: 32px;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.08);
}

.resource-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}

.resource-icon-large {
  width: 64px;
  height: 64px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
}

.resource-meta {
  display: flex;
  gap: 12px;
}

.type-badge {
  padding: 6px 14px;
  background: #e2e8f0;
  border-radius: 20px;
  font-size: 13px;
  color: #4a5568;
}

.difficulty {
  padding: 6px 14px;
  border-radius: 20px;
  font-size: 13px;
  font-weight: 500;
}

.difficulty.入门 {
  background: #c6f6d5;
  color: #22543d;
}

.difficulty.基础 {
  background: #bee3f8;
  color: #1a365d;
}

.difficulty.中级 {
  background: #feebc8;
  color: #744210;
}

.difficulty.高级 {
  background: #fed7d7;
  color: #742a2a;
}

.resource-title {
  font-size: 24px;
  font-weight: 700;
  color: #2d3748;
  margin-bottom: 24px;
}

.content-area {
  background: #f7fafc;
  border-radius: 16px;
  padding: 32px;
  min-height: 300px;
  margin-bottom: 24px;
}

.content-placeholder {
  text-align: center;
}

.placeholder-icon {
  font-size: 64px;
  margin-bottom: 16px;
}
.task-core-points { margin-top: 12px; padding: 10px 14px; background: #f0fff4; border-radius: 8px; }
.task-core-points h4 { margin: 0 0 6px; color: #276749; }
.task-pitfalls { margin-top: 10px; padding: 10px 14px; background: #fff5f5; border-radius: 8px; border-left: 3px solid #fc8181; }
.task-pitfalls h4 { margin: 0 0 6px; color: #9b2c2c; }
.exercise-content { margin-top: 12px; }
.ex-options { display: flex; flex-direction: column; gap: 4px; margin: 8px 0; }
.ex-opt { padding: 8px 12px; background: #f7fafc; border-radius: 6px; }
.task-answer { margin-top: 10px; padding: 10px; background: #f8fafc; border-radius: 8px; }
/* Plan Tab */
.plan-tab-bar { display:flex; gap:2px; border-bottom:2px solid #e2e8f0; margin-bottom:16px; overflow-x:auto; }
.plan-tab-btn { padding:8px 14px; border:none; background:none; cursor:pointer; font-size:0.85em; color:#718096; white-space:nowrap; border-bottom:2px solid transparent; margin-bottom:-2px; }
.plan-tab-btn.active { color:#667eea; border-bottom-color:#667eea; font-weight:600; }
.plan-tab-panel { padding:8px 0; }
.empty-tab { text-align:center; padding:30px; color:#a0aec0; }
.req-tags { display:flex; gap:8px; margin-bottom:12px; flex-wrap:wrap; }
.req-tag { background:#f7f8ff; color:#667eea; padding:4px 12px; border-radius:20px; font-size:0.85em; }
.res-card { background:#fff; border:1px solid #e2e8f0; border-radius:10px; padding:14px; margin-bottom:10px; }
.res-card h4 { margin:0 0 4px; }
.res-meta { display:flex; gap:6px; margin-top:6px; }
.tag { font-size:0.75em; padding:2px 8px; border-radius:10px; background:#f7fafc; color:#718096; }

.content-placeholder h3 {
  font-size: 18px;
  font-weight: 600;
  color: #2d3748;
  margin-bottom: 8px;
}

.content-placeholder h3 {
  font-size: 20px;
  font-weight: 700;
  color: #2d3748;
  margin-bottom: 16px;
}

.content-images {
  display: flex;
  justify-content: center;
  gap: 16px;
  margin-bottom: 20px;
}

.content-image {
  font-size: 40px;
}

.video-container {
  width: 100%;
  max-width: 800px;
  margin: 0 auto 20px;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.15);
}

.video-placeholder {
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%);
  padding: 40px;
  text-align: center;
  color: white;
}

.video-icon {
  font-size: 64px;
  margin-bottom: 16px;
}

.video-placeholder h3 {
  font-size: 20px;
  font-weight: 600;
  margin-bottom: 12px;
}

.video-placeholder p {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.8);
  line-height: 1.6;
  margin-bottom: 24px;
}

.play-button {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 80px;
  height: 80px;
  background: rgba(102, 126, 234, 0.9);
  border: none;
  border-radius: 50%;
  color: white;
  font-size: 28px;
  cursor: pointer;
  transition: all 0.3s ease;
  z-index: 10;
}

.play-button:hover {
  transform: translate(-50%, -50%) scale(1.1);
  box-shadow: 0 8px 25px rgba(102, 126, 234, 0.5);
}

.video-progress {
  margin-bottom: 16px;
}

.progress-bar {
  height: 6px;
  background: rgba(255, 255, 255, 0.3);
  border-radius: 3px;
  overflow: hidden;
  margin-bottom: 8px;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #667eea 0%, #764ba2 100%);
  border-radius: 3px;
  transition: width 0.3s ease;
}

.progress-time {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.7);
}

.video-controls {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-bottom: 20px;
}

.control-btn {
  padding: 8px 20px;
  background: rgba(255, 255, 255, 0.2);
  border: none;
  border-radius: 20px;
  color: white;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.control-btn:hover {
  background: rgba(255, 255, 255, 0.3);
}

.video-sections {
  text-align: left;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  padding: 16px;
}

.video-section {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.9);
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  cursor: pointer;
  transition: all 0.2s ease;
}

.video-section:hover {
  background: rgba(255, 255, 255, 0.1);
}

.video-section:last-child {
  border-bottom: none;
}

.video-section .section-number {
  width: 24px;
  height: 24px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
}

.article-content {
  text-align: left;
  max-width: 800px;
  margin: 0 auto;
}

.video-chapters {
  text-align: left;
}

.video-intro {
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%);
  border-radius: 16px;
  padding: 32px;
  text-align: center;
  color: white;
  margin-bottom: 24px;
}

.video-icon-large {
  font-size: 64px;
  margin-bottom: 16px;
}

.video-intro h3 {
  font-size: 20px;
  font-weight: 600;
  margin-bottom: 12px;
}

.video-intro p {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.8);
  line-height: 1.6;
}

.chapter-list h4 {
  font-size: 18px;
  font-weight: 600;
  color: #2d3748;
  margin-bottom: 20px;
}

.chapters-grid {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.chapter-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  background: white;
  border-radius: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  cursor: pointer;
  transition: all 0.3s ease;
  border: 2px solid transparent;
}

.chapter-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(102, 126, 234, 0.15);
  border-color: #667eea;
}

.chapter-thumb {
  position: relative;
  width: 80px;
  height: 60px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.chapter-num {
  position: absolute;
  top: 8px;
  left: 8px;
  width: 24px;
  height: 24px;
  background: rgba(255, 255, 255, 0.3);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: white;
}

.play-icon {
  font-size: 20px;
  color: white;
  opacity: 0;
  transition: opacity 0.3s ease;
}

.chapter-card:hover .play-icon {
  opacity: 1;
}

.chapter-card:hover .chapter-num {
  opacity: 0;
}

.chapter-details {
  flex: 1;
  min-width: 0;
}

.chapter-title {
  font-size: 16px;
  font-weight: 600;
  color: #2d3748;
  margin-bottom: 8px;
}

.chapter-materials {
  font-size: 14px;
  color: #667eea;
  margin-bottom: 4px;
}

.chapter-meta {
  font-size: 13px;
  color: #718096;
}

.article-intro {
  background: white;
  border-radius: 12px;
  padding: 24px;
  margin-bottom: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.article-intro p {
  font-size: 15px;
  color: #4a5568;
  line-height: 1.8;
  margin-bottom: 0;
}

.sections-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.section-item {
  background: white;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  overflow: hidden;
  transition: all 0.3s ease;
}

.section-item.expanded {
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.1);
}

.section-header {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px;
  cursor: pointer;
}

.section-header:hover {
  background: #f7fafc;
}

.section-arrow {
  margin-left: auto;
  font-size: 12px;
  color: #a0aec0;
  transition: transform 0.3s ease;
}

.section-content {
  padding: 0 16px 16px 56px;
  background: #fafafa;
  border-top: 1px solid #e2e8f0;
}

.section-content p {
  font-size: 14px;
  color: #4a5568;
  line-height: 1.8;
  margin-bottom: 12px;
}

.section-content ul {
  margin: 0;
  padding-left: 20px;
}

.section-content li {
  font-size: 14px;
  color: #4a5568;
  margin-bottom: 8px;
}

.section-number {
  width: 28px;
  height: 28px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
  flex-shrink: 0;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: #2d3748;
}

.content-text {
  max-width: 800px;
  margin: 0 auto 20px;
  text-align: left;
}

.content-text p {
  font-size: 15px;
  color: #4a5568;
  line-height: 1.8;
  margin-bottom: 0;
}

.content-info {
  display: flex;
  justify-content: center;
  gap: 24px;
  margin-bottom: 24px;
}

.content-info span {
  font-size: 14px;
  color: #4a5568;
}

.simulated-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-width: 600px;
  margin: 0 auto;
}

.content-line {
  height: 8px;
  background: linear-gradient(90deg, #e2e8f0 0%, #cbd5e0 100%);
  border-radius: 4px;
}

.content-line.short {
  width: 60%;
}

.content-line.medium {
  width: 80%;
}

.action-buttons {
  display: flex;
  justify-content: center;
  gap: 16px;
}

.nav-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 24px;
  background: #f7fafc;
  border: 2px solid #e2e8f0;
  border-radius: 12px;
  color: #4a5568;
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
}

.nav-btn:hover:not(:disabled) {
  background: #edf2f7;
  border-color: #a0aec0;
}

.nav-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.complete-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 14px 32px;
  background: linear-gradient(135deg, #48bb78 0%, #38a169 100%);
  color: white;
  border: none;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
}

.complete-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(72, 187, 120, 0.4);
}

.complete-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.loading-spinner {
  width: 18px;
  height: 18px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.empty-state {
  background: white;
  border-radius: 20px;
  padding: 60px 40px;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.08);
  text-align: center;
}

.empty-icon {
  font-size: 64px;
  margin-bottom: 16px;
}

.empty-state h3 {
  font-size: 20px;
  font-weight: 600;
  color: #2d3748;
  margin-bottom: 8px;
}

.empty-state p {
  font-size: 14px;
  color: #718096;
  margin-bottom: 24px;
}

.empty-btn {
  padding: 14px 32px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
}

.empty-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(102, 126, 234, 0.4);
}

.overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 50;
}
.content-header { position: relative; z-index: 60; }
.content-header { position: relative; z-index: 60; }

@media (min-width: 1024px) {
  .sidebar {
    position: static;
    left: auto;
  }
  
  .sidebar-toggle, .close-sidebar, .overlay {
    display: none;
  }
  
  .sidebar.open {
    left: auto;
  }
}

@media (max-width: 768px) {
  .content-header {
    padding: 16px;
    flex-wrap: wrap;
    gap: 12px;
  }
  
  .header-info {
    margin-left: 0;
    order: 3;
    width: 100%;
  }
  
  .header-info h1 {
    font-size: 18px;
  }
  
  .resource-view {
    padding: 20px;
  }
  
  .content-area {
    padding: 20px;
  }
  
  .action-buttons {
    flex-wrap: wrap;
  }
  
  .nav-btn, .complete-btn {
    flex: 1;
    min-width: 140px;
  }
}
</style>