<script setup>
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { gsap } from 'gsap';
import { agentApi, goalApi, profileApi } from '../api/index';

const router = useRouter();
const learningGoals = ref([]);
const recommendedResources = ref([]);
const learningProgress = ref([]);
const selectedGoal = ref(null);
const isGenerating = ref(false);
const generatePrompt = ref('');
const activeTab = ref('goals');
const showAddGoalModal = ref(false);
const newGoal = ref({
  title: '',
  icon: '🎯',
  category: '编程开发',
  customCategory: ''
});

const availableIcons = ['🐍', '🧠', '🌍', '📊', '🎨', '🎵', '📝', '🔬', '💡', '🚀'];
const categories = ['编程开发', '人工智能', '语言学习', '数据科学', '艺术设计', '其他'];

const mockGoals = [
  { id: 1, title: '掌握 Python 编程', progress: 65, icon: '🐍', color: 'from-green-500 to-emerald-500', category: '编程开发', resources: [], currentResourceIndex: 0, completedResources: [], lastStudyTime: null },
  { id: 2, title: '深度学习入门', progress: 30, icon: '🧠', color: 'from-purple-500 to-indigo-500', category: '人工智能', resources: [], currentResourceIndex: 0, completedResources: [], lastStudyTime: null },
  { id: 3, title: '英语雅思备考', progress: 80, icon: '🌍', color: 'from-blue-500 to-cyan-500', category: '语言学习', resources: [], currentResourceIndex: 0, completedResources: [], lastStudyTime: null },
  { id: 4, title: '数据结构与算法', progress: 45, icon: '📊', color: 'from-orange-500 to-red-500', category: '编程开发', resources: [], currentResourceIndex: 0, completedResources: [], lastStudyTime: null }
];

const mockProgress = [
  { id: 1, resource: 'Python基础教程', progress: 100, status: 'completed', icon: '✅', goalId: 1 },
  { id: 2, resource: '机器学习入门', progress: 75, status: 'in_progress', icon: '📚', goalId: 2 },
  { id: 3, resource: '数据结构实战', progress: 30, status: 'in_progress', icon: '📖', goalId: 4 },
  { id: 4, resource: '雅思听力训练', progress: 60, status: 'in_progress', icon: '🎧', goalId: 3 },
  { id: 5, resource: '深度学习实战', progress: 0, status: 'pending', icon: '⏳', goalId: 2 }
];

const tabs = [
  { id: 'goals', label: '学习目标', icon: '🎯' },
  { id: 'progress', label: '学习进度', icon: '📊' }
];

const getGoalIcon = (title) => {
  if (!title) return '📚';
  const t = title.toLowerCase();
  if (t.includes('深度') || t.includes('ai') || t.includes('人工智能') || t.includes('机器学习') || t.includes('神经网络') || t.includes('深度学习')) return '🤖';
  if (t.includes('数据') || t.includes('结构') || t.includes('算法') || t.includes('leetcode') || t.includes('刷题')) return '🧮';
  if (t.includes('编程') || t.includes('python') || t.includes('代码') || t.includes('java') || t.includes('c++') || t.includes('javascript') || t.includes('js') || t.includes('typescript')) return '💻';
  if (t.includes('数学') || t.includes('代数') || t.includes('概率') || t.includes('微积分') || t.includes('线性代数') || t.includes('高数')) return '📐';
  if (t.includes('英语') || t.includes('语言') || t.includes('口语') || t.includes('托福') || t.includes('雅思') || t.includes('gre')) return '🌍';
  if (t.includes('设计') || t.includes('ui') || t.includes('ux') || t.includes('figma') || t.includes('sketch')) return '🎨';
  if (t.includes('网络') || t.includes('web') || t.includes('前端') || t.includes('后端') || t.includes('vue') || t.includes('react') || t.includes('angular')) return '🌐';
  if (t.includes('数据库') || t.includes('sql') || t.includes('mysql') || t.includes('postgres') || t.includes('mongodb')) return '🗄️';
  if (t.includes('安全') || t.includes('加密') || t.includes('渗透') || t.includes('漏洞')) return '🔒';
  if (t.includes('测试') || t.includes('自动化') || t.includes('selenium') || t.includes('junit')) return '🧪';
  if (t.includes('运维') || t.includes('devops') || t.includes('docker') || t.includes('kubernetes') || t.includes('k8s')) return '⚙️';
  if (t.includes('商业') || t.includes('分析') || t.includes('营销') || t.includes('管理')) return '💼';
  if (t.includes('写作') || t.includes('论文') || t.includes('报告')) return '✍️';
  if (t.includes('视频') || t.includes('剪辑') || t.includes('pr') || t.includes('ae')) return '🎬';
  return '📚';
};

