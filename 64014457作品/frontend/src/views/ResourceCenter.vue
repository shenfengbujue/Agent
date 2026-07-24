<script setup>
import { ref, onMounted, computed } from 'vue';
import { gsap } from 'gsap';
import { useRouter } from 'vue-router';
import { knowledgeApi } from '../api/index';
import MarkdownRenderer from '../components/common/MarkdownRenderer.vue';

const router = useRouter();

const resources = ref([]);
const featuredResources = ref([]);
const selectedCategory = ref('all');
const searchQuery = ref('');
const loading = ref(true);
const collapsedCategories = ref(new Set()); // 记录用户手动折叠的分类
const showDropdown = ref(false);
const currentResource = ref(null);

const categoryMapping = {
  'python': '编程开发', 'ai': '编程开发', 'frontend': '编程开发', 'backend': '编程开发',
  'network': '网络与安全', 'security': '网络与安全',
  'data': '数据科学', 'math': '数据科学',
  'english': '英语学习', 'science': '自然科学',
  'business': '商业金融', 'management': '商业金融', 'finance': '商业金融',
  'law': '法律', 'health': '健康生活', 'other': '其他',
  'AI生成': 'AI生成内容', 'AI习题': 'AI生成习题',
  '编程开发': '编程开发', '网络与安全': '网络与安全', '数据科学': '数据科学',
  '英语学习': '英语学习', '自然科学': '自然科学', '商业金融': '商业金融',
  '法律': '法律', '人文社科': '人文社科', '健康生活': '健康生活', '其他': '其他'
};

const categoryIcons = {
  '编程开发': '💻', '网络与安全': '🔒', '数据科学': '📊',
  '英语学习': '🌍', '自然科学': '🔬', '商业金融': '💰',
  '法律': '⚖️', '人文社科': '📖', '健康生活': '❤️',
  '其他': '📚', 'AI生成': '🤖', 'AI习题': '✍️',
  'AI生成内容': '🤖', 'AI生成习题': '✍️',
  'python': '💻', 'ai': '🤖', 'english': '🌍', 'math': '📊'
};

