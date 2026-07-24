<script setup>import { ref, onMounted } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { gsap } from 'gsap';
import { knowledgeApi, goalApi } from '../api/index';
const router = useRouter();
const route = useRoute();
const isSearching = ref(false);
const searchKeyword = ref('');
const searchResults = ref([]);
const selectedResources = ref([]);
const newGoal = ref({
 title: '',
 icon: '🎯',
 category: '编程开发'
});
const isSearched = ref(false);
onMounted(() => {
 const goalData = route.query;
 if (goalData.title) {
 newGoal.value.title = goalData.title;
 }
 if (goalData.icon) {
 newGoal.value.icon = goalData.icon;
 }
 if (goalData.category) {
 newGoal.value.category = goalData.category;
 }
 animatePage();
});
const animatePage = () => {
 gsap.fromTo('.generator-header', { opacity: 0, y: -20 }, { opacity: 1, y: 0, duration: 0.6 });
 gsap.fromTo('.input-section', { opacity: 0, x: -20 }, { opacity: 1, x: 0, duration: 0.4, delay: 0.2 });
};
const searchKnowledgeBase = async () => {
 if (!searchKeyword.value.trim())
 return;
 isSearching.value = true;
 searchResults.value = [];
 try {
 const response = await knowledgeApi.searchKnowledge(searchKeyword.value, null, 20);
 if (response.data && response.data.data && Array.isArray(response.data.data)) {
 searchResults.value = response.data.data.map((resource, index) => ({
 id: Date.now() + index,
 title: resource.title || '',
 type: resource.type || '文章',
 duration: resource.duration || '',
 difficulty: resource.difficulty || '基础',
 category: resource.category || '',
 summary: resource.summary || '',
 selected: false
 }));
 }
 } catch (error) {
 console.error('搜索知识库失败:', error);
 searchResults.value = [];
 }
 isSearching.value = false;
 isSearched.value = true;
 gsap.fromTo('.result-section', { opacity: 0, y: 20 }, { opacity: 1, y: 0, duration: 0.5 });
};
const toggleResource = (resource) => {
 resource.selected = !resource.selected;
 if (resource.selected) {
 selectedResources.value.push(resource);
 }
 else {
 selectedResources.value = selectedResources.value.filter(r => r.id !== resource.id);
 }
};
const selectAllResources = () => {
 searchResults.value.forEach(r => {
 r.selected = true;
 });
 selectedResources.value = [...searchResults.value];
};
const deselectAllResources = () => {
 searchResults.value.forEach(r => {
 r.selected = false;
 });
 selectedResources.value = [];
};
const confirmAndCreateGoal = async () => {
 if (selectedResources.value.length === 0) {
 alert('请至少选择一个学习资源');
 return;
 }
 const colors = [
 'from-green-500 to-emerald-500',
 'from-purple-500 to-indigo-500',
 'from-blue-500 to-cyan-500',
 'from-orange-500 to-red-500',
 'from-pink-500 to-rose-500',
 'from-teal-500 to-cyan-500'
 ];
 const user = localStorage.getItem('user');
 const userId = user ? JSON.parse(user).id : null;
 if (!userId) {
 alert('请先登录');
 router.push('/login');
 return;
 }
 const goalData = {
 title: newGoal.value.title,
 icon: newGoal.value.icon,
 category: newGoal.value.category,
 color: colors[Math.floor(Math.random() * colors.length)],
 progress: 0,
 resources: JSON.stringify(selectedResources.value),
 currentResourceIndex: 0,
 completedResources: JSON.stringify([])
 };
 try {
 await goalApi.createGoal(userId, goalData);
 } catch (err) {
 console.error('Failed to create goal:', err);
 }
 router.push('/learning');
};
const goBack = () => {
 router.push('/learning');
};
</script>