// 规范化resources：version=2嵌套格式→扁平展示
function normalizeResources(raw) {
  if (!raw) return [];
  let data;
  try { data = typeof raw === 'string' ? JSON.parse(raw) : raw; } catch { return []; }
  // version=2: 嵌套阶段格式
  if (data?.version === 2 && data?.stages) {
    const flat = [];
    for (const stage of data.stages) {
      for (const day of (stage.days || [])) {
        for (const task of (day.tasks || [])) {
          flat.push({
            id: task.taskId,
            title: task.title,
            type: task.type,
            status: task.status,
            stageName: stage.name,
            dayIndex: day.dayIndex
          });
        }
      }
    }
    return flat;
  }
  // version=1: 扁平数组
  return Array.isArray(data) ? data : [];
}

// 获取阶段进度摘要
function getStageSummary(raw) {
  if (!raw) return { stages: 0, progress: 0, currentStage: '' };
  let data;
  try { data = typeof raw === 'string' ? JSON.parse(raw) : raw; } catch { return { stages: 0, progress: 0, currentStage: '' }; }
  if (data?.version === 2) {
    let total = 0, completed = 0;
    for (const s of (data.stages || [])) {
      total++;
      if (s.status === 'completed') completed++;
      for (const d of (s.days || [])) {
        for (const t of (d.tasks || [])) {
          if (t.status === 'completed') completed++;
        }
      }
    }
    const currentStage = data.stages.find(s => s.status === 'active' || s.status === 'in_progress');
    return {
      stages: data.totalStages || data.stages.length,
      progress: data.stages.length > 0 ? Math.round(
        data.stages.reduce((sum, s) => sum + (s.status === 'completed' ? 1 : 0), 0) / data.stages.length * 100
      ) : 0,
      currentStage: currentStage?.name || ''
    };
  }
  return { stages: 0, progress: 0, currentStage: '' };
}

onMounted(async () => {
  const user = localStorage.getItem('user');
  const userId = user ? JSON.parse(user).id : null;
  if (userId) {
    try {
      const response = await goalApi.getGoalsByUserId(userId);
      if (response.data && response.data.data) {
        learningGoals.value = response.data.data.map(g => ({
          ...g,
          icon: (g.icon && g.icon.trim() && g.icon.length === 1) ? g.icon : getGoalIcon(g.title),
          resources: normalizeResources(g.resources),
          completedResources: g.completedResources ? JSON.parse(g.completedResources) : [],
          _rawResources: g.resources || '[]'
        }));
      }
    } catch (err) {
      console.error('Failed to fetch learning goals:', err);
      learningGoals.value = [];
    }
  } else {
    learningGoals.value = [];
  }
  calculateLearningProgress();
  animatePage();
});

const calculateLearningProgress = () => {
  const progress = [];
  learningGoals.value.forEach(goal => {
    const resources = goal.resources || [];
    const completedResources = goal.completedResources || [];
    // 检测是否为AI方案（有stageName的version=2格式）
    const isAiPlan = resources.length > 0 && resources[0].stageName;

    if (isAiPlan) {
      // AI方案: 按阶段显示进度
      const seenStages = new Set();
      resources.forEach((r) => {
        if (!seenStages.has(r.stageName)) {
          seenStages.add(r.stageName);
          const stageTasks = resources.filter(t => t.stageName === r.stageName);
          const stageCompleted = stageTasks.filter(t => completedResources.includes(t.id)).length;
          const stagePct = stageTasks.length > 0 ? Math.round(stageCompleted / stageTasks.length * 100) : 0;
          progress.push({
            id: r.stageName, resource: r.stageName,
            progress: stagePct,
            status: stagePct >= 100 ? 'completed' : stagePct > 0 ? 'in_progress' : 'pending',
            icon: '🗺️', goalId: goal.id, goalTitle: goal.title, goalColor: goal.color
          });
        }
      });
    } else {
      // 普通目标: 逐项显示
      resources.forEach((resource, index) => {
        const isCompleted = completedResources.includes(resource.id);
        progress.push({
          id: resource.id, resource: resource.title,
          progress: isCompleted ? 100 : (index < goal.currentResourceIndex ? 100 : 0),
          status: isCompleted ? 'completed' : (index === goal.currentResourceIndex ? 'in_progress' : 'pending'),
          icon: resource.type === '视频' ? '🎬' : resource.type === '文章' ? '📝' : '📚',
          goalId: goal.id, goalTitle: goal.title, goalColor: goal.color
        });
      });
    }
  });
  learningProgress.value = progress;
};

// 按学习目标分组
const collapsedGoals = ref(new Set());

function toggleGoalGroup(goalTitle) {
  if (collapsedGoals.value.has(goalTitle)) {
    collapsedGoals.value.delete(goalTitle);
  } else {
    collapsedGoals.value.add(goalTitle);
  }
}

