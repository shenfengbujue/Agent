<script setup>
import { ref, onMounted, computed } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { knowledgeApi } from '../api/index';

const router = useRouter();
const route = useRoute();

const searchQuery = ref('');
const resources = ref([]);
const loading = ref(true);
const highlightedResource = ref(null);

const categoryMapping = {
  'python': '编程开发',
  'ai': '人工智能',
  'math': '数学',
  'english': '英语',
  'business': '商业分析',
  'design': '设计',
  'other': '其他',
  'frontend': '前端开发',
  'backend': '后端开发',
  'mobile': '移动开发',
  'database': '数据库',
  'network': '网络技术',
  'security': '网络安全',
  'data': '数据分析',
  'cloud': '云计算',
  'marketing': '市场营销',
  'management': '管理学',
  'finance': '金融学',
  'psychology': '心理学',
  'literature': '文学',
  'history': '历史',
  'science': '自然科学',
  'art': '艺术',
  'music': '音乐',
  'health': '健康',
  'law': '法律'
};

const categoryIcons = {
  'python': '💻',
  'ai': '🤖',
  'math': '📐',
  'english': '🌍',
  'business': '📊',
  'design': '🎨',
  'other': '📚',
  'frontend': '🎨',
  'backend': '⚙️',
  'mobile': '📱',
  'database': '🗄️',
  'network': '🌐',
  'security': '🔒',
  'data': '📈',
  'cloud': '☁️',
  'marketing': '📣',
  'management': '📋',
  'finance': '💰',
  'psychology': '🧠',
  'literature': '📖',
  'history': '🏛️',
  'science': '🔬',
  'art': '🎭',
  'music': '🎵',
  'health': '❤️',
  'law': '⚖️'
};

const fetchSearchResults = async (query) => {
  loading.value = true;
  try {
    const res = await knowledgeApi.searchKnowledge(query, null, 500);
    if (res.data && res.data.data && Array.isArray(res.data.data)) {
      const lowerQuery = query.toLowerCase();
      const mapped = res.data.data.map((r, index) => {
        const title = r.title || '';
        const content = r.content || r.summary || '';
        let relevanceScore = 0;
        if (lowerQuery) {
          if (title.toLowerCase().includes(lowerQuery)) relevanceScore += 10;
          if (title.toLowerCase().startsWith(lowerQuery)) relevanceScore += 5;
          if (content.toLowerCase().includes(lowerQuery)) relevanceScore += 5;
          const queryWords = lowerQuery.split(/\s+/);
          queryWords.forEach(word => {
            if (title.toLowerCase().includes(word)) relevanceScore += 3;
            if (content.toLowerCase().includes(word)) relevanceScore += 1;
          });
        }
        const views = typeof r.viewCount === 'number' ? r.viewCount : Math.floor(Math.random() * 500);
        const likes = typeof r.likeCount === 'number' ? r.likeCount : Math.floor(Math.random() * 100);
        return {
          id: r.id || index,
          title: title,
          summary: content,
          category: r.category || 'other',
          difficulty: r.difficulty || '基础',
          duration: r.duration || '',
          type: r.type || '文章',
          views: views,
          likes: likes,
          liked: !!r.liked,
          score: relevanceScore * 100 + views + likes * 10
        };
      });
      
      const sorted = [...mapped].sort((a, b) => b.score - a.score);
      resources.value = sorted;
      
      if (sorted.length > 0) {
        highlightedResource.value = sorted[0];
      }
    }
  } catch (err) {
    console.error('搜索失败:', err);
    resources.value = [];
    highlightedResource.value = null;
  } finally {
    loading.value = false;
  }
};

const handleToggleLike = async (resource, event) => {
  if (event) event.stopPropagation();
  if (!resource || !resource.id) return;
  try {
    const res = await knowledgeApi.toggleLike(resource.id);
    if (res.data && res.data.code === 200 && res.data.data) {
      resource.liked = res.data.data.liked;
      resource.likes = res.data.data.likeCount;
    }
  } catch (e) {
    console.error('点赞失败:', e);
  }
};

const viewResource = (resource) => {
  router.push({
    name: 'KnowledgeResource',
    query: {
      resourceId: resource.id,
      resourceTitle: resource.title,
      resourceCategory: resource.category
    }
  });
};

const getResourceIcon = (category) => {
  return categoryIcons[category] || '📚';
};

const getCategoryName = (category) => {
  return categoryMapping[category] || '其他';
};

onMounted(() => {
  const query = route.query.q || '';
  searchQuery.value = query;
  if (query) {
    fetchSearchResults(query);
  }
});
</script>