<template>
  <div class="generator-page">
    <div class="page-header">
      <button class="back-btn" @click="goBack">
        <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
          <path d="M15 10H5M5 10l5-5M5 10l5 5" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
        </svg>
        返回
      </button>
      <h1>知识库搜索</h1>
      <div class="goal-badge">
        <span>{{ newGoal.icon }}</span>
        <span>{{ newGoal.title }}</span>
      </div>
    </div>
    
    <div class="generator-container">
      <div class="input-section">
        <div class="section-title">
          <h2>🔍 知识库搜索</h2>
          <p>输入关键词，从知识库中查找相关学习资料</p>
        </div>
        
        <div class="input-wrapper">
          <input 
            v-model="searchKeyword"
            class="prompt-input"
            type="text"
            placeholder="例如：Python、机器学习、英语..."
            @keyup.enter="searchKnowledgeBase"
          />
        </div>
        
        <button 
          :disabled="isSearching || !searchKeyword.trim()" 
          class="generate-btn"
          @click="searchKnowledgeBase"
        >
          <span v-if="isSearching" class="loading-spinner"></span>
          <span v-else>🔍 搜索知识库</span>
        </button>
      </div>
      
      <div v-if="isSearched" class="result-section">
        <div class="resources-section">
          <div class="resources-header">
            <h3>📚 搜索结果</h3>
            <div class="select-all-btn">
              <button @click="selectAllResources">全选</button>
              <button @click="deselectAllResources">取消全选</button>
            </div>
          </div>
          
          <div v-if="searchResults.length === 0" class="no-results">
            <div class="no-results-icon">🔍</div>
            <p>未找到相关学习资料，请尝试其他关键词</p>
          </div>
          
          <div v-else class="resources-list">
            <div 
              v-for="resource in searchResults" 
              :key="resource.id"
              :class="['resource-card', { selected: resource.selected }]"
              @click="toggleResource(resource)"
            >
              <div class="resource-icon">
                {{ resource.type === '视频' ? '🎬' : resource.type === '文章' ? '📝' : resource.type === '练习' ? '✍️' : resource.type === '测验' ? '📋' : '📚' }}
              </div>
              <div class="resource-info">
                <h4>{{ resource.title }}</h4>
                <p v-if="resource.summary" class="resource-summary">{{ resource.summary }}</p>
                <div class="resource-meta">
                  <span class="type-badge">{{ resource.type }}</span>
                  <span class="duration">{{ resource.duration }}</span>
                  <span :class="['difficulty', resource.difficulty]">{{ resource.difficulty }}</span>
                </div>
              </div>
              <div class="select-indicator">
                <svg v-if="resource.selected" width="20" height="20" viewBox="0 0 20 20" fill="none">
                  <circle cx="10" cy="10" r="9" fill="#667eea"/>
                  <path d="M6 10l4 4 8-8" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                </svg>
                <svg v-else width="20" height="20" viewBox="0 0 20 20" fill="none">
                  <circle cx="10" cy="10" r="9" stroke="#e2e8f0" stroke-width="2"/>
                </svg>
              </div>
            </div>
          </div>
          
          <div class="action-bar">
            <span class="selected-count">已选择 {{ selectedResources.length }} 个资源</span>
            <button 
              :disabled="selectedResources.length === 0"
              class="confirm-btn"
              @click="confirmAndCreateGoal"
            >
              ✅ 确认创建学习目标
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.generator-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #f5f7fa 0%, #e4e8ec 100%);
  padding: 20px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  max-width: 800px;
  margin: 0 auto 32px;
}

.back-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  background: white;
  border: none;
  border-radius: 20px;
  color: #4a5568;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.back-btn:hover {
  background: #f7fafc;
  color: #667eea;
}

.page-header h1 {
  font-size: 24px;
  font-weight: 700;
  color: #2d3748;
}

.goal-badge {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.1) 0%, rgba(118, 75, 162, 0.1) 100%);
  border-radius: 20px;
  font-size: 14px;
  font-weight: 500;
  color: #667eea;
}

.goal-badge span:first-child {
  font-size: 18px;
}