const groupedProgress = computed(() => {
  const groups = {};
  learningProgress.value.forEach(item => {
    const key = item.goalTitle || '未分类';
    if (!groups[key]) groups[key] = { items: [], overallPct: 0, goalId: item.goalId };
    groups[key].items.push(item);
  });
  // 计算每个目标的总体进度
  for (const key of Object.keys(groups)) {
    const items = groups[key].items;
    const total = items.reduce((s, i) => s + i.progress, 0);
    groups[key].overallPct = items.length > 0 ? Math.round(total / items.length) : 0;
  }
  return groups;
});

const animatePage = () => {
  gsap.fromTo('.page-header',
    { opacity: 0, y: -20 },
    { opacity: 1, y: 0, duration: 0.6 }
  );
  
  gsap.fromTo('.tab-btn',
    { opacity: 0, scale: 0.9 },
    { opacity: 1, scale: 1, duration: 0.3, stagger: 0.1, delay: 0.2 }
  );
};

const selectGoal = (goal) => {
  selectedGoal.value = goal;
};

const openAddGoalModal = () => {
  showAddGoalModal.value = true;
};

const closeAddGoalModal = () => {
  showAddGoalModal.value = false;
  newGoal.value = {
    title: '',
    icon: '🎯',
    category: '编程开发',
    customCategory: ''
  };
};

const addGoal = () => {
  if (!newGoal.value.title.trim()) return;
  
  const category = newGoal.value.category === '其他' 
    ? (newGoal.value.customCategory.trim() || '其他')
    : newGoal.value.category;
  
  const goalData = {
    title: newGoal.value.title,
    icon: newGoal.value.icon,
    category: category
  };
  
  closeAddGoalModal();
  
  router.push({
    path: '/learning/generate',
    query: goalData
  });
};

const continueLearning = async (goal) => {
  if (goal.progress === 0) {
    goal.progress = 1;
    const user = localStorage.getItem('user');
    const userId = user ? JSON.parse(user).id : null;
    if (userId) {
      try {
        await goalApi.updateGoal(userId, goal.id, { progress: goal.progress });
      } catch (err) {
        console.error('Failed to update goal:', err);
      }
    }
  }

  // 根据学习进度更新用户画像
  const user = localStorage.getItem('user');
  const userId = user ? JSON.parse(user).id : null;
  if (userId && goal) {
    try {
      await profileApi.updateFromLearningProgress(
        goal.title || '',
        goal.category || '',
        goal.progress || 0
      );
      console.log('用户画像已根据学习进度更新');
    } catch (err) {
      console.error('更新用户画像失败:', err);
    }
  }

  router.push(`/learning/content/${goal.id}`);
};

const deleteGoal = async (goalId) => {
  if (confirm('确定要删除这个学习目标吗？')) {
    const user = localStorage.getItem('user');
    const userId = user ? JSON.parse(user).id : null;
    if (userId) {
      try {
        await goalApi.deleteGoal(userId, goalId);
      } catch (err) {
        console.error('Failed to delete goal:', err);
      }
    }
    learningGoals.value = learningGoals.value.filter(g => g.id !== goalId);
    if (selectedGoal.value?.id === goalId) {
      selectedGoal.value = null;
    }
  }
};

const generateResource = async () => {
  if (!generatePrompt.value.trim()) return;
  
  isGenerating.value = true;
  try {
    const response = await agentApi.generateResource(1, generatePrompt.value);
    if (response.data.success) {
      recommendedResources.value.unshift({
        id: Date.now(),
        title: response.data.title,
        summary: response.data.summary,
        content: response.data.content,
        keywords: response.data.keywords,
        agent: response.data.agent,
        createdAt: new Date().toLocaleString()
      });
    }
  } catch (err) {
    console.error('Failed to generate resource:', err);
  } finally {
    isGenerating.value = false;
    generatePrompt.value = '';
  }
};

const startLearning = (resource) => {
  const progress = learningProgress.value.find(p => p.id === resource.id);
  if (progress && progress.status === 'pending') {
    progress.status = 'in_progress';
  }

  // 根据学习进度更新用户画像
  const user = localStorage.getItem('user');
  const userId = user ? JSON.parse(user).id : null;
  if (userId && resource) {
    profileApi.updateFromLearningProgress(
      resource.resource || '',
      resource.goalTitle || '',
      0
    ).catch(err => console.error('更新用户画像失败:', err));
  }

  if (resource.goalId) {
    router.push(`/learning/content/${resource.goalId}`);
  }
};
</script>

