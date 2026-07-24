<script setup>
import { ref, onMounted, computed } from 'vue';
import { useRouter } from 'vue-router';
import { gsap } from 'gsap';
import { goalApi, groupApi, resourceApi } from '../api/index';

const router = useRouter();

const currentUser = ref(null);
const learningGoals = ref([]);
const recentResources = ref([]);
const groupRankings = ref([]);

const topGroups = computed(() => {
  return [...groupRankings.value].sort((a, b) => b.score - a.score).slice(0, 5);
});

const userStats = ref({
  passCount: 0,
  resourceCount: 0,
  streak: 0,
  level: 1
});

onMounted(async () => {
  const user = localStorage.getItem('user');
  if (user) {
    currentUser.value = JSON.parse(user);
    userStats.value.level = currentUser.value.level || 1;
  }
  
  const userId = currentUser.value?.id || null;
  
  if (userId) {
    try {
      const goalResponse = await goalApi.getGoalsByUserId(userId);
      if (goalResponse.data && goalResponse.data.data) {
        learningGoals.value = goalResponse.data.data.map(g => ({
          id: g.id,
          name: g.title,
          icon: (g.icon && g.icon.trim() && g.icon.length === 1) ? g.icon : getGoalIcon(g.title),
          progress: g.progress || 0,
          resources: g.resources ? JSON.parse(g.resources) : [],
          completedResources: g.completedResources ? JSON.parse(g.completedResources) : []
        }));
      }
    } catch (err) {
      console.error('Failed to fetch learning goals:', err);
      learningGoals.value = [];
    }
  }
  
  try {
    const groupResponse = await groupApi.getGroupRankings();
    if (groupResponse.data && groupResponse.data.data) {
      groupRankings.value = groupResponse.data.data.map(g => ({
        id: g.id,
        name: g.groupName || g.name,
        icon: getGroupIcon(g.course || g.groupName),
        score: g.score || 0,
        memberCount: g.memberCount || 0,
        postCount: g.postCount || 0
      }));
    }
  } catch (err) {
    console.error('Failed to fetch group rankings:', err);
    groupRankings.value = [];
  }

  // 加载推荐资源（从后端最新6条中取前4条）
  try {
    const resRes = await resourceApi.getFeaturedResources();
    if (resRes.data && resRes.data.data) {
      recentResources.value = resRes.data.data.slice(0, 4).map(r => ({
        id: r.id,
        title: r.title,
        category: r.category || '推荐',
        entryType: r.entryType,
        icon: getResourceIcon(r.title, r.category)
      }));
    }
  } catch (err) {
    console.error('Failed to fetch resources:', err);
    recentResources.value = [];
  }

  userStats.value.passCount = learningGoals.value.filter(g => g.progress >= 100).length;
  userStats.value.resourceCount = learningGoals.value.reduce((sum, g) => sum + (g.resources.length || 0), 0);
  
  userStats.value.streak = currentUser.value?.loginDays || 0;
  
  animateHome();
});

const viewResource = (resource) => {
  if (!resource || !resource.id) return;
  if (resource.entryType === 'LEARNING_PLAN') {
    router.push({ name: 'PlanDetail', query: { id: resource.id, title: resource.title } });
  } else {
    router.push({
      name: 'KnowledgeResource',
      query: { resourceId: resource.id, resourceTitle: resource.title, resourceCategory: resource.category }
    });
  }
};

const getResourceIcon = (title, category) => {
  if (!title) return '📖';
  const t = (title + (category || '')).toLowerCase();
  if (t.includes('python') || t.includes('编程')) return '🐍';
  if (t.includes('数学') || t.includes('线性') || t.includes('高数')) return '🧮';
  if (t.includes('机器学习') || t.includes('ai') || t.includes('深度')) return '🤖';
  if (t.includes('web') || t.includes('前端') || t.includes('后端')) return '🌐';
  if (t.includes('英语') || t.includes('四级') || t.includes('六级')) return '📝';
  if (t.includes('java')) return '☕';
  return '📖';
};

