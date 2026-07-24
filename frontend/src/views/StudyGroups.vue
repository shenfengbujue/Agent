<script setup>
import { ref, onMounted, computed } from 'vue';
import { useRouter } from 'vue-router';
import { gsap } from 'gsap';
import { groupApi } from '../api/index';

const router = useRouter();

const searchQuery = ref('');
const selectedCategory = ref('all');
const showCreateModal = ref(false);
const myGroups = ref([]);
const allGroups = ref([]);
const currentUser = ref(null);

const newGroup = ref({
  name: '',
  description: '',
  category: '',
  maxMembers: 20,
  isPublic: true
});

const categories = [
  { id: 'all', label: '全部', icon: '🌐' },
  { id: 'programming', label: '编程开发', icon: '💻' },
  { id: 'math', label: '数学', icon: '📐' },
  { id: 'english', label: '英语', icon: '🌍' },
  { id: 'ai', label: '人工智能', icon: '🤖' },
  { id: 'design', label: '设计', icon: '🎨' },
  { id: 'business', label: '商业', icon: '💼' }
];

const categoryLabelMap = {
  programming: '编程开发',
  math: '数学',
  english: '英语',
  ai: '人工智能',
  design: '设计',
  business: '商业'
};

const mockGroups = [
  {
    id: 1,
    name: 'Python 学习小组',
    description: '一起学习Python编程，从入门到进阶，互相讨论解决编程难题。',
    category: 'programming',
    icon: '🐍',
    memberCount: 5,
    maxMembers: 50,
    currentMembers: 5,
    isPublic: true,
    createdAt: '2024-12-01',
    score: 92,
    topics: ['基础语法', '数据分析', '爬虫', 'Web开发'],
    recentActivity: '2分钟前有人发言',
    owner: { name: 'Python大师', avatar: '🐍' }
  },
  {
    id: 2,
    name: '机器学习研习社',
    description: '深度学习、神经网络、自然语言处理等AI前沿技术研讨。',
    category: 'ai',
    icon: '🧠',
    memberCount: 4,
    maxMembers: 40,
    currentMembers: 4,
    isPublic: true,
    createdAt: '2024-11-15',
    score: 88,
    topics: ['深度学习', 'NLP', '计算机视觉', '强化学习'],
    recentActivity: '5分钟前有人发言',
    owner: { name: 'AI探索者', avatar: '🤖' }
  },
  {
    id: 3,
    name: '高等数学互助组',
    description: '微积分、线性代数、概率论等高等数学知识共享与答疑。',
    category: 'math',
    icon: '📐',
    memberCount: 3,
    maxMembers: 30,
    currentMembers: 3,
    isPublic: true,
    createdAt: '2024-12-10',
    score: 78,
    topics: ['微积分', '线性代数', '概率论', '离散数学'],
    recentActivity: '10分钟前有人发言',
    owner: { name: '数学达人', avatar: '🔢' }
  },
  {
    id: 4,
    name: '英语口语角',
    description: '每日英语口语练习，托福雅思备考交流，英语学习资源分享。',
    category: 'english',
    icon: '🗣️',
    memberCount: 3,
    maxMembers: 60,
    currentMembers: 3,
    isPublic: true,
    createdAt: '2024-10-20',
    score: 85,
    topics: ['口语练习', '托福', '雅思', '商务英语'],
    recentActivity: '1分钟前有人发言',
    owner: { name: '英语达人', avatar: '🌍' }
  },
  {
    id: 5,
    name: '前端开发社区',
    description: 'Vue、React、Angular等前端框架学习，CSS技巧交流。',
    category: 'programming',
    icon: '⚛️',
    memberCount: 3,
    maxMembers: 45,
    currentMembers: 3,
    isPublic: true,
    createdAt: '2024-11-28',
    score: 80,
    topics: ['Vue', 'React', 'CSS', 'TypeScript'],
    recentActivity: '3分钟前有人发言',
    owner: { name: '前端架构师', avatar: '🎨' }
  },
  {
    id: 6,
    name: 'UI/UX 设计交流',
    description: '用户界面和用户体验设计交流，Figma、Sketch等工具使用心得。',
    category: 'design',
    icon: '🎨',
    memberCount: 3,
    maxMembers: 25,
    currentMembers: 3,
    isPublic: true,
    createdAt: '2024-12-05',
    score: 72,
    topics: ['UI设计', 'UX研究', 'Figma', '设计系统'],
    recentActivity: '15分钟前有人发言',
    owner: { name: '设计美学', avatar: '🖌️' }
  },
  {
    id: 7,
    name: '算法与数据结构',
    description: 'LeetCode刷题小组，面试算法准备，编程竞赛讨论。',
    category: 'programming',
    icon: '🧩',
    memberCount: 3,
    maxMembers: 50,
    currentMembers: 3,
    isPublic: true,
    createdAt: '2024-09-15',
    score: 95,
    topics: ['LeetCode', '面试', '竞赛', '图论'],
    recentActivity: '刚刚有人发言',
    owner: { name: '算法狂魔', avatar: '⚡' }
  },
  {
    id: 8,
    name: '商业案例分析',
    description: 'MBA经典案例分析，商业模式探讨，创业经验交流。',
    category: 'business',
    icon: '📊',
    memberCount: 2,
    maxMembers: 30,
    currentMembers: 2,
    isPublic: true,
    createdAt: '2024-12-08',
    score: 65,
    topics: ['案例分析', '商业模式', '创业', '市场营销'],
    recentActivity: '30分钟前有人发言',
    owner: { name: '商业精英', avatar: '💼' }
  }
];