.generator-container {
  max-width: 800px;
  margin: 0 auto;
}

.input-section {
  background: white;
  border-radius: 20px;
  padding: 32px;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.08);
  margin-bottom: 24px;
}

.section-title h2 {
  font-size: 20px;
  font-weight: 600;
  color: #2d3748;
  margin-bottom: 8px;
}

.section-title p {
  font-size: 14px;
  color: #718096;
  margin-bottom: 20px;
}

.prompt-input {
  width: 100%;
  padding: 16px;
  border: 2px solid #e2e8f0;
  border-radius: 12px;
  font-size: 15px;
  font-family: inherit;
  resize: none;
  transition: all 0.3s ease;
}

.prompt-input:focus {
  outline: none;
  border-color: #667eea;
}

.prompt-input::placeholder {
  color: #a0aec0;
}

.generate-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 16px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 12px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  margin-top: 16px;
}

.generate-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(102, 126, 234, 0.4);
}

.generate-btn:disabled {
  opacity: 0.7;
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

.result-section {
  background: white;
  border-radius: 20px;
  padding: 32px;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.08);
}

.ai-response {
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.05) 0%, rgba(118, 75, 162, 0.05) 100%);
  border-radius: 16px;
  padding: 20px;
  margin-bottom: 24px;
}

.ai-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.ai-icon {
  font-size: 24px;
}

.ai-label {
  font-size: 14px;
  font-weight: 600;
  color: #667eea;
}

.ai-text {
  font-size: 15px;
  color: #4a5568;
  line-height: 1.7;
  white-space: pre-wrap;
}

.resources-section {
  margin-top: 24px;
}

.resources-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.resources-header h3 {
  font-size: 18px;
  font-weight: 600;
  color: #2d3748;
}

.select-all-btn {
  display: flex;
  gap: 12px;
}

.select-all-btn button {
  padding: 8px 16px;
  background: #f7fafc;
  border: none;
  border-radius: 8px;
  color: #4a5568;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.select-all-btn button:hover {
  background: #edf2f7;
  color: #667eea;
}

.resources-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.resource-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  background: #f7fafc;
  border: 2px solid transparent;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.resource-card:hover {
  background: #edf2f7;
}

.resource-card.selected {
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.1) 0%, rgba(118, 75, 162, 0.1) 100%);
  border-color: #667eea;
}

.resource-icon {
  width: 48px;
  height: 48px;
  background: white;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  flex-shrink: 0;
}

.resource-info {
  flex: 1;
}

.resource-info h4 {
  font-size: 16px;
  font-weight: 600;
  color: #2d3748;
  margin-bottom: 8px;
}

.resource-meta {
  display: flex;
  gap: 12px;
}

.type-badge {
  padding: 4px 10px;
  background: #e2e8f0;
  border-radius: 12px;
  font-size: 12px;
  color: #4a5568;
}

.duration {
  font-size: 13px;
  color: #718096;
}

.difficulty {
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 12px;
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

.select-indicator {
  flex-shrink: 0;
}

.action-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 20px;
  border-top: 1px solid #e2e8f0;
  margin-top: 20px;
}

.selected-count {
  font-size: 14px;
  color: #718096;
}

.confirm-btn {
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

.confirm-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(72, 187, 120, 0.4);
}

.confirm-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.no-results {
  text-align: center;
  padding: 40px;
  background: #f7fafc;
  border-radius: 12px;
}

.no-results-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.no-results p {
  font-size: 15px;
  color: #718096;
}

.resource-summary {
  font-size: 13px;
  color: #718096;
  margin-bottom: 8px;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

@media (max-width: 768px) {
  .page-header {
    flex-wrap: wrap;
    gap: 12px;
  }
  
  .page-header h1 {
    font-size: 20px;
  }
  
  .input-section, .result-section {
    padding: 20px;
  }
  
  .resource-card {
    flex-wrap: wrap;
  }
  
  .resource-info {
    flex: 1;
    min-width: 200px;
  }
}
</style>