const getGroupIcon = (name) => {
  if (!name) return '📚';
  const n = name.toLowerCase();
  if (n.includes('python') || n.includes('编程') || n.includes('算法')) return '💻';
  if (n.includes('数学') || n.includes('高数')) return '📐';
  if (n.includes('英语') || n.includes('口语')) return '🌍';
  if (n.includes('ai') || n.includes('人工智能') || n.includes('机器学习')) return '🤖';
  if (n.includes('设计') || n.includes('ui') || n.includes('ux')) return '🎨';
  if (n.includes('商业') || n.includes('案例')) return '💼';
  return '📚';
};

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

const animateHome = () => {
  gsap.fromTo('.welcome-banner',
    { opacity: 0, y: 30 },
    { opacity: 1, y: 0, duration: 0.8, ease: 'power3.out' }
  );
  gsap.fromTo('.stat-card',
    { opacity: 0, y: 20 },
    { opacity: 1, y: 0, duration: 0.4, stagger: 0.1, delay: 0.3 }
  );
  gsap.fromTo('.panel-card',
    { opacity: 0, y: 20 },
    { opacity: 1, y: 0, duration: 0.4, stagger: 0.15, delay: 0.5 }
  );
};

const goToGroups = () => {
  router.push('/study-groups');
};
</script>