onMounted(async () => {
  const storedUser = localStorage.getItem('user');
  if (storedUser) {
    currentUser.value = JSON.parse(storedUser);
  }
  
  // 从后端加载我的小组和数据
  await loadMyGroups();
  await loadAllGroups();
  animatePage();
});

const loadMyGroups = async () => {
  try {
    const res = await groupApi.getMyGroups();
    if (res.data && res.data.code === 200 && res.data.data) {
      myGroups.value = res.data.data.map(g => ({
        id: Number(g.id),
        name: g.groupName || g.name,
        icon: getCategoryIcon(g.course || g.category),
        category: g.course || g.category || 'other',
        joinedAt: g.joinedAt || g.createdAt
      }));
    }
  } catch (e) {
    console.error('加载我的小组失败:', e);
  }
};

const loadAllGroups = async () => {
  try {
    const res = await groupApi.getAllGroups();
    if (res.data && res.data.code === 200 && res.data.data) {
      const dbGroups = res.data.data.map(g => ({
        id: g.id,
        name: g.groupName || g.name,
        description: g.description || '暂无描述',
        category: (g.course || g.category || 'other').toLowerCase(),
        icon: getCategoryIcon(g.course || g.category),
        memberCount: g.memberCount || 1,
        maxMembers: g.maxMembers || 50,
        currentMembers: g.memberCount || 1,
        isPublic: true,
        createdAt: g.createdAt || new Date().toISOString(),
        topics: g.course ? [g.course] : [],
        owner: { name: g.creatorId || '系统', avatar: '👤' },
        creatorId: g.creatorId,
        score: ((g.memberCount || 0) * 5) + ((g.maxMembers || 50) / 5) + (g.postCount || 0) * 2
      }));
      allGroups.value = dbGroups;
    } else {
      allGroups.value = [];
    }
  } catch (e) {
    console.error('加载小组列表失败:', e);
    allGroups.value = [];
  }
};

const getCategoryIcon = (course) => {
  if (!course) return '📚';
  const c = course.toLowerCase();
  if (c.includes('python') || c.includes('编程') || c.includes('算法')) return '💻';
  if (c.includes('数学') || c.includes('高数')) return '📐';
  if (c.includes('英语')) return '🌍';
  if (c.includes('ai') || c.includes('人工')) return '🤖';
  return '📚';
};

const filteredGroups = computed(() => {
  let groups = [...allGroups.value];

  if (selectedCategory.value !== 'all') {
    groups = groups.filter(g => g.category === selectedCategory.value);
  }

  if (searchQuery.value.trim()) {
    const query = searchQuery.value.trim().toLowerCase();
    groups = groups.filter(g =>
      g.name.toLowerCase().includes(query) ||
      g.description.toLowerCase().includes(query) ||
      (g.topics && g.topics.some(t => t.toLowerCase().includes(query)))
    );
  }

  // 按综合评分降序排序
  groups.sort((a, b) => (b.score || 0) - (a.score || 0));
  
  return groups;
});

