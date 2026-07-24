﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿<script setup>
import { ref, onMounted } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { gsap } from 'gsap';
import { knowledgeApi, goalApi } from '../api/index';
import MarkdownRenderer from '../components/common/MarkdownRenderer.vue';

const router = useRouter();
const route = useRoute();

const resource = ref(null);
const learningGoals = ref([]);
const showGoalSelector = ref(false);
const knowledgeItem = ref(null);
const loading = ref(true);
const expandedSection = ref(null);
const selectedVideoSection = ref(null);
const extractedKnowledgePoints = ref([]);

const extractKnowledgePoints = (content) => {
  if (!content) return [];
  const points = [];
  let pointIndex = 0;
  
  const cleanedContent = content.replace(/\r\n/g, '\n').replace(/\n{3,}/g, '\n\n');
  
  const headingRegex = /【([^】]+)】/g;
  let headingMatch;
  while ((headingMatch = headingRegex.exec(cleanedContent)) !== null) {
    const headingText = headingMatch[1].trim();
    if (headingText.length >= 2) {
      points.push({
        id: pointIndex++,
        text: headingText,
        type: 'heading'
      });
    }
  }
  
  const numberedRegex = /^\s*(\d+[.、．])\s*([^。！？；\n]{1,80})/gm;
  let numberedMatch;
  while ((numberedMatch = numberedRegex.exec(cleanedContent)) !== null) {
    const number = numberedMatch[1];
    const title = numberedMatch[2].trim();
    if (title.length >= 4) {
      points.push({
        id: pointIndex++,
        text: title,
        type: 'list'
      });
    }
  }

  const chineseNumRegex = /^\s*([一二三四五六七八九十]+[.、．])\s*([^。！？；\n]{1,80})/gm;
  let chineseMatch;
  while ((chineseMatch = chineseNumRegex.exec(cleanedContent)) !== null) {
    const number = chineseMatch[1];
    const title = chineseMatch[2].trim();
    if (title.length >= 4) {
      points.push({
        id: pointIndex++,
        text: title,
        type: 'list'
      });
    }
  }

  const bracketRegex = /^\s*(\(\d+\))\s*([^。！？；\n]{1,80})/gm;
  let bracketMatch;
  while ((bracketMatch = bracketRegex.exec(cleanedContent)) !== null) {
    const number = bracketMatch[1];
    const title = bracketMatch[2].trim();
    if (title.length >= 4) {
      points.push({
        id: pointIndex++,
        text: title,
        type: 'list'
      });
    }
  }

  const seen = new Set();
  const uniquePoints = points.filter(p => {
    if (seen.has(p.text)) return false;
    seen.add(p.text);
    return p.text.length >= 4;
  });
  
  return uniquePoints.slice(0, 8);
};

const categoryMapping = {
  'python': '编程开发',
  'ai': '人工智能',
  'math': '数学',
  'english': '英语',
  'business': '商业分析',
  'design': '设计',
  'other': '其他'
};

onMounted(async () => {
  const resourceId = parseInt(route.query.resourceId);
  const resourceTitle = route.query.resourceTitle;
  if (resourceId) {
    await fetchResourceById(resourceId);
  } else if (resourceTitle) {
    await fetchResourceByTitle(resourceTitle);
  }
  animatePage();
});

const fetchResourceById = async (id) => {
  loading.value = true;
  try {
    const res = await knowledgeApi.getKnowledgeItemById(id);
    if (res.data && res.data.data) {
      knowledgeItem.value = res.data.data;
      resource.value = {
        id: res.data.data.id,
        title: res.data.data.title,
        type: res.data.data.type,
        duration: res.data.data.duration,
        difficulty: res.data.data.difficulty,
        category: res.data.data.category,
        summary: res.data.data.content,
        views: res.data.data.viewCount || 0,
        likes: res.data.data.likeCount || 0,
        liked: !!res.data.data.liked
      };
      
      const allContent = [res.data.data.content || ''];
      if (res.data.data.sections) {
        res.data.data.sections.forEach(sec => {
          if (sec.content) allContent.push(sec.content);
        });
      }
      extractedKnowledgePoints.value = extractKnowledgePoints(allContent.join('\n\n'));
      
      try {
        const viewRes = await knowledgeApi.recordView(id);
        if (viewRes.data && viewRes.data.code === 200 && viewRes.data.data) {
          if (resource.value) {
            resource.value.views = viewRes.data.data.viewCount;
          }
        }
      } catch (viewErr) {
        console.error('记录浏览量失败:', viewErr);
      }
    }
  } catch (err) {
    console.error('Failed to fetch resource:', err);
  }
  loading.value = false;
};