<template>
  <div class="search-results-page">
    <div class="page-header">
      <div class="header-content">
        <button class="back-btn" @click="router.back()">
          <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
            <path d="M10 13l-5-5 5-5" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          返回
        </button>
        <h1>搜索结果</h1>
      </div>
      <div class="search-box">
        <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
          <path d="M12.9 14.3a8 8 0 1 1 1.4-1.4l5.3 5.3-1.4 1.4-5.3-5.3zM8 14A6 6 0 1 0 8 2a6 6 0 0 0 0 12z" stroke="currentColor" stroke-width="2"/>
        </svg>
        <input 
          v-model="searchQuery" 
          type="text" 
          placeholder="搜索学习资源..."
          @keyup.enter="handleSearch"
        />
        <button class="search-btn" @click="handleSearch">
          <svg width="18" height="18" viewBox="0 0 20 20" fill="none">
            <path d="M12.9 14.3a8 8 0 1 1 1.4-1.4l5.3 5.3-1.4 1.4-5.3-5.3zM8 14A6 6 0 1 0 8 2a6 6 0 0 0 0 12z" stroke="currentColor" stroke-width="2"/>
          </svg>
        </button>
      </div>
    </div>

    <div class="search-info">
      <span v-if="searchQuery">搜索关键词：「{{ searchQuery }}」</span>
      <span v-if="resources.length > 0">共找到 {{ resources.length }} 条结果</span>
    </div>

    <div v-if="highlightedResource && resources.length > 0" class="highlight-section">
      <div class="section-title">
        <h2>🔥 强烈推荐</h2>
        <span class="badge">热门精选</span>
      </div>
      <div 
        class="highlight-card"
        @click="viewResource(highlightedResource)"
      >
        <div class="highlight-icon">{{ getResourceIcon(highlightedResource.category) }}</div>
        <div class="highlight-info">
          <div class="highlight-badges">
            <span class="category-tag" :class="highlightedResource.category">{{ getCategoryName(highlightedResource.category) }}</span>
            <span class="difficulty-tag">{{ highlightedResource.difficulty }}</span>
            <span class="type-tag">{{ highlightedResource.type }}</span>
          </div>
          <h3>{{ highlightedResource.title }}</h3>
          <p>{{ highlightedResource.summary }}</p>
          <div class="highlight-meta">
            <span>👁️ {{ highlightedResource.views }}</span>
            <span :class="['like-btn', { liked: highlightedResource.liked }]" @click.stop="handleToggleLike(highlightedResource, $event)">
              {{ highlightedResource.liked ? '❤️' : '🤍' }} {{ highlightedResource.likes }}
            </span>
            <span class="duration">{{ highlightedResource.duration }}</span>
          </div>
        </div>
        <div class="highlight-arrow">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
            <path d="M9 6l6 6-6 6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </div>
      </div>
    </div>

    <div class="results-section">
      <div v-if="loading" class="loading">
        <div class="loader"></div>
        <p>搜索中...</p>
      </div>
      
      <div v-else-if="resources.length === 0" class="empty-state">
        <div class="empty-icon">🔍</div>
        <p>未找到相关资源</p>
        <p class="hint">尝试使用其他关键词搜索</p>
      </div>
      
      <div v-else class="results-grid">
        <div 
          v-for="resource in resources" 
          :key="resource.id" 
          :class="['result-card', { highlighted: highlightedResource && highlightedResource.id === resource.id }]"
          @click="viewResource(resource)"
        >
          <div class="card-type-tag" :class="resource.type">
            <span v-if="resource.type === '文章'">📄 文章</span>
            <span v-else-if="resource.type === '视频'">🎬 视频</span>
            <span v-else-if="resource.type === '练习'">📝 练习</span>
            <span v-else-if="resource.type === '测验'">✅ 测验</span>
            <span v-else>📚 {{ resource.type }}</span>
          </div>
          <div class="card-header">
            <span class="resource-icon">{{ getResourceIcon(resource.category) }}</span>
            <span :class="['category-tag', resource.category]">{{ getCategoryName(resource.category) }}</span>
            <span v-if="highlightedResource && highlightedResource.id === resource.id" class="recommend-badge">🔥 推荐</span>
          </div>
          <h3>{{ resource.title }}</h3>
          <p>{{ resource.summary }}</p>
          <div class="card-meta">
            <span>{{ resource.difficulty }}</span>
            <span>{{ resource.duration }}</span>
          </div>
          <div class="card-stats">
            <span>👁️ {{ resource.views }}</span>
            <span :class="['like-btn', { liked: resource.liked }]" @click.stop="handleToggleLike(resource, $event)">
              {{ resource.liked ? '❤️' : '🤍' }} {{ resource.likes }}
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.search-results-page {
  max-width: 1400px;
  margin: 0 auto;
  padding: 40px 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.header-content {
  display: flex;
  align-items: center;
  gap: 16px;
}

.back-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: white;
  border: 2px solid #e2e8f0;
  border-radius: 10px;
  cursor: pointer;
  color: #4a5568;
  font-size: 14px;
  transition: all 0.3s ease;
}

.back-btn:hover {
  border-color: #667eea;
  color: #667eea;
}