// 截取前150字作为卡片摘要，并去掉Markdown标记
const getSummary = (content) => {
  if (!content) return '';
  // 去掉markdown标题和格式化标记，只留纯文本
  const plain = content
    .replace(/#{1,6}\s/g, '')
    .replace(/\*\*/g, '')
    .replace(/`/g, '')
    .replace(/\n+/g, ' ')
    .replace(/---/g, '')
    .substring(0, 150);
  return plain + (content.length > 150 ? '...' : '');
};

const fetchData = async () => {
  loading.value = true;
  try {
    const res = await knowledgeApi.searchKnowledge('', null, 2000);
    if (res.data && res.data.data && Array.isArray(res.data.data)) {
      const mapped = res.data.data.map((r, index) => ({
        id: r.id || index,
        title: r.title || '',
        summary: r.content || r.summary || '',
        category: r.category || 'other',
        difficulty: r.difficulty || '基础',
        duration: r.duration || '',
        type: r.type || '文章',
        entryType: r.entryType || '',
        views: typeof r.viewCount === 'number' ? r.viewCount : Math.floor(Math.random() * 500),
        likes: typeof r.likeCount === 'number' ? r.likeCount : Math.floor(Math.random() * 100),
        liked: !!r.liked
      }));
      
      resources.value = mapped;
      
      featuredResources.value = [...resources.value]
        .sort((a, b) => (b.views + b.likes * 10) - (a.views + a.likes * 10))
        .slice(0, 3);
    }
  } catch (err) {
    console.error('Failed to fetch resources:', err);
    resources.value = [];
    featuredResources.value = [];
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
      if (featuredResources.value.some(r => r.id === resource.id)) {
        const idx = featuredResources.value.findIndex(r => r.id === resource.id);
        if (idx >= 0) {
          featuredResources.value[idx] = { ...featuredResources.value[idx], liked: resource.liked, likes: resource.likes };
        }
      }
    }
  } catch (e) {
    console.error('点赞失败:', e);
    alert('点赞失败，请重试');
  }
};

// 从实际数据中动态提取分类列表
const dynamicCategories = computed(() => {
  const catSet = new Map();
  resources.value.forEach(r => {
    const cat = r.category || '未分类';
    if (!catSet.has(cat)) {
      catSet.set(cat, { id: cat, name: getCategoryName(cat), icon: getResourceIcon(cat), backendId: cat, count: 0 });
    }
    catSet.get(cat).count++;
  });
  return [{ id: 'all', name: '全部', icon: '📋', backendId: 'all', count: resources.value.length }, ...catSet.values()];
});

const filteredResources = () => {
  let result = resources.value;
  if (selectedCategory.value !== 'all') {
    result = result.filter(r => r.category === selectedCategory.value);
  }
  return result;
};

const handleSearch = () => {
  if (!searchQuery.value.trim()) return;
  router.push({
    name: 'SearchResults',
    query: { q: searchQuery.value.trim() }
  });
};

const groupedResources = computed(() => {
  const filtered = filteredResources();
  const groups = {};
  filtered.forEach(resource => {
    if (!groups[resource.category]) {
      groups[resource.category] = [];
    }
    groups[resource.category].push(resource);
  });
  return groups;
});

const allResourcesCount = computed(() => {
  const counts = {};
  resources.value.forEach(resource => {
    counts[resource.category] = (counts[resource.category] || 0) + 1;
  });
  return counts;
});

const toggleCategory = (category) => {
  if (collapsedCategories.value.has(category)) {
    collapsedCategories.value.delete(category);  // 展开
  } else {
    collapsedCategories.value.add(category);     // 折叠
  }
};

const isCategoryExpanded = (category) => {
  return !collapsedCategories.value.has(category);  // 默认展开
};

onMounted(async () => {
  await fetchData();
  animatePage();
});

const animatePage = () => {
  gsap.fromTo('.page-header',
    { opacity: 0, y: -20 },
    { opacity: 1, y: 0, duration: 0.6 }
  );
  
  gsap.fromTo('.category-btn',
    { opacity: 0, scale: 0.9 },
    { opacity: 1, scale: 1, duration: 0.4, stagger: 0.05, delay: 0.2 }
  );
};

const getResourceIcon = (category) => {
  if (category === 'all') return '📋';
  return categoryIcons[category] || '📚';
};

const getCategoryName = (category) => {
  if (category === 'all') return '全部';
  if (!category) return '未分类';
  // DB已改为中文，直接返回；映射表做兜底
  return categoryMapping[category] || category;
};

const viewResource = (resource) => {
  // AI方案 → PlanDetail（可交互做题+图谱）
  if (resource.entryType === 'LEARNING_PLAN') {
    router.push({
      name: 'PlanDetail',
      query: { id: resource.id, title: resource.title }
    });
  } else {
    router.push({
      name: 'KnowledgeResource',
      query: {
        resourceId: resource.id,
        resourceTitle: resource.title,
        resourceCategory: resource.category
      }
    });
  }
};

const deleteResource = async (resource) => {
  if (!confirm(`确定删除「${resource.title}」？`)) return;
  try {
    await knowledgeApi.deleteEntry(resource.id);
    resources.value = resources.value.filter(r => r.id !== resource.id);
  } catch(e) { alert('删除失败: ' + (e.response?.data?.message || e.message)); }
};
</script>

<template>
  <div class="resource-page">
    <div class="page-header">
      <div class="header-content">
        <h1>资源中心</h1>
        <p>探索丰富的学习资源，开启您的学习之旅</p>
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

    <div class="categories-section">
      <div class="category-dropdown">
        <button class="dropdown-btn" @click="showDropdown = !showDropdown">
          <span class="dropdown-icon">{{ getResourceIcon(selectedCategory) }}</span>
          <span>{{ getCategoryName(selectedCategory) }}</span>
          <span class="dropdown-arrow">{{ showDropdown ? '▲' : '▼' }}</span>
        </button>
        <div v-show="showDropdown" class="dropdown-menu">
          <button
            v-for="cat in dynamicCategories"
            :key="cat.id"
            :class="['dropdown-item', { active: selectedCategory === cat.id }]"
            @click="selectedCategory = cat.id; showDropdown = false"
          >
            <span>{{ cat.icon }}</span>
            <span>{{ getCategoryName(cat.id) }}</span>
            <span class="cat-count">{{ cat.count }}</span>
          </button>
        </div>
      </div>
    </div>

    <div v-if="featuredResources.length > 0" class="featured-section">
      <div class="section-title">
        <h2>精选推荐</h2>
        <span class="badge">精选</span>
      </div>
      <div class="featured-grid">
        <div 
          v-for="resource in featuredResources" 
          :key="resource.id" 
          class="featured-card"
          @click="viewResource(resource)"
        >
          <div class="featured-badge">⭐</div>
          <div class="featured-icon">{{ getResourceIcon(resource.category) }}</div>
          <div class="featured-info">
            <h3>{{ resource.title }}</h3>
            <p><MarkdownRenderer :content="getSummary(resource.summary)" /></p>
            <div class="featured-meta">
              <span>{{ getCategoryName(resource.category) }}</span>
              <span>👁️ {{ resource.views }}</span>
              <span
                :class="['like-btn', { liked: resource.liked }]"
                @click.stop="handleToggleLike(resource, $event)"
              >
                {{ resource.liked ? '❤️' : '🤍' }} {{ resource.likes }}
              </span>
              <span class="featured-total">📊 {{ resource.views + resource.likes }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="resources-section">
      <div class="section-title">
        <h2>全部资源</h2>
      </div>
      
      <div v-if="loading" class="loading">
        <div class="loader"></div>
        <p>加载中...</p>
      </div>
      
      <div v-else-if="filteredResources().length === 0" class="empty-state">
        <div class="empty-icon">🔍</div>
        <p>未找到相关资源</p>
        <p class="hint" v-if="searchQuery">已在知识库中检索全部内容，未找到与「{{ searchQuery }}」相关的资源</p>
        <p class="hint" v-else>尝试切换其他分类或搜索关键词</p>
        <button class="empty-btn" @click="searchQuery = ''; fetchData(); selectedCategory = 'all'">查看全部资源</button>
      </div>
      
      <div v-else class="resources-container">
        <div 
          v-for="(categoryResources, category) in groupedResources" 
          :key="category" 
          class="category-section"
        >
          <div 
            class="category-header" 
            @click="toggleCategory(category)"
          >
            <span class="category-icon-large">{{ getResourceIcon(category) }}</span>
            <div class="category-info">
              <span class="category-name">{{ getCategoryName(category) }}</span>
            </div>
            <span class="category-arrow">{{ isCategoryExpanded(category) ? '▼' : '▶' }}</span>
          </div>
          
          <div 
            v-show="isCategoryExpanded(category)" 
            class="category-content"
          >
            <div class="resources-grid">
                <div 
                  v-for="resource in categoryResources" 
                  :key="resource.id" 
                  class="resource-card"
                  @click="viewResource(resource)"
                >
                  <div class="resource-type-tag" :class="resource.type">
                    <span v-if="resource.type === '文章'">📄 文章</span>
                    <span v-else-if="resource.type === '视频'">🎬 视频</span>
                    <span v-else-if="resource.type === '练习'">📝 练习</span>
                    <span v-else-if="resource.type === '测验'">✅ 测验</span>
                    <span v-else>📚 {{ resource.type }}</span>
                  </div>
                  <div class="resource-header">
                    <span class="resource-icon">{{ getResourceIcon(resource.category) }}</span>
                    <span :class="['category-tag', resource.category]">{{ getCategoryName(resource.category) }}</span>
                  </div>
                  <h3>{{ resource.title }}</h3>
                  <p><MarkdownRenderer :content="getSummary(resource.summary)" /></p>
                  <div class="resource-stats">
                    <span>👁️ {{ resource.views }}</span>
                    <span
                      :class="['like-btn', { liked: resource.liked }]"
                      @click.stop="handleToggleLike(resource, $event)"
                    >
                      {{ resource.liked ? '❤️' : '🤍' }} {{ resource.likes }}
                    </span>
                  </div>
                  <button class="resource-btn" @click.stop="viewResource(resource)">查看详情</button>
                  <button v-if="resource.entryType === 'LEARNING_PLAN'" class="card-delete-btn" @click.stop="deleteResource(resource)" title="删除此方案">🗑️ 删除</button>
                </div>
              </div>
          </div>
        </div>
      </div>
    </div>
  </div>

</template>

<style scoped>
.resource-page {
  max-width: 1400px;
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

.categories-section {
  margin-bottom: 32px;
}

.category-dropdown {
  position: relative;
  width: 200px;
}

.dropdown-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  background: white;
  border: 2px solid #e2e8f0;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s ease;
  font-size: 14px;
  color: #4a5568;
}

.dropdown-btn:hover {
  border-color: #667eea;
  color: #667eea;
}

.dropdown-icon {
  font-size: 18px;
}

.dropdown-arrow {
  font-size: 12px;
  color: #a0aec0;
  transition: transform 0.3s ease;
}

.dropdown-menu {
  position: absolute;
  top: calc(100% + 8px);
  left: 0;
  width: 100%;
  background: white;
  border: 2px solid #e2e8f0;
  border-radius: 12px;
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.1);
  z-index: 100;
  max-height: 300px;
  overflow-y: auto;
}

.dropdown-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  background: none;
  border: none;
  cursor: pointer;
  transition: all 0.2s ease;
  font-size: 14px;
  color: #4a5568;
  text-align: left;
}

.dropdown-item:hover {
  background: #f7fafc;
  color: #667eea;
}

.dropdown-item.active {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.featured-section {
  margin-bottom: 40px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}

.section-title h2 {
  font-size: 24px;
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

.count {
  color: #a0aec0;
  font-size: 14px;
}

.featured-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(400px, 1fr));
  gap: 20px;
}

.featured-card {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 16px;
  padding: 28px;
  display: flex;
  gap: 20px;
  position: relative;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.3s ease;
}

.featured-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 30px rgba(102, 126, 234, 0.4);
}

.featured-badge {
  position: absolute;
  top: 16px;
  right: 16px;
  font-size: 24px;
}

.featured-icon {
  width: 70px;
  height: 70px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
}

.featured-info {
  flex: 1;
  color: white;
}

.featured-info h3 {
  font-size: 20px;
  font-weight: 700;
  margin-bottom: 8px;
}

.featured-info p {
  opacity: 0.9;
  margin-bottom: 12px;
  font-size: 14px;
}

.featured-meta {
  display: flex;
  gap: 16px;
  font-size: 13px;
  opacity: 0.8;
}

.featured-total {
  background: rgba(255, 255, 255, 0.2);
  padding: 2px 8px;
  border-radius: 10px;
}

.resources-section {
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
  margin-bottom: 24px;
}

.empty-btn {
  padding: 12px 28px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 10px;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
}

.empty-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4);
}

.resources-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 20px;
}

.resource-card {
  background: white;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.05);
  transition: all 0.3s ease;
  position: relative;
}

.resource-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.1);
}

.resource-type-tag {
  position: absolute;
  top: 16px;
  right: 16px;
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 600;
  z-index: 1;
}

.resource-type-tag.文章 {
  background: linear-gradient(135deg, #dbeafe 0%, #bfdbfe 100%);
  color: #1d4ed8;
}

.resource-type-tag.视频 {
  background: linear-gradient(135deg, #fce7f3 0%, #fbcfe8 100%);
  color: #be185d;
}

.resource-type-tag.练习 {
  background: linear-gradient(135deg, #dcfce7 0%, #bbf7d0 100%);
  color: #15803d;
}

.resource-type-tag.测验 {
  background: linear-gradient(135deg, #fef3c7 0%, #fde68a 100%);
  color: #b45309;
}

.resource-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
}

.resource-icon {
  font-size: 24px;
}

.category-tag {
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 600;
  background: #f7fafc;
  color: #4a5568;
}

.resource-card h3 {
  font-size: 18px;
  font-weight: 600;
  color: #2d3748;
  margin-bottom: 10px;
}

.resource-card p {
  color: #718096;
  font-size: 14px;
  line-height: 1.5;
  margin-bottom: 16px;
}

.resource-stats {
  display: flex;
  gap: 16px;
  font-size: 13px;
  color: #a0aec0;
  margin-bottom: 16px;
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

.resource-btn {
  width: 100%;
  padding: 12px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
}

.resource-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4);
}
.card-delete-btn {
  padding: 8px 14px;
  background: transparent;
  color: #a0aec0;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  cursor: pointer;
  font-size: 13px;
  transition: all 0.2s;
}
.card-delete-btn:hover {
  background: #fff5f5;
  color: #e53e3e;
  border-color: #fc8181;
}

.category-section {
  margin-bottom: 20px;
  background: white;
  border-radius: 16px;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.05);
  overflow: hidden;
}

.category-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 20px 24px;
  cursor: pointer;
  transition: background 0.3s ease;
}

.category-header:hover {
  background: #f7fafc;
}

.category-icon-large {
  font-size: 28px;
}

.category-info {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
}

.category-name {
  font-size: 18px;
  font-weight: 700;
  color: #2d3748;
}

.category-count {
  font-size: 14px;
  color: #a0aec0;
  padding: 4px 12px;
  background: #f7fafc;
  border-radius: 20px;
}

.category-hint {
  font-size: 12px;
  color: #667eea;
  margin-right: 8px;
}

.category-arrow {
  font-size: 14px;
  color: #a0aec0;
  transition: transform 0.3s ease;
}

.category-content {
  padding: 0 24px 24px;
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(-10px); }
  to { opacity: 1; transform: translateY(0); }
}

.resources-container {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
</style>