<template>
  <div class="learning-page">
    <div class="page-header">
      <div class="header-content">
        <h1>个性化学习</h1>
        <p>基于大模型的智能学习路径规划与资源生成</p>
      </div>
      <div class="user-stats">
        <div class="stat-item">
          <span class="stat-icon">🎯</span>
          <span class="stat-value">{{ learningGoals.length }}</span>
          <span class="stat-label">学习目标</span>
        </div>
        <div class="stat-item">
          <span class="stat-icon">📚</span>
          <span class="stat-value">{{ learningGoals.filter(g => g.progress > 0 && g.progress < 100).length }}</span>
          <span class="stat-label">进行中</span>
        </div>
        <div class="stat-item">
          <span class="stat-icon">✅</span>
          <span class="stat-value">{{ learningGoals.filter(g => g.progress >= 100).length }}</span>
          <span class="stat-label">已完成</span>
        </div>
      </div>
    </div>

    <div class="tabs-container">
      <button
        v-for="tab in tabs"
        :key="tab.id"
        :class="['tab-btn', { active: activeTab === tab.id }]"
        @click="activeTab = tab.id"
      >
        <span class="tab-icon">{{ tab.icon }}</span>
        <span>{{ tab.label }}</span>
      </button>
    </div>

    <div v-show="activeTab === 'goals'" class="tab-content">
      <div class="section-intro">
        <h2>我的学习目标</h2>
        <p>选择一个目标开始学习，智能体将为您推荐个性化学习路径</p>
      </div>
      
      <div class="goals-header">
        <button class="add-goal-btn" @click="openAddGoalModal">
          <span>+</span>
          <span>添加学习目标</span>
        </button>
      </div>
      
      <div class="goals-grid">
        <div 
          v-for="goal in learningGoals" 
          :key="goal.id"
          :class="['goal-card', { active: selectedGoal?.id === goal.id }]"
          @click="selectGoal(goal)"
        >
          <button class="delete-btn" @click.stop="deleteGoal(goal.id)">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
              <path d="M6 18L18 6M6 6l12 12" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
            </svg>
          </button>
          <div class="goal-header">
            <div :class="['goal-icon', goal.color]">
              {{ goal.icon }}
            </div>
            <span class="goal-category">{{ goal.category }}</span>
          </div>
          <h3>{{ goal.title }}</h3>
          <template v-if="getStageSummary(goal._rawResources)?.stages">
            <div class="stage-info">
              {{ getStageSummary(goal._rawResources).currentStage }}
              · {{ getStageSummary(goal._rawResources).stages }}个阶段
            </div>
          </template>
          <div class="progress-info">
            <div class="progress-bar-wrapper">
              <div :class="['progress-fill', goal.color]" :style="{ width: goal.progress + '%' }"></div>
            </div>
            <span class="progress-percent">{{ goal.progress }}%</span>
          </div>
          <div class="goal-actions">
            <button class="action-btn primary" @click.stop="continueLearning(goal)">
              继续学习
            </button>
          </div>
        </div>
      </div>

      <div v-if="selectedGoal" class="selected-goal-detail">
        <div class="detail-header">
          <h3>📋 {{ selectedGoal.title }}</h3>
          <span :class="['progress-badge', selectedGoal.color]">{{ selectedGoal.progress }}% 完成</span>
        </div>
        <p>您正在学习「{{ selectedGoal.title }}」，已完成 {{ selectedGoal.progress }}% 的学习内容。继续加油！</p>
        <div class="related-resources">
          <h4>相关学习资源</h4>
          <div class="mini-resources">
            <div 
              v-for="progress in learningProgress.filter(p => p.goalId === selectedGoal.id)" 
              :key="progress.id"
              class="mini-resource"
            >
              <span class="mini-icon">{{ progress.icon }}</span>
              <div class="mini-info">
                <span class="mini-title">{{ progress.resource }}</span>
                <div class="mini-progress">
                  <div class="mini-bar">
                    <div class="mini-fill" :style="{ width: progress.progress + '%' }"></div>
                  </div>
                  <span>{{ progress.progress }}%</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="showAddGoalModal" class="modal-overlay" @click.self="closeAddGoalModal">
      <div class="modal-content">
        <div class="modal-header">
          <h3>添加学习目标</h3>
          <button class="close-btn" @click="closeAddGoalModal">
            <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
              <path d="M6 6l8 8M14 6l-8 8" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
            </svg>
          </button>
        </div>
        
        <div class="modal-body">
          <div class="form-group">
            <label>目标名称</label>
            <input 
              v-model="newGoal.title" 
              type="text" 
              placeholder="请输入学习目标名称"
              class="form-input"
            />
          </div>
          
          <div class="form-group">
            <label>选择图标</label>
            <div class="icon-grid">
              <button 
                v-for="icon in availableIcons" 
                :key="icon"
                :class="['icon-btn', { active: newGoal.icon === icon }]"
                @click="newGoal.icon = icon"
              >
                {{ icon }}
              </button>
            </div>
          </div>
          
          <div class="form-group">
            <label>选择分类</label>
            <select v-model="newGoal.category" class="form-select">
              <option v-for="cat in categories" :key="cat" :value="cat">{{ cat }}</option>
            </select>
          </div>
          
          <div v-if="newGoal.category === '其他'" class="form-group custom-category-input">
            <label>自定义分类</label>
            <input 
              v-model="newGoal.customCategory" 
              type="text" 
              placeholder="请输入自定义分类名称"
              class="form-input"
            />
          </div>
        </div>
        
        <div class="modal-footer">
          <button class="modal-btn secondary" @click="closeAddGoalModal">取消</button>
          <button class="modal-btn primary" @click="addGoal">确认添加</button>
        </div>
      </div>
    </div>

    <div v-show="activeTab === 'generate'" class="tab-content">
      <div class="section-intro">
        <h2>智能资源生成</h2>
        <p>基于大模型，根据您的学习需求生成个性化学习资源</p>
      </div>
      
      <div class="generate-card">
        <div class="generate-header">
          <div class="ai-icon">🤖</div>
          <div class="ai-info">
            <h3>学习助手</h3>
            <p>我可以帮您生成学习资料、解答问题、制定学习计划</p>
          </div>
        </div>
        
        <div class="input-section">
          <textarea 
            v-model="generatePrompt" 
            placeholder="请输入您的学习需求，例如：