const isJoined = (group) => {
  if (isOwner(group)) return true;
  return myGroups.value.some(g => String(g.id) === String(group.id));
};

const handleJoinGroup = async (group) => {
  if (isJoined(group.id)) return;
  try {
    await groupApi.joinGroup(group.id);
    const groupEntry = {
      id: group.id,
      name: group.name,
      icon: group.icon,
      category: group.category,
      joinedAt: new Date().toISOString()
    };
    myGroups.value.push(groupEntry);
    const idx = allGroups.value.findIndex(g => g.id === group.id);
    if (idx !== -1) {
      allGroups.value[idx].currentMembers += 1;
    }
  } catch (e) {
    console.error('加入小组失败:', e);
  }
};

const handleLeaveGroup = async (group) => {
  try {
    await groupApi.leaveGroup(group.id);
    myGroups.value = myGroups.value.filter(g => g.id !== group.id);
    const idx = allGroups.value.findIndex(g => g.id === group.id);
    if (idx !== -1 && allGroups.value[idx].currentMembers > 0) {
      allGroups.value[idx].currentMembers -= 1;
    }
  } catch (e) {
    console.error('离开小组失败:', e);
  }
};

const handleDeleteGroup = async (group) => {
  if (!confirm(`确定要删除小组「${group.name}」吗？此操作不可恢复。`)) return;
  try {
    await groupApi.deleteGroup(group.id);
    allGroups.value = allGroups.value.filter(g => g.id !== group.id);
    myGroups.value = myGroups.value.filter(g => g.id !== group.id);
  } catch (e) {
    console.error('删除小组失败:', e);
  }
};

const isOwner = (group) => {
  if (!currentUser.value) return false;
  const userId = String(currentUser.value.id);
  const groupCreatorId = String(group.creatorId || group.owner?.name || '');
  return userId === groupCreatorId;
};

const goToGroup = (groupId) => {
  router.push(`/group/${groupId}`);
};

const handleCreateGroup = async () => {
  if (!newGroup.value.name.trim()) {
    alert('请输入小组名称');
    return;
  }
  if (!newGroup.value.category) {
    alert('请选择小组分类');
    return;
  }

  try {
    const res = await groupApi.createGroup({
      groupName: newGroup.value.name.trim(),
      description: newGroup.value.description.trim() || '新创建的学习小组',
      course: newGroup.value.category,
      maxMembers: newGroup.value.maxMembers
    });
    
    if (res.data && res.data.code === 200 && res.data.data) {
      const g = res.data.data;
      const group = {
        id: g.id,
        name: g.groupName || newGroup.value.name.trim(),
        description: g.description || newGroup.value.description.trim(),
        category: newGroup.value.category,
        icon: categories.find(c => c.id === newGroup.value.category)?.icon || '📚',
        memberCount: 1,
        maxMembers: newGroup.value.maxMembers,
        currentMembers: 1,
        isPublic: newGroup.value.isPublic,
        createdAt: new Date().toISOString(),
        topics: [newGroup.value.category],
        creatorId: g.creatorId || currentUser.value?.id,
        owner: { name: g.creatorId || currentUser.value?.id || '我', avatar: currentUser.value?.avatar || '😊' }
      };
      allGroups.value.unshift(group);
      myGroups.value.push({ id: group.id, name: group.name, icon: group.icon, category: group.category, joinedAt: new Date().toISOString() });
      
      showCreateModal.value = false;
      newGroup.value = { name: '', description: '', category: '', maxMembers: 20, isPublic: true };
      
      alert('小组创建成功！');
    } else if (res.data && res.data.message) {
      alert(res.data.message);
    } else {
      alert('创建失败');
    }
  } catch (e) {
    console.error('创建小组失败:', e);
    if (e.response && e.response.data && e.response.data.message) {
      alert(e.response.data.message);
    } else {
      alert('创建失败：' + (e.message || '未知错误'));
    }
  }
};

const animatePage = () => {
  gsap.fromTo('.page-header',
    { opacity: 0, y: -20 },
    { opacity: 1, y: 0, duration: 0.6, ease: 'power3.out' }
  );
  gsap.fromTo('.category-item',
    { opacity: 0, y: 10 },
    { opacity: 1, y: 0, duration: 0.3, stagger: 0.05, delay: 0.2 }
  );
  gsap.fromTo('.group-card',
    { opacity: 0, y: 20 },
    { opacity: 1, y: 0, duration: 0.4, stagger: 0.1, delay: 0.4 }
  );
};
</script>