const fetchResourceByTitle = async (title) => {
  loading.value = true;
  try {
    const res = await knowledgeApi.getKnowledgeItemByTitle(title);
    if (res.data && res.data.data) {
      knowledgeItem.value = res.data.data;
      resource.value = {
        id: res.data.data.id,
        title: res.data.data.title,
        type: res.data.data.type,
        duration: res.data.data.duration,
        difficulty: res.data.data.difficulty,
        category: res.data.data.category,
        summary: res.data.data.content,
        views: res.data.data.viewCount || 0,
        likes: res.data.data.likeCount || 0,
        liked: !!res.data.data.liked
      };
      
      const allContent = [res.data.data.content || ''];
      if (res.data.data.sections) {
        res.data.data.sections.forEach(sec => {
          if (sec.content) allContent.push(sec.content);
        });
      }
      extractedKnowledgePoints.value = extractKnowledgePoints(allContent.join('\n\n'));
      
      if (res.data.data.id) {
        try {
          const viewRes = await knowledgeApi.recordView(res.data.data.id);
          if (viewRes.data && viewRes.data.code === 200 && viewRes.data.data) {
            if (resource.value) {
              resource.value.views = viewRes.data.data.viewCount;
            }
          }
        } catch (viewErr) {
          console.error('记录浏览量失败:', viewErr);
        }
      }
    }
  } catch (err) {
    console.error('Failed to fetch resource:', err);
  }
  loading.value = false;
};

const handleToggleLike = async () => {
  if (!resource.value || !resource.value.id) return;
  try {
    const res = await knowledgeApi.toggleLike(resource.value.id);
    if (res.data && res.data.code === 200 && res.data.data) {
      resource.value.liked = res.data.data.liked;
      resource.value.likes = res.data.data.likeCount;
    }
  } catch (e) {
    console.error('点赞失败:', e);
    alert('点赞失败，请重试');
  }
};

const animatePage = () => {
  gsap.fromTo('.content-header', { opacity: 0, y: -20 }, { opacity: 1, y: 0, duration: 0.6 });
  gsap.fromTo('.main-content', { opacity: 0, y: 20 }, { opacity: 1, y: 0, duration: 0.5, delay: 0.2 });
};