- 帮我生成一份深度学习入门教程
- 解释什么是神经网络
- 推荐学习Python的最佳路径
- 帮我复习数据结构知识"
            rows="4"
            class="prompt-input"
          ></textarea>
          <div class="input-hints">
            <span class="hint">💡</span>
            <span>提示：描述越详细，生成的资源越精准</span>
          </div>
        </div>
        
        <button 
          :disabled="isGenerating || !generatePrompt.trim()" 
          class="generate-btn"
          @click="generateResource"
        >
          <span v-if="isGenerating" class="loading-spinner"></span>
          <span v-else>
            <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
              <path d="M10 3l3 3-7 7h8v4h-4v-3l-4-4 4-4h3V3z" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
            </svg>
          </span>
          <span>{{ isGenerating ? '生成中...' : '生成资源' }}</span>
        </button>
      </div>

      <div v-if="recommendedResources.length > 0" class="generated-section">
        <div class="section-title">
          <h3>生成的资源</h3>
          <span class="count">{{ recommendedResources.length }} 个资源</span>
        </div>
        <div class="generated-list">
          <div 
            v-for="resource in recommendedResources" 
            :key="resource.id" 
            class="generated-card"
          >
            <div class="resource-header">
              <span class="resource-icon">✨</span>
              <div class="resource-info">
                <h4>{{ resource.title }}</h4>
                <div class="resource-meta">
                  <span class="agent-tag">由 {{ resource.agent }} 生成</span>
                  <span class="time-tag">{{ resource.createdAt }}</span>
                </div>
              </div>
            </div>
            <p class="resource-summary">{{ resource.summary }}</p>
            <div class="keywords">
              <span v-for="(keyword, idx) in resource.keywords.split(',')" :key="idx" class="keyword-tag">
                {{ keyword.trim() }}
              </span>
            </div>
            <div class="resource-content-wrapper">
              <pre class="resource-content">{{ resource.content }}</pre>
            </div>
            <div class="resource-actions">
              <button class="action-btn secondary">收藏</button>
              <button class="action-btn primary">开始学习</button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-show="activeTab === 'progress'" class="tab-content">
      <div class="section-intro">
        <h2>学习进度</h2>
        <p>追踪您的学习进度，了解学习成果</p>
      </div>
      
      <div class="progress-stats">
        <div class="stat-card">
          <div class="stat-circle">
            <span class="circle-value">{{ learningProgress.length > 0 ? Math.round(learningProgress.filter(p => p.status === 'completed').length / learningProgress.length * 100) : 0 }}%</span>
          </div>
          <span class="stat-label">总体完成率</span>
        </div>
        <div class="stat-card">
          <div class="stat-circle secondary">
            <span class="circle-value">{{ learningProgress.filter(p => p.status === 'in_progress').length }}</span>
          </div>
          <span class="stat-label">进行中</span>
        </div>
        <div class="stat-card">
          <div class="stat-circle success">
            <span class="circle-value">{{ learningProgress.filter(p => p.status === 'completed').length }}</span>
          </div>
          <span class="stat-label">已完成</span>
        </div>
      </div>

      <div class="progress-list">
        <template v-for="(group, gName) in groupedProgress" :key="gName">
          <!-- 上级：学习目标 -->
          <div class="progress-goal-header" @click="toggleGoalGroup(gName)">
            <span class="goal-folder-icon">{{ collapsedGoals.has(gName) ? '📁' : '📂' }}</span>
            <span class="goal-folder-title">{{ gName }}</span>
            <span class="goal-folder-pct">{{ group.overallPct }}%</span>
            <span class="goal-folder-arrow">{{ collapsedGoals.has(gName) ? '›' : '⌄' }}</span>
          </div>
          <!-- 下级：阶段/资源 -->
          <template v-if="!collapsedGoals.has(gName)">
          <div
            v-for="item in group.items"
            :key="item.id"
            class="progress-item"
          >
            <div class="progress-icon-wrap" :class="item.status">
              {{ item.icon }}
            </div>
            <div class="progress-info">
              <h4>{{ item.resource }}</h4>
              <div class="progress-bar-container">
                <div class="progress-bar">
                  <div
                    :class="['progress-fill', { completed: item.status === 'completed', pending: item.status === 'pending' }]"
                    :style="{ width: item.progress + '%' }"
                  ></div>
                </div>
                <span class="progress-text">{{ item.progress }}%</span>
              </div>
            </div>
            <div class="progress-status">
              <span :class="['status-badge', item.status]">
                {{ item.status === 'completed' ? '已完成' : item.status === 'in_progress' ? '进行中' : '待开始' }}
              </span>
            </div>
            <div class="progress-action">
              <button
                v-if="item.status !== 'completed'"
                class="action-btn small"
                @click="startLearning(item)"
            >
              {{ item.status === 'pending' ? '开始学习' : '继续学习' }}
            </button>
            <span v-else class="completed-badge">✓</span>
          </div>
        </div>
          </template>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped>