<template>
  <div class="home-page">
    <div class="home-container">
      <div class="welcome-banner">
        <div class="welcome-text">
          <h1>欢迎回来，<span class="highlight">{{ currentUser?.nickname }}</span></h1>
          <p>继续您的智能学习之旅，今天也是进步的一天！</p>
        </div>
        <div class="welcome-stats">
          <div class="stat-card">
            <span class="stat-icon">🔥</span>
            <div class="stat-info">
              <span class="stat-num">{{ userStats.streak }}</span>
              <span class="stat-label">连续天数</span>
            </div>
          </div>
          <div class="stat-card">
            <span class="stat-icon">✅</span>
            <div class="stat-info">
              <span class="stat-num">{{ userStats.passCount }}</span>
              <span class="stat-label">通关数量</span>
            </div>
          </div>
          <div class="stat-card">
            <span class="stat-icon">📚</span>
            <div class="stat-info">
              <span class="stat-num">{{ userStats.resourceCount }}</span>
              <span class="stat-label">学习资源</span>
            </div>
          </div>
          <div class="stat-card">
            <span class="stat-icon">🏅</span>
            <div class="stat-info">
              <span class="stat-num">Lv.{{ userStats.level }}</span>
              <span class="stat-label">当前等级</span>
            </div>
          </div>
        </div>
      </div>

      <div class="quick-entry">
        <div class="entry-card" @click="goToGroups">
          <div class="entry-icon">👨‍👩‍👧‍👦</div>
          <div class="entry-info">
            <h3>学习小组</h3>
            <p>找到志同道合的学习伙伴，一起进步</p>
          </div>
          <div class="entry-arrow">
            <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
              <path d="M5 10h10M10 5l5 5-5 5" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
            </svg>
          </div>
        </div>
      </div>

      <div class="panels-grid">
        <div class="panel-card">
          <div class="panel-header">
            <h3>📋 我的学习目标</h3>
            <button class="panel-link" @click="router.push('/learning')">查看全部 →</button>
          </div>
          <div v-if="learningGoals.length === 0" class="panel-empty">
            <span class="empty-icon">🎯</span>
            <p>还没有学习目标</p>
            <button class="empty-btn" @click="router.push('/learning/generate')">创建学习目标</button>
          </div>
          <div v-else class="goal-list">
            <div v-for="goal in learningGoals.slice(0, 3)" :key="goal.id" class="goal-item">
              <span class="goal-icon">{{ goal.icon }}</span>
              <div class="goal-info">
                <span class="goal-name">{{ goal.name }}</span>
                <div class="progress-bar">
                  <div class="progress-fill" :style="{ width: goal.progress + '%' }"></div>
                </div>
              </div>
              <span class="goal-progress">{{ goal.progress }}%</span>
            </div>
          </div>
        </div>

        <div class="panel-card">
          <div class="panel-header">
            <h3>🤖 智能体推荐</h3>
            <button class="panel-link" @click="router.push('/assistant')">查看更多 →</button>
          </div>
          <div class="agent-list">
            <div class="agent-item">
              <span class="agent-icon">🧑‍🏫</span>
              <div class="agent-info">
                <span class="agent-name">学习导师</span>
                <span class="agent-desc">个性化学习规划与指导</span>
              </div>
              <button class="agent-btn" @click="router.push('/assistant')">对话</button>
            </div>
            <div class="agent-item">
              <span class="agent-icon">📝</span>
              <div class="agent-info">
                <span class="agent-name">出题专家</span>
                <span class="agent-desc">自动生成练习题与测试</span>
              </div>
              <button class="agent-btn" @click="router.push('/assistant')">对话</button>
            </div>
            <div class="agent-item">
              <span class="agent-icon">🔍</span>
              <div class="agent-info">
                <span class="agent-name">知识解析</span>
                <span class="agent-desc">深度剖析知识点与难点</span>
              </div>
              <button class="agent-btn" @click="router.push('/assistant')">对话</button>
            </div>
          </div>
        </div>

        <div class="panel-card">
          <div class="panel-header">
            <h3>📊 最近资源</h3>
            <button class="panel-link" @click="router.push('/resources')">查看更多 →</button>
          </div>
          <div class="resource-list">
            <div class="resource-item" v-for="r in recentResources" :key="r.id"
                 @click="viewResource(r)" style="cursor:pointer">
              <span class="res-icon">{{ r.icon }}</span>
              <span class="res-name">{{ r.title }}</span>
              <span class="res-badge">{{ r.category }}</span>
            </div>
            <div v-if="recentResources.length === 0" class="resource-item">
              <span class="res-name" style="color:#a0aec0">暂无资源，去学习页面生成吧 🚀</span>
            </div>
          </div>
        </div>

        <div class="panel-card">
          <div class="panel-header">
            <h3>🏆 学习小组排名</h3>
            <button class="panel-link" @click="router.push('/study-groups')">查看排行 →</button>
          </div>
          <div class="community-preview">
            <div class="rank-row" v-for="(group, index) in topGroups" :key="group.id">
              <span class="rank-num">{{ index + 1 }}</span>
              <span class="rank-avatar">{{ group.icon }}</span>
              <span class="rank-name">{{ group.name }}</span>
              <span class="rank-level">{{ group.score }}分</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.home-page {
  min-height: calc(100vh - 70px - 60px);
  display: flex;
  justify-content: center;
}

.home-container {
  max-width: 1200px;
  width: 100%;
  padding: 30px 40px;
}

.welcome-banner {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 20px;
  padding: 36px 40px;
  margin-bottom: 30px;
  color: white;
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 24px;
}

.welcome-text h1 {
  font-size: 28px;
  font-weight: 700;
  margin-bottom: 8px;
}

.highlight {
  color: #ffd700;
}

.welcome-text p {
  font-size: 16px;
  opacity: 0.9;
}

.welcome-stats {
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 10px;
  background: rgba(255, 255, 255, 0.15);
  border-radius: 14px;
  padding: 14px 20px;
  backdrop-filter: blur(10px);
}

.stat-icon {
  font-size: 24px;
}

.stat-info {
  display: flex;
  flex-direction: column;
}

.stat-num {
  font-size: 20px;
  font-weight: 700;
}

.stat-label {
  font-size: 12px;
  opacity: 0.85;
}

.quick-entry {
  margin-bottom: 24px;
}

.entry-card {
  background: white;
  border-radius: 18px;
  padding: 20px 28px;
  display: flex;
  align-items: center;
  gap: 16px;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.06);
  cursor: pointer;
  transition: all 0.3s ease;
  border: 2px solid transparent;
}