const goBack = () => {
  router.push('/resources');
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

const loadLearningGoals = async () => {
  const user = localStorage.getItem('user');
  const userId = user ? JSON.parse(user).id : null;
  if (!userId) return;
  try {
    const response = await goalApi.getGoalsByUserId(userId);
    if (response.data && response.data.data) {
      learningGoals.value = response.data.data.map(g => ({
        ...g,
        icon: (g.icon && g.icon.trim() && g.icon.length === 1) ? g.icon : getGoalIcon(g.title),
        resources: g.resources ? JSON.parse(g.resources) : []
      }));
    }
  } catch (err) {
    console.error('Failed to load learning goals:', err);
  }
};

const addToGoal = async (goalId) => {
  const user = localStorage.getItem('user');
  const userId = user ? JSON.parse(user).id : null;
  if (!userId) {
    alert('请先登录');
    router.push('/login');
    return;
  }
  const goal = learningGoals.value.find(g => g.id === goalId);
  if (!goal) return;
  
  const newResource = {
    id: knowledgeItem.value.id,
    title: knowledgeItem.value.title,
    type: knowledgeItem.value.type,
    category: knowledgeItem.value.category,
    duration: knowledgeItem.value.duration,
    difficulty: knowledgeItem.value.difficulty
  };
  
  if (goal.resources.some(r => r.id === newResource.id)) {
    alert('该资源已在学习目标中');
    return;
  }
  
  goal.resources.push(newResource);
  
  try {
    await goalApi.updateGoal(userId, goalId, {
      resources: JSON.stringify(goal.resources)
    });
    alert('资源已添加到学习目标！');
    loadLearningGoals();
  } catch (err) {
    console.error('Failed to update goal:', err);
    alert('添加失败');
  }
};

const createNewGoalAndAdd = async () => {
  const user = localStorage.getItem('user');
  const userId = user ? JSON.parse(user).id : null;
  if (!userId) {
    alert('请先登录');
    router.push('/login');
    return;
  }
  
  const goalTitle = prompt('请输入新学习目标名称：');
  if (!goalTitle) return;
  
  const colors = [
    'from-green-500 to-emerald-500',
    'from-purple-500 to-indigo-500',
    'from-blue-500 to-cyan-500',
    'from-orange-500 to-red-500',
    'from-pink-500 to-rose-500',
    'from-teal-500 to-cyan-500'
  ];
  
  const newResource = {
    id: knowledgeItem.value.id,
    title: knowledgeItem.value.title,
    type: knowledgeItem.value.type,
    category: knowledgeItem.value.category,
    duration: knowledgeItem.value.duration,
    difficulty: knowledgeItem.value.difficulty
  };
  
  try {
    await goalApi.createGoal(userId, {
      title: goalTitle,
      icon: '📚',
      category: knowledgeItem.value.category || '其他',
      color: colors[Math.floor(Math.random() * colors.length)],
      progress: 0,
      resources: JSON.stringify([newResource]),
      currentResourceIndex: 0,
      completedResources: JSON.stringify([])
    });
    alert('学习目标已创建，资源已添加！');
    loadLearningGoals();
  } catch (err) {
    console.error('Failed to create goal:', err);
    alert('创建失败：' + (err.response?.data?.message || '未知错误'));
  }
};

const getCategoryName = (category) => {
  return categoryMapping[category] || '其他';
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
        返回资源中心
      </button>
      <div class="header-info">
        <h1>{{ resource?.title || '学习资源' }}</h1>
        <div class="category-badge">{{ getCategoryName(resource?.category) }}</div>
      </div>
    </div>
    
    <div class="content-wrapper">
      <main class="main-content">
        <div v-if="loading" class="loading-state">
          <div class="loader"></div>
          <p>加载中...</p>
        </div>
        
        <div v-else-if="resource" class="resource-view">
          <div class="resource-header">
            <div class="resource-icon-large">
              {{ resource.type === '视频' ? '🎬' : resource.type === '文章' ? '📝' : resource.type === '练习' ? '✍️' : resource.type === '测验' ? '📋' : '📚' }}
            </div>
            <div class="resource-meta">
              <span class="type-badge">{{ resource.type }}</span>
              <span class="duration">{{ resource.duration }}</span>
              <span :class="['difficulty', resource.difficulty]">{{ resource.difficulty }}</span>
            </div>
          </div>
          
          <h2 class="resource-title">{{ resource.title }}</h2>

          <div class="resource-stats-row">
            <span class="stat-item">👁️ 浏览 {{ resource.views }}</span>
            <button
              :class="['like-btn', { liked: resource.liked }]"
              @click="handleToggleLike"
            >
              {{ resource.liked ? '❤️' : '🤍' }} 点赞 {{ resource.likes }}
            </button>
          </div>

          <div class="add-to-goal-section">
            <button class="add-to-goal-btn" @click="loadLearningGoals(); showGoalSelector = true">
              🎯 加入学习目标
            </button>
          </div>
          
          <div class="content-area">
            <div class="content-placeholder">
              <div v-if="resource.type === '视频'" class="video-chapters">
                <div class="video-intro">
                  <div class="video-icon-large">🎬</div>
                  <h3>{{ resource.title }}</h3>
                  <div class="video-notice">
                    <span>📝</span>
                    <span>本资源为视频类型但暂无视频文件，以下为图文学习内容</span>
                  </div>
                </div>
                
                <div v-if="extractedKnowledgePoints.length > 0" class="knowledge-points-section">
                  <h3 class="points-title">📚 学习要点</h3>
                  <div class="knowledge-points-grid">
                    <div 
                      v-for="(point, index) in extractedKnowledgePoints" 
                      :key="point.id"
                      :class="['knowledge-point-item', point.type]"
                    >
                      <span class="point-number">{{ index + 1 }}</span>
                      <span class="point-text">{{ point.text }}</span>
                    </div>
                  </div>
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
                      <p><MarkdownRenderer :content="section.content" /></p>
                    </div>
                  </div>
                </div>
              </div>
              <div v-else class="article-content">
                <div v-if="extractedKnowledgePoints.length > 0" class="knowledge-points-section">
                  <h3 class="points-title">📚 学习要点</h3>
                  <div class="knowledge-points-grid">
                    <div 
                      v-for="(point, index) in extractedKnowledgePoints" 
                      :key="point.id"
                      :class="['knowledge-point-item', point.type]"
                    >
                      <span class="point-number">{{ index + 1 }}</span>
                      <span class="point-text">{{ point.text }}</span>
                    </div>
                  </div>
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
                      <p><MarkdownRenderer :content="section.content" /></p>
                    </div>
                  </div>
                </div>
              </div>
              <!-- 正文内容（Markdown渲染） -->
              <div v-if="resource.summary" class="content-body">
                <MarkdownRenderer :content="resource.summary" />
              </div>
              <div class="content-info">
                <span v-if="resource.duration">📊 预计时长：{{ resource.duration }}</span>
                <span v-if="resource.difficulty">🎯 难度等级：{{ resource.difficulty }}</span>
              </div>
            </div>
          </div>
          
          <div class="action-buttons">
            <button class="back-btn-large" @click="goBack">
              ← 返回资源中心
            </button>
          </div>
        </div>
        
        <div v-else class="empty-state">
          <div class="empty-icon">📚</div>
          <h3>资源不存在</h3>
          <p>无法找到该学习资源</p>
          <button class="empty-btn" @click="goBack">
            返回资源中心
          </button>
        </div>
      </main>
    </div>
    
    <div v-if="showGoalSelector" class="goal-selector-overlay" @click.self="showGoalSelector = false">
      <div class="goal-selector-modal">
        <div class="modal-header">
          <h3>🎯 选择学习目标</h3>
          <button class="close-btn" @click="showGoalSelector = false">✕</button>
        </div>
        <div class="modal-body">
          <div v-if="learningGoals.length === 0" class="no-goals">
            <div class="no-goals-icon">📭</div>
            <p>暂无学习目标</p>
            <button class="create-goal-btn" @click="createNewGoalAndAdd(); showGoalSelector = false">
              创建新目标并添加
            </button>
          </div>
          <div v-else class="goals-list">
            <div 
              v-for="goal in learningGoals" 
              :key="goal.id"
              class="goal-option"
              @click="addToGoal(goal.id); showGoalSelector = false"
            >
              <div :class="['goal-icon-small', goal.color]">{{ goal.icon }}</div>
              <div class="goal-option-info">
                <span class="goal-option-title">{{ goal.title }}</span>
                <span class="goal-option-category">{{ goal.category }}</span>
              </div>
              <div class="goal-option-progress">
                <div class="mini-bar">
                  <div :class="['mini-fill', goal.color]" :style="{ width: goal.progress + '%' }"></div>
                </div>
                <span>{{ goal.progress }}%</span>
              </div>
            </div>
            <div class="add-new-goal">
              <button class="create-goal-btn" @click="createNewGoalAndAdd(); showGoalSelector = false">
                + 创建新学习目标
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
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

.category-badge {
  display: inline-block;
  padding: 4px 12px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 500;
}

.content-wrapper {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
}

.main-content {
  flex: 1;
}

.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 60px;
}

.loader {
  width: 48px;
  height: 48px;
  border: 4px solid #e2e8f0;
  border-top-color: #667eea;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
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

.content-placeholder h3 {
  font-size: 18px;
  font-weight: 600;
  color: #2d3748;
  margin-bottom: 12px;
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

.article-content {
  text-align: left;
  max-width: 800px;
  margin: 0 auto;
}

.video-notice {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: linear-gradient(135deg, #fef3c7 0%, #fde68a 100%);
  border-radius: 8px;
  margin-bottom: 16px;
  font-size: 14px;
  color: #92400e;
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

.knowledge-points-section {
  background: white;
  border-radius: 12px;
  padding: 24px;
  margin-bottom: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.points-title {
  font-size: 18px;
  font-weight: 600;
  color: #2d3748;
  margin-bottom: 16px;
}

.knowledge-points-grid {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.knowledge-point-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 18px;
  background: white;
  border-radius: 10px;
  transition: all 0.2s ease;
  border-left: 4px solid #667eea;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}

.knowledge-point-item:hover {
  background: #f8fafc;
  transform: translateX(4px);
}

.knowledge-point-item.heading {
  background: linear-gradient(135deg, #fef3c7 0%, #fde68a 100%);
  border-left: 4px solid #f59e0b;
}

.knowledge-point-item.list {
  background: linear-gradient(135deg, #dcfce7 0%, #bbf7d0 100%);
  border-left: 4px solid #10b981;
}

.knowledge-point-item.paragraph {
  background: #f8fafc;
  border-left: 4px solid #667eea;
}

.point-number {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border-radius: 50%;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}

.knowledge-point-item.heading .point-number {
  background: linear-gradient(135deg, #f59e0b 0%, #d97706 100%);
}

.knowledge-point-item.list .point-number {
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
}

.knowledge-point-item.paragraph .point-number {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.point-text {
  font-size: 14px;
  color: #4a5568;
  line-height: 1.7;
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

.content-body {
  margin: 20px 0;
  padding: 20px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  line-height: 1.8;
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

.back-btn-large {
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

.back-btn-large:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(102, 126, 234, 0.4);
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
}

.add-to-goal-section {
  margin-bottom: 20px;
}

.resource-stats-row {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 20px;
  padding: 12px 16px;
  background: #f7fafc;
  border-radius: 12px;
}

.stat-item {
  font-size: 15px;
  color: #4a5568;
  font-weight: 500;
}

.like-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: white;
  border: 2px solid #e2e8f0;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  color: #4a5568;
  cursor: pointer;
  transition: all 0.2s ease;
}

.like-btn:hover {
  border-color: #e53e3e;
  color: #e53e3e;
  background: rgba(229, 62, 62, 0.05);
}

.like-btn.liked {
  border-color: #e53e3e;
  color: #e53e3e;
  background: rgba(229, 62, 62, 0.08);
}

.add-to-goal-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 12px 24px;
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
  color: white;
  border: none;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: 0 4px 15px rgba(16, 185, 129, 0.3);
}

.add-to-goal-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(16, 185, 129, 0.4);
}

.goal-selector-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.goal-selector-modal {
  background: white;
  border-radius: 20px;
  width: 90%;
  max-width: 500px;
  max-height: 80vh;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.2);
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
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
  font-size: 20px;
  color: #718096;
  cursor: pointer;
  padding: 4px;
}

.close-btn:hover {
  color: #2d3748;
}

.modal-body {
  padding: 20px;
  max-height: 60vh;
  overflow-y: auto;
}

.no-goals {
  text-align: center;
  padding: 40px 20px;
}

.no-goals-icon {
  font-size: 48px;
  margin-bottom: 12px;
}

.no-goals p {
  font-size: 14px;
  color: #718096;
  margin-bottom: 20px;
}

.create-goal-btn {
  padding: 12px 24px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
}

.create-goal-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 15px rgba(102, 126, 234, 0.4);
}

.goals-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.goal-option {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: #f7fafc;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.goal-option:hover {
  background: #edf2f7;
  transform: translateX(4px);
}

.goal-icon-small {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
}

.goal-option-info {
  flex: 1;
}

.goal-option-title {
  display: block;
  font-size: 15px;
  font-weight: 600;
  color: #2d3748;
}

.goal-option-category {
  display: block;
  font-size: 12px;
  color: #718096;
  margin-top: 4px;
}

.goal-option-progress {
  text-align: right;
}

.goal-option-progress .mini-bar {
  width: 60px;
  height: 6px;
  background: #e2e8f0;
  border-radius: 3px;
  overflow: hidden;
  margin-bottom: 4px;
}

.goal-option-progress .mini-fill {
  height: 100%;
  border-radius: 3px;
  transition: width 0.3s ease;
}

.goal-option-progress span {
  font-size: 12px;
  font-weight: 600;
  color: #4a5568;
}

.add-new-goal {
  margin-top: 12px;
  text-align: center;
}

.add-new-goal .create-goal-btn {
  width: 100%;
}
</style>