.learning-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 40px 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 32px;
  padding-bottom: 24px;
  border-bottom: 1px solid #e2e8f0;
}

.header-content h1 {
  font-size: 32px;
  font-weight: 700;
  color: #2d3748;
  margin-bottom: 8px;
}

.header-content p {
  color: #718096;
}

.user-stats {
  display: flex;
  gap: 20px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 16px 24px;
  background: white;
  border-radius: 12px;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.05);
}

.stat-icon {
  font-size: 20px;
  margin-bottom: 4px;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #667eea;
}

.stat-label {
  font-size: 12px;
  color: #a0aec0;
}

.tabs-container {
  display: flex;
  gap: 8px;
  margin-bottom: 32px;
  background: white;
  padding: 6px;
  border-radius: 12px;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.05);
}

.tab-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 14px 20px;
  border: none;
  border-radius: 10px;
  background: none;
  cursor: pointer;
  font-size: 15px;
  font-weight: 500;
  color: #718096;
  transition: all 0.3s ease;
}

.tab-btn:hover {
  background: #f7fafc;
  color: #4a5568;
}

.tab-btn.active {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.tab-icon {
  font-size: 18px;
}

.tab-content {
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.section-intro {
  margin-bottom: 24px;
}

.section-intro h2 {
  font-size: 24px;
  font-weight: 700;
  color: #2d3748;
  margin-bottom: 8px;
}

.section-intro p {
  color: #718096;
}

.goals-header {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 20px;
}

.add-goal-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
}

.add-goal-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4);
}

.add-goal-btn span:first-child {
  font-size: 18px;
  font-weight: 700;
}

.goals-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 20px;
  margin-bottom: 32px;
}

.goal-card {
  background: white;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.05);
  cursor: pointer;
  transition: all 0.3s ease;
  border: 2px solid transparent;
  position: relative;
}

.delete-btn {
  position: absolute;
  top: 12px;
  right: 12px;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 8px;
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: all 0.3s ease;
}

.goal-card:hover .delete-btn {
  opacity: 1;
}

.delete-btn:hover {
  background: rgba(239, 68, 68, 0.2);
  transform: scale(1.1);
}

.goal-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.1);
}

.goal-card.active {
  border-color: #667eea;
  background: #f7fafc;
}