.entry-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 30px rgba(102, 126, 234, 0.2);
  border-color: #667eea;
}

.entry-icon {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.1) 0%, rgba(118, 75, 162, 0.1) 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
}

.entry-info {
  flex: 1;
}

.entry-info h3 {
  font-size: 18px;
  font-weight: 700;
  color: #2d3748;
  margin-bottom: 4px;
}

.entry-info p {
  font-size: 14px;
  color: #718096;
}

.entry-arrow {
  color: #667eea;
  transition: transform 0.3s ease;
}

.entry-card:hover .entry-arrow {
  transform: translateX(4px);
}

.panels-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 24px;
}

.panel-card {
  background: white;
  border-radius: 18px;
  padding: 24px;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.06);
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.panel-header h3 {
  font-size: 18px;
  font-weight: 700;
  color: #2d3748;
}

.panel-link {
  background: none;
  border: none;
  color: #667eea;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: color 0.2s;
}

.panel-link:hover {
  color: #764ba2;
}

.panel-empty {
  text-align: center;
  padding: 30px 0;
}

.empty-icon {
  font-size: 40px;
  display: block;
  margin-bottom: 12px;
}

.panel-empty p {
  color: #a0aec0;
  margin-bottom: 16px;
}

.empty-btn {
  padding: 10px 24px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 20px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.3s;
}

.empty-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4);
}

.goal-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.goal-item {
  display: flex;
  align-items: center;
  gap: 14px;
}

.goal-icon {
  font-size: 24px;
}

.goal-info {
  flex: 1;
  min-width: 0;
}

.goal-name {
  font-size: 14px;
  font-weight: 600;
  color: #2d3748;
  display: block;
  margin-bottom: 6px;
}

.progress-bar {
  height: 6px;
  background: #edf2f7;
  border-radius: 3px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 3px;
  transition: width 0.3s;
}

.goal-progress {
  font-size: 14px;
  font-weight: 600;
  color: #667eea;
}

.agent-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.agent-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px;
  border-radius: 12px;
  background: #f7fafc;
  transition: all 0.3s;
}

.agent-item:hover {
  background: #edf2f7;
}

.agent-icon {
  font-size: 28px;
}

.agent-info {
  flex: 1;
}

.agent-name {
  font-size: 14px;
  font-weight: 600;
  color: #2d3748;
  display: block;
}

.agent-desc {
  font-size: 12px;
  color: #a0aec0;
}

.agent-btn {
  padding: 6px 16px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 14px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.3s;
}

.agent-btn:hover {
  transform: scale(1.05);
}

.resource-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.resource-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 10px;
  background: #f7fafc;
}

.res-icon {
  font-size: 20px;
}

.res-name {
  flex: 1;
  font-size: 14px;
  color: #2d3748;
  font-weight: 500;
}

.res-badge {
  font-size: 11px;
  padding: 2px 10px;
  border-radius: 10px;
  background: #e8f0fe;
  color: #667eea;
  font-weight: 600;
}

.community-preview {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.rank-row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 10px 12px;
  border-radius: 10px;
  background: #f7fafc;
}

.rank-num {
  font-size: 18px;
  font-weight: 700;
  color: #667eea;
  width: 24px;
  text-align: center;
}

.rank-avatar {
  font-size: 22px;
}

.rank-name {
  flex: 1;
  font-size: 14px;
  font-weight: 500;
  color: #2d3748;
}

.rank-level {
  font-size: 13px;
  color: #667eea;
  font-weight: 600;
}

@media (max-width: 768px) {
  .home-container {
    padding: 20px;
  }
  
  .welcome-banner {
    padding: 24px;
    flex-direction: column;
  }
  
  .welcome-stats {
    gap: 12px;
  }

  .stat-card {
    padding: 10px 14px;
  }

  .panels-grid {
    grid-template-columns: 1fr;
  }
}
</style>