<template>
  <div class="study-groups-page">
    <div class="page-container">
      <div class="page-header">
        <div class="header-content">
          <h1>👨‍👩‍👧‍👦 学习小组</h1>
          <p>找到志同道合的学习伙伴，加入小组共同进步</p>
        </div>
        <button class="create-btn" @click="showCreateModal = true">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
            <path d="M12 5v14M5 12h14" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"/>
          </svg>
          <span>创建小组</span>
        </button>
      </div>

      <div v-if="myGroups.length > 0" class="my-groups-section">
        <h2 class="section-title">📌 我的小组</h2>
        <div class="my-groups-grid">
          <div
            v-for="group in myGroups"
            :key="group.id"
            class="my-group-card"
            @click="goToGroup(group.id)"
          >
            <span class="my-group-icon">{{ group.icon }}</span>
            <span class="my-group-name">{{ group.name }}</span>
            <div class="my-group-arrow">
              <svg width="16" height="16" viewBox="0 0 20 20" fill="none">
                <path d="M7 4l6 6-6 6" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
              </svg>
            </div>
          </div>
        </div>
      </div>

      <div class="search-section">
        <div class="search-box">
          <svg class="search-icon" width="20" height="20" viewBox="0 0 24 24" fill="none">
            <circle cx="10.5" cy="10.5" r="6.5" stroke="#a0aec0" stroke-width="2"/>
            <path d="M15.5 15.5L21 21" stroke="#a0aec0" stroke-width="2" stroke-linecap="round"/>
          </svg>
          <input
            v-model="searchQuery"
            type="text"
            placeholder="搜索小组名称、描述或话题..."
            class="search-input"
          />
        </div>
      </div>

      <div class="categories-section">
        <button
          v-for="cat in categories"
          :key="cat.id"
          :class="['category-item', { active: selectedCategory === cat.id }]"
          @click="selectedCategory = cat.id"
        >
          <span class="cat-icon">{{ cat.icon }}</span>
          <span class="cat-label">{{ cat.label }}</span>
        </button>
      </div>

      <div class="groups-section">
        <div class="section-header">
          <h2 class="section-title">
            {{ selectedCategory === 'all' ? '🔍 发现小组' : `🔍 ${categoryLabelMap[selectedCategory] || ''}小组` }}
          </h2>
          <span class="group-count">共 {{ filteredGroups.length }} 个小组</span>
        </div>

        <div v-if="filteredGroups.length === 0" class="empty-state">
          <span class="empty-icon">🔍</span>
          <p>没有找到匹配的小组</p>
          <button class="reset-btn" @click="searchQuery = ''; selectedCategory = 'all'">清除筛选</button>
        </div>

        <div v-else class="groups-grid">
          <div
            v-for="group in filteredGroups"
            :key="group.id"
            class="group-card"
            @click="goToGroup(group.id)"
          >
            <div class="card-header">
              <div class="group-icon-wrapper">
                <span class="group-icon">{{ group.icon }}</span>
              </div>
              <div class="group-meta">
                <div class="member-badge">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
                    <circle cx="9" cy="8" r="3" stroke="#667eea" stroke-width="2"/>
                    <path d="M3 20v-1a4 4 0 014-4h4a4 4 0 014 4v1" stroke="#667eea" stroke-width="2" stroke-linecap="round"/>
                    <circle cx="18" cy="8" r="2" stroke="#667eea" stroke-width="2"/>
                    <path d="M15 16v-1a3 3 0 011.5-2.6M21 16v-1a3 3 0 00-1-2.2" stroke="#667eea" stroke-width="2" stroke-linecap="round"/>
                  </svg>
                  <span>{{ group.currentMembers }}/{{ group.maxMembers }}</span>
                </div>
                <span v-if="isJoined(group)" class="joined-tag">已加入</span>
              </div>
            </div>

            <div class="card-body">
              <h3 class="group-name">
                {{ group.name }}
                <span :class="['rank-badge', 'rank-' + Math.min(filteredGroups.indexOf(group) + 1, 3)]">
                  {{ filteredGroups.indexOf(group) + 1 }}
                </span>
                <span v-if="isOwner(group)" class="owner-badge">👑 我创建的</span>
              </h3>
              <p class="group-desc">{{ group.description }}</p>
            </div>

            <div class="topics-row">
              <span v-for="topic in group.topics.slice(0, 3)" :key="topic" class="topic-tag">{{ topic }}</span>
              <span v-if="group.topics.length > 3" class="topic-more">+{{ group.topics.length - 3 }}</span>
            </div>

            <div class="card-footer">
              <div class="action-buttons">
                <button
                  v-if="isOwner(group)"
                  class="delete-btn"
                  @click.stop="handleDeleteGroup(group)"
                >
                  删除
                </button>
                <button
                  v-if="isJoined(group) && !isOwner(group)"
                  class="leave-btn"
                  @click.stop="handleLeaveGroup(group)"
                >
                  退出
                </button>
                <button
                  v-if="!isJoined(group)"
                  class="join-btn"
                  @click.stop="handleJoinGroup(group)"
                  :disabled="group.currentMembers >= group.maxMembers"
                >
                  {{ group.currentMembers >= group.maxMembers ? '已满' : '加入' }}
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="showCreateModal" class="modal-overlay" @click.self="showCreateModal = false">
      <div class="modal-content">
        <div class="modal-header">
          <h2>✨ 创建学习小组</h2>
          <button class="modal-close" @click="showCreateModal = false">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
              <path d="M6 18L18 6M6 6l12 12" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
            </svg>
          </button>
        </div>

        <div class="modal-body">
          <div class="form-group">
            <label>小组名称 <span class="required">*</span></label>
            <input
              v-model="newGroup.name"
              type="text"
              placeholder="给你的小组取个名字"
              class="form-input"
              maxlength="30"
            />
          </div>

          <div class="form-group">
            <label>小组描述</label>
            <textarea
              v-model="newGroup.description"
              placeholder="描述一下小组的学习目标和方向..."
              class="form-textarea"
              rows="3"
              maxlength="200"
            ></textarea>
          </div>

          <div class="form-group">
            <label>选择分类 <span class="required">*</span></label>
            <div class="category-select">
              <button
                v-for="cat in categories.filter(c => c.id !== 'all')"
                :key="cat.id"
                :class="['cat-option', { selected: newGroup.category === cat.id }]"
                @click="newGroup.category = cat.id"
              >
                <span>{{ cat.icon }}</span>
                <span>{{ cat.label }}</span>
              </button>
            </div>
          </div>

          <div class="form-group">
            <label>最大成员数</label>
            <div class="member-slider">
              <input
                v-model.number="newGroup.maxMembers"
                type="range"
                min="5"
                max="100"
                step="5"
                class="slider"
              />
              <span class="slider-value">{{ newGroup.maxMembers }} 人</span>
            </div>
          </div>

          <div class="form-group">
            <label class="checkbox-label">
              <input v-model="newGroup.isPublic" type="checkbox" class="form-checkbox" />
              <span>公开小组（所有人都可以搜索和加入）</span>
            </label>
          </div>
        </div>

        <div class="modal-footer">
          <button class="cancel-btn" @click="showCreateModal = false">取消</button>
          <button
            class="submit-btn"
            @click="handleCreateGroup"
            :disabled="!newGroup.name.trim() || !newGroup.category"
          >
            创建小组
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.study-groups-page {
  min-height: calc(100vh - 70px - 60px);
}