.goal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.goal-icon {
  width: 50px;
  height: 50px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.goal-category {
  padding: 6px 12px;
  background: #f7fafc;
  border-radius: 20px;
  font-size: 12px;
  color: #4a5568;
  font-weight: 500;
}

.stage-info {
  font-size: 12px;
  color: #667eea;
  margin-top: 4px;
  font-weight: 500;
}

.goal-card h3 {
  font-size: 18px;
  font-weight: 600;
  color: #2d3748;
  margin-bottom: 16px;
}

.progress-info {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.progress-bar-wrapper {
  flex: 1;
  height: 8px;
  background: #e2e8f0;
  border-radius: 4px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  border-radius: 4px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  transition: width 0.5s ease;
}

.progress-percent {
  font-size: 14px;
  font-weight: 700;
  color: #667eea;
  min-width: 45px;
  text-align: right;
}

.goal-actions {
  display: flex;
  gap: 12px;
}

.action-btn {
  flex: 1;
  padding: 12px;
  border: none;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
}

.action-btn.primary {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.action-btn.primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4);
}

.action-btn.secondary {
  background: #f7fafc;
  color: #4a5568;
}

.action-btn.secondary:hover {
  background: #edf2f7;
}

.action-btn.small {
  padding: 8px 16px;
  font-size: 13px;
}

.selected-goal-detail {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 16px;
  padding: 28px;
  color: white;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.detail-header h3 {
  font-size: 20px;
  font-weight: 700;
}

.progress-badge {
  padding: 8px 16px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 20px;
  font-size: 14px;
  font-weight: 600;
}

.selected-goal-detail p {
  opacity: 0.9;
  margin-bottom: 20px;
}

.related-resources h4 {
  font-size: 16px;
  margin-bottom: 12px;
}

.mini-resources {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.mini-resource {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 10px;
}

.mini-icon {
  font-size: 20px;
}

.mini-info {
  flex: 1;
}

.mini-title {
  font-size: 14px;
  margin-bottom: 6px;
}

.mini-progress {
  display: flex;
  align-items: center;
  gap: 8px;
}

.mini-bar {
  flex: 1;
  height: 4px;
  background: rgba(255, 255, 255, 0.3);
  border-radius: 2px;
  overflow: hidden;
}

.mini-fill {
  height: 100%;
  background: white;
  border-radius: 2px;
}

.generate-card {
  background: white;
  border-radius: 16px;
  padding: 28px;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.05);
  margin-bottom: 32px;
}

.generate-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
  padding-bottom: 20px;
  border-bottom: 1px solid #e2e8f0;
}

.ai-icon {
  width: 60px;
  height: 60px;
  border-radius: 14px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
}

.ai-info h3 {
  font-size: 18px;
  font-weight: 600;
  color: #2d3748;
  margin-bottom: 4px;
}

.ai-info p {
  color: #718096;
  font-size: 14px;
}

.input-section {
  margin-bottom: 20px;
}

.prompt-input {
  width: 100%;
  padding: 16px;
  border: 2px solid #e2e8f0;
  border-radius: 12px;
  font-size: 15px;
  resize: vertical;
  min-height: 120px;
  transition: border-color 0.3s ease;
}

.prompt-input:focus {
  outline: none;
  border-color: #667eea;
}

.input-hints {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  color: #a0aec0;
  font-size: 14px;
}

.generate-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 14px 32px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 12px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
}

.generate-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(102, 126, 234, 0.4);
}

.generate-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.loading-spinner {
  width: 20px;
  height: 20px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.generated-section {
  margin-top: 32px;
}

.section-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.section-title h3 {
  font-size: 20px;
  font-weight: 700;
  color: #2d3748;
}

.count {
  color: #a0aec0;
  font-size: 14px;
}

.generated-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.generated-card {
  background: white;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.05);
}

.resource-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.resource-icon {
  font-size: 28px;
}

.resource-info h4 {
  font-size: 18px;
  font-weight: 600;
  color: #2d3748;
  margin-bottom: 4px;
}

.resource-meta {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: #a0aec0;
}

.agent-tag {
  color: #667eea;
}

.resource-summary {
  color: #718096;
  margin-bottom: 12px;
  font-size: 14px;
}

.keywords {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}

.keyword-tag {
  padding: 4px 12px;
  background: #f7fafc;
  border-radius: 20px;
  font-size: 12px;
  color: #4a5568;
}

.resource-content-wrapper {
  background: #f7fafc;
  border-radius: 10px;
  padding: 16px;
  margin-bottom: 16px;
  max-height: 300px;
  overflow-y: auto;
}

.resource-content {
  font-size: 14px;
  line-height: 1.6;
  color: #4a5568;
  white-space: pre-wrap;
}

.resource-actions {
  display: flex;
  gap: 12px;
}

.progress-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
  margin-bottom: 32px;
}

.stat-card {
  background: white;
  border-radius: 16px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.05);
}

.stat-circle {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 12px;
  position: relative;
}

.stat-circle::before {
  content: '';
  position: absolute;
  inset: 4px;
  background: white;
  border-radius: 50%;
}

.stat-circle .circle-value {
  position: relative;
  z-index: 1;
  font-size: 20px;
  font-weight: 700;
  color: #667eea;
}