.header-content h1 {
  font-size: 28px;
  font-weight: 700;
  color: #2d3748;
}

.search-box {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #f7fafc;
  border-radius: 12px;
  padding: 12px 16px;
  width: 380px;
  color: #a0aec0;
}

.search-box input {
  flex: 1;
  background: none;
  border: none;
  font-size: 15px;
  color: #2d3748;
}

.search-btn {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  border-radius: 8px;
  padding: 8px 12px;
  color: white;
  cursor: pointer;
  transition: all 0.3s ease;
}

.search-btn:hover {
  transform: scale(1.05);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.search-info {
  display: flex;
  gap: 20px;
  margin-bottom: 32px;
  font-size: 14px;
  color: #718096;
}

.highlight-section {
  margin-bottom: 40px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}

.section-title h2 {
  font-size: 22px;
  font-weight: 700;
  color: #2d3748;
}

.badge {
  padding: 4px 12px;
  background: linear-gradient(135deg, #f59e0b 0%, #d97706 100%);
  color: white;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 600;
}

.highlight-card {
  background: linear-gradient(135deg, #f59e0b 0%, #d97706 100%);
  border-radius: 20px;
  padding: 32px;
  display: flex;
  gap: 24px;
  position: relative;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.3s ease;
}

.highlight-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 40px rgba(245, 158, 11, 0.4);
}

.highlight-icon {
  width: 80px;
  height: 80px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 36px;
  flex-shrink: 0;
}

.highlight-info {
  flex: 1;
  color: white;
}

.highlight-badges {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
}

.category-tag {
  padding: 4px 12px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 600;
  background: rgba(255, 255, 255, 0.2);
  color: white;
}

.difficulty-tag, .type-tag {
  padding: 4px 12px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 600;
  background: rgba(255, 255, 255, 0.2);
  color: white;
}

.highlight-info h3 {
  font-size: 24px;
  font-weight: 700;
  margin-bottom: 12px;
}

.highlight-info p {
  opacity: 0.9;
  margin-bottom: 16px;
  font-size: 15px;
  line-height: 1.6;
}

.highlight-meta {
  display: flex;
  gap: 20px;
  font-size: 14px;
  opacity: 0.8;
}

.highlight-arrow {
  color: white;
  opacity: 0.6;
  flex-shrink: 0;
}

.results-section {
  margin-bottom: 40px;
}

.loading {
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

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 80px;
  text-align: center;
}

.empty-icon {
  font-size: 64px;
  margin-bottom: 20px;
}

.empty-state p {
  color: #718096;
  font-size: 16px;
  margin-bottom: 8px;
}

.empty-state .hint {
  color: #a0aec0;
  font-size: 14px;
}

.results-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 20px;
}

.result-card {
  background: white;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.05);
  transition: all 0.3s ease;
  position: relative;
  border: 2px solid transparent;
}

.result-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.1);
}

.result-card.highlighted {
  border-color: #f59e0b;
  background: linear-gradient(135deg, #fffbeb 0%, #fff7ed 100%);
}

.card-type-tag {
  position: absolute;
  top: 16px;
  right: 16px;
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 600;
  z-index: 1;
}

.card-type-tag.文章 {
  background: linear-gradient(135deg, #dbeafe 0%, #bfdbfe 100%);
  color: #1d4ed8;
}

.card-type-tag.视频 {
  background: linear-gradient(135deg, #fce7f3 0%, #fbcfe8 100%);
  color: #be185d;
}

.card-type-tag.练习 {
  background: linear-gradient(135deg, #dcfce7 0%, #bbf7d0 100%);
  color: #15803d;
}

.card-type-tag.测验 {
  background: linear-gradient(135deg, #fef3c7 0%, #fde68a 100%);
  color: #b45309;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
}

.resource-icon {
  font-size: 24px;
}

.recommend-badge {
  padding: 2px 8px;
  background: linear-gradient(135deg, #f59e0b 0%, #d97706 100%);
  color: white;
  border-radius: 8px;
  font-size: 11px;
  font-weight: 600;
}

.result-card h3 {
  font-size: 18px;
  font-weight: 600;
  color: #2d3748;
  margin-bottom: 10px;
}

.result-card p {
  color: #718096;
  font-size: 14px;
  line-height: 1.5;
  margin-bottom: 14px;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-meta {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: #a0aec0;
  margin-bottom: 12px;
}

.card-stats {
  display: flex;
  gap: 16px;
  font-size: 13px;
  color: #a0aec0;
}

.like-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  user-select: none;
  padding: 2px 8px;
  border-radius: 10px;
  transition: all 0.2s ease;
}

.like-btn:hover {
  background: rgba(255, 75, 110, 0.1);
  color: #e53e3e;
}

.like-btn.liked {
  color: #e53e3e;
  font-weight: 600;
}

.duration {
  background: rgba(255, 255, 255, 0.2);
  padding: 2px 8px;
  border-radius: 8px;
}
</style>