.page-container {
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
  font-size: 15px;
}

.create-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 28px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 14px;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  white-space: nowrap;
}

.create-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(102, 126, 234, 0.4);
}

/* My Groups */
.my-groups-section {
  margin-bottom: 32px;
}

.section-title {
  font-size: 20px;
  font-weight: 700;
  color: #2d3748;
  margin-bottom: 16px;
}

.my-groups-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 12px;
}

.my-group-card {
  background: white;
  border-radius: 14px;
  padding: 16px 20px;
  display: flex;
  align-items: center;
  gap: 12px;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.05);
  cursor: pointer;
  transition: all 0.3s ease;
  border: 2px solid transparent;
}

.my-group-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(102, 126, 234, 0.15);
  border-color: #667eea;
}

.my-group-icon {
  font-size: 24px;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.1) 0%, rgba(118, 75, 162, 0.1) 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.my-group-name {
  flex: 1;
  font-size: 14px;
  font-weight: 600;
  color: #2d3748;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.my-group-arrow {
  color: #a0aec0;
  flex-shrink: 0;
}

/* Search */
.search-section {
  margin-bottom: 24px;
}

.search-box {
  position: relative;
  max-width: 500px;
}

.search-icon {
  position: absolute;
  left: 16px;
  top: 50%;
  transform: translateY(-50%);
}