.stat-circle.secondary {
  background: linear-gradient(135deg, #a0aec0 0%, #718096 100%);
}

.stat-circle.secondary .circle-value {
  color: #718096;
}

.stat-circle.success {
  background: linear-gradient(135deg, #48bb78 0%, #38a169 100%);
}

.stat-circle.success .circle-value {
  color: #38a169;
}

.stat-label {
  font-size: 14px;
  color: #718096;
}

.progress-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.progress-goal-header {
  display: flex; align-items: center; gap: 10px;
  padding: 12px 16px; background: #f8fafc; border-radius: 10px;
  cursor: pointer; margin: 8px 0 4px; border: 1px solid #e2e8f0;
}
.progress-goal-header:hover { background: #edf2f7; border-color: #667eea; }
.goal-folder-icon { font-size: 1.2em; }
.goal-folder-title { flex: 1; font-weight: 600; font-size: 0.95em; color: #2d3748; }
.goal-folder-pct { font-weight: 700; color: #667eea; font-size: 0.9em; }
.goal-folder-arrow { font-size: 1.2em; color: #a0aec0; }
.progress-item {
  display: flex;
  align-items: center;
  gap: 16px;
  background: white;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.05);
}

.progress-icon-wrap {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  background: #f7fafc;
}

.progress-icon-wrap.completed {
  background: #c6f6d5;
}

.progress-icon-wrap.in_progress {
  background: #feebc8;
}

.progress-icon-wrap.pending {
  background: #e2e8f0;
}

.progress-info {
  flex: 1;
}

.progress-info h4 {
  font-size: 16px;
  font-weight: 600;
  color: #2d3748;
  margin-bottom: 8px;
}

.progress-bar-container {
  display: flex;
  align-items: center;
  gap: 12px;
}

.progress-bar {
  flex: 1;
  height: 6px;
  background: #e2e8f0;
  border-radius: 3px;
  overflow: hidden;
}

.progress-text {
  font-size: 14px;
  font-weight: 600;
  color: #667eea;
}

.progress-status {
  min-width: 80px;
}

.status-badge {
  padding: 6px 14px;
  border-radius: 20px;
  font-size: 13px;
  font-weight: 600;
}

.status-badge.completed {
  background: #c6f6d5;
  color: #276749;
}

.status-badge.in_progress {
  background: #feebc8;
  color: #c05621;
}

.status-badge.pending {
  background: #e2e8f0;
  color: #718096;
}

.completed-badge {
  color: #48bb78;
  font-size: 20px;
  font-weight: bold;
}

.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  animation: fadeIn 0.3s ease;
}

.modal-content {
  background: white;
  border-radius: 16px;
  width: 90%;
  max-width: 500px;
  overflow: hidden;
  animation: slideUp 0.3s ease;
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  border-bottom: 1px solid #e2e8f0;
}

.modal-header h3 {
  font-size: 18px;
  font-weight: 600;
  color: #2d3748;
}

.close-btn {
  background: none;
  border: none;
  color: #a0aec0;
  cursor: pointer;
  padding: 8px;
  border-radius: 8px;
  transition: all 0.2s ease;
}

.close-btn:hover {
  background: #f7fafc;
  color: #4a5568;
}

.modal-body {
  padding: 24px;
}

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: #4a5568;
  margin-bottom: 8px;
}

.form-input {
  width: 100%;
  padding: 12px 16px;
  border: 2px solid #e2e8f0;
  border-radius: 10px;
  font-size: 15px;
  transition: border-color 0.3s ease;
}

.form-input:focus {
  outline: none;
  border-color: #667eea;
}

.form-select {
  width: 100%;
  padding: 12px 16px;
  border: 2px solid #e2e8f0;
  border-radius: 10px;
  font-size: 15px;
  background: white;
  cursor: pointer;
  transition: border-color 0.3s ease;
}

.form-select:focus {
  outline: none;
  border-color: #667eea;
}

.icon-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
}

.icon-btn {
  width: 100%;
  padding: 12px;
  font-size: 24px;
  border: 2px solid #e2e8f0;
  border-radius: 10px;
  background: white;
  cursor: pointer;
  transition: all 0.3s ease;
}

.icon-btn:hover {
  border-color: #667eea;
  background: #f7fafc;
}

.icon-btn.active {
  border-color: #667eea;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.modal-footer {
  display: flex;
  gap: 12px;
  padding: 20px 24px;
  border-top: 1px solid #e2e8f0;
  justify-content: flex-end;
}

.modal-btn {
  padding: 12px 24px;
  border: none;
  border-radius: 10px;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
}

.modal-btn.primary {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.modal-btn.primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4);
}

.modal-btn.secondary {
  background: #f7fafc;
  color: #4a5568;
}

.modal-btn.secondary:hover {
  background: #edf2f7;
}

@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    text-align: center;
    gap: 20px;
  }
  
  .user-stats {
    width: 100%;
  }
  
  .stat-item {
    flex: 1;
  }
  
  .progress-stats {
    grid-template-columns: 1fr;
  }
  
  .progress-item {
    flex-wrap: wrap;
  }
  
  .progress-status {
    order: 4;
    width: 100%;
    text-align: center;
  }
}
</style>