.search-input {
  width: 100%;
  padding: 14px 16px 14px 48px;
  border: 2px solid #e2e8f0;
  border-radius: 14px;
  font-size: 15px;
  color: #2d3748;
  background: white;
  transition: all 0.3s ease;
  outline: none;
}

.search-input:focus {
  border-color: #667eea;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
}

.search-input::placeholder {
  color: #a0aec0;
}

/* Categories */
.categories-section {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 32px;
}

.category-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 18px;
  border: 2px solid #e2e8f0;
  border-radius: 25px;
  background: white;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  color: #4a5568;
  transition: all 0.3s ease;
}

.category-item:hover {
  border-color: #667eea;
  color: #667eea;
}

.category-item.active {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border-color: transparent;
}

.cat-icon {
  font-size: 16px;
}

/* Groups Grid */
.groups-section {
  margin-bottom: 40px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.group-count {
  color: #a0aec0;
  font-size: 14px;
}

.empty-state {
  text-align: center;
  padding: 60px 20px;
}

.empty-icon {
  font-size: 48px;
  display: block;
  margin-bottom: 16px;
}

.empty-state p {
  color: #a0aec0;
  font-size: 16px;
  margin-bottom: 20px;
}

.reset-btn {
  padding: 10px 24px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 20px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.3s;
}

.reset-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4);
}

.groups-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 20px;
}

.group-card {
  background: white;
  border-radius: 18px;
  padding: 24px;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.06);
  cursor: pointer;
  transition: all 0.3s ease;
  border: 2px solid transparent;
  display: flex;
  flex-direction: column;
}

.group-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 30px rgba(102, 126, 234, 0.2);
  border-color: #667eea;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.group-icon-wrapper {
  width: 48px;
  height: 48px;
  border-radius: 14px;
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.1) 0%, rgba(118, 75, 162, 0.1) 100%);
  display: flex;
  align-items: center;
  justify-content: center;
}

.group-icon {
  font-size: 26px;
}

.group-meta {
  display: flex;
  align-items: center;
  gap: 10px;
}

.member-badge {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  background: #f7fafc;
  border-radius: 20px;
  font-size: 12px;
  color: #667eea;
  font-weight: 500;
}

.joined-tag {
  font-size: 12px;
  padding: 4px 12px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border-radius: 20px;
  font-weight: 500;
}

.card-body {
  flex: 1;
  margin-bottom: 16px;
}

.group-name {
  font-size: 18px;
  font-weight: 700;
  color: #2d3748;
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.rank-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 800;
  min-width: 26px;
  height: 26px;
  padding: 0 7px;
  border-radius: 13px;
  background: #edf2f7;
  color: #a0aec0;
  white-space: nowrap;
  flex-shrink: 0;
}

.rank-badge.rank-1 {
  background: linear-gradient(135deg, #ffd700 0%, #ffb800 100%);
  color: #7c5e00;
  box-shadow: 0 2px 12px rgba(255, 184, 0, 0.5);
  font-size: 15px;
  min-width: 30px;
  height: 30px;
  border-radius: 15px;
}

.rank-badge.rank-2 {
  background: linear-gradient(135deg, #c0c0c0 0%, #a8a8a8 100%);
  color: #444;
  box-shadow: 0 2px 10px rgba(168, 168, 168, 0.4);
  font-size: 14px;
  min-width: 28px;
  height: 28px;
  border-radius: 14px;
}

.rank-badge.rank-3 {
  background: linear-gradient(135deg, #cd7f32 0%, #b87333 100%);
  color: #fff;
  box-shadow: 0 2px 10px rgba(184, 115, 51, 0.5);
  font-size: 14px;
  min-width: 28px;
  height: 28px;
  border-radius: 14px;
}

.owner-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 10px;
  background: linear-gradient(135deg, #fbbf24 0%, #f59e0b 100%);
  color: #7c5e00;
  margin-left: 8px;
  box-shadow: 0 2px 8px rgba(251, 191, 36, 0.4);
}

.group-desc {
  font-size: 14px;
  color: #718096;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.topics-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 16px;
}

.topic-tag {
  font-size: 12px;
  padding: 4px 10px;
  background: #edf2f7;
  color: #4a5568;
  border-radius: 8px;
  font-weight: 500;
}

.topic-more {
  font-size: 12px;
  padding: 4px 10px;
  background: #edf2f7;
  color: #a0aec0;
  border-radius: 8px;
}

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 16px;
  border-top: 1px solid #edf2f7;
}

.owner-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.owner-avatar {
  font-size: 18px;
}

.owner-name {
  font-size: 13px;
  color: #a0aec0;
}

.action-buttons {
  display: flex;
  gap: 8px;
}

.join-btn {
  padding: 8px 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 20px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
}

.join-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4);
}

.join-btn:disabled {
  background: #cbd5e0;
  cursor: not-allowed;
}

.leave-btn {
  padding: 8px 20px;
  background: white;
  color: #e53e3e;
  border: 2px solid #fed7d7;
  border-radius: 20px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
}

.leave-btn:hover {
  background: #fff5f5;
  border-color: #e53e3e;
}

.delete-btn {
  padding: 8px 20px;
  background: #e53e3e;
  color: white;
  border: none;
  border-radius: 20px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
}

.delete-btn:hover {
  background: #c53030;
  box-shadow: 0 4px 15px rgba(229, 62, 62, 0.4);
}

/* Modal */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 200;
  padding: 20px;
}

.modal-content {
  background: white;
  border-radius: 20px;
  width: 100%;
  max-width: 520px;
  max-height: 90vh;
  overflow-y: auto;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24px 28px;
  border-bottom: 1px solid #e2e8f0;
}

.modal-header h2 {
  font-size: 20px;
  font-weight: 700;
  color: #2d3748;
}

.modal-close {
  background: none;
  border: none;
  color: #a0aec0;
  cursor: pointer;
  padding: 4px;
  border-radius: 8px;
  transition: all 0.2s;
}

.modal-close:hover {
  background: #f7fafc;
  color: #4a5568;
}

.modal-body {
  padding: 24px 28px;
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

.required {
  color: #e53e3e;
}

.form-input,
.form-textarea {
  width: 100%;
  padding: 12px 16px;
  border: 2px solid #e2e8f0;
  border-radius: 12px;
  font-size: 14px;
  color: #2d3748;
  background: #f7fafc;
  outline: none;
  transition: all 0.3s ease;
  box-sizing: border-box;
}

.form-input:focus,
.form-textarea:focus {
  border-color: #667eea;
  background: white;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
}

.form-textarea {
  resize: vertical;
  min-height: 80px;
  font-family: inherit;
}

.category-select {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}

.cat-option {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px;
  border: 2px solid #e2e8f0;
  border-radius: 12px;
  background: white;
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
  color: #4a5568;
  transition: all 0.2s;
}

.cat-option:hover {
  border-color: #667eea;
}

.cat-option.selected {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border-color: transparent;
}

.member-slider {
  display: flex;
  align-items: center;
  gap: 16px;
}

.slider {
  flex: 1;
  -webkit-appearance: none;
  height: 6px;
  border-radius: 3px;
  background: #edf2f7;
  outline: none;
  accent-color: #667eea;
}

.slider-value {
  font-size: 14px;
  font-weight: 600;
  color: #667eea;
  min-width: 50px;
  text-align: right;
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  font-weight: 400 !important;
  color: #4a5568;
}

.form-checkbox {
  width: 18px;
  height: 18px;
  accent-color: #667eea;
  cursor: pointer;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 20px 28px;
  border-top: 1px solid #e2e8f0;
}

.cancel-btn {
  padding: 10px 24px;
  background: white;
  color: #718096;
  border: 2px solid #e2e8f0;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.cancel-btn:hover {
  background: #f7fafc;
}

.submit-btn {
  padding: 10px 28px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s;
}

.submit-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4);
}

.submit-btn:disabled {
  background: #cbd5e0;
  cursor: not-allowed;
}

@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 16px;
  }

  .groups-grid {
    grid-template-columns: 1fr;
  }

  .my-groups-grid {
    grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  }

  .category-select {
    grid-template-columns: repeat(2, 1fr);
  }

  .modal-content {
    margin: 10px;
  }
}
</style>