<script setup>
import { ref, onMounted, computed } from 'vue';
import { useRouter } from 'vue-router';
import { gsap } from 'gsap';
import axios from 'axios';
import { userApi, authApi, profileApi } from '../api/index';

const router = useRouter();
const user = ref(null);
const isEditing = ref(false);
const activeTab = ref('profile');
const showEmailModal = ref(false);
const newEmail = ref('');
const showPasswordModal = ref(false);
const profileKeywords = ref([]);
const profileInterests = ref([]);
const profilePreferences = ref('');
const profileDimensions = ref({});

// 过滤去重关键词：排除碎片和重复
const cleanKeywords = computed(() => {
  const seen = new Set();
  const garbageWords = ['learn','want','level','hour','per','day','beginner','study','need','help','like','know','time','week','month','year'];
  return (profileKeywords.value || [])
    .filter(kw => {
      if (!kw || kw.length < 2) return false;
      if (garbageWords.includes(kw.toLowerCase())) return false;
      // 碎片检测：被其他更长关键词包含的去掉
      const lower = kw.toLowerCase();
      if (seen.has(lower)) return false;
      seen.add(lower);
      return true;
    })
    .filter((kw, i, arr) => {
      // 再扫一遍：如果自己是其他关键词的子串，去掉
      return !arr.some((other, j) => i !== j && other.length > kw.length && other.includes(kw));
    });
});

const cleanInterests = computed(() => {
  const seen = new Set();
  return (profileInterests.value || [])
    .filter(i => i && i.length >= 2)
    .filter(i => {
      const lower = i.toLowerCase();
      if (seen.has(lower)) return false;
      seen.add(lower);
      return true;
    });
});
const passwordForm = ref({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
});

const editForm = ref({
  nickname: '',
  email: '',
  avatar: ''
});

const avatarOptions = ['👤', '😊', '🤓', '😎', '🥳', '🧑‍💻', '👨‍🎓', '👩‍🎓', '🦊', '🐱', '🦁', '🐼'];
const uploadedAvatar = ref(null);
const fileInputRef = ref(null);

const stats = ref([
  { label: '学习天数', value: 0, icon: '📅' },
  { label: '完成课程', value: 0, icon: '✅' },
  { label: '获得经验', value: 0, icon: '⭐' },
  { label: '社区排名', value: '-', icon: '🏆' }
]);

const achievements = ref([
  { id: 1, name: '初学者', icon: '🌱', desc: '完成第一个学习目标', unlocked: false },
  { id: 2, name: '勤奋学习者', icon: '📚', desc: '连续学习7天', unlocked: false },
  { id: 3, name: '知识达人', icon: '💡', desc: '完成10个学习目标', unlocked: false },
  { id: 4, name: '社区之星', icon: '🌟', desc: '进入排行榜前10', unlocked: false },
  { id: 5, name: '分享大师', icon: '🎁', desc: '分享5个学习资源', unlocked: false },
  { id: 6, name: '学霸', icon: '🎓', desc: '完成所有学习目标', unlocked: false }
]);

const tabs = [
  { id: 'profile', label: '个人资料', icon: '👤' },
  { id: 'stats', label: '学习统计', icon: '📊' },
  { id: 'achievements', label: '成就徽章', icon: '🏆' },
  { id: 'settings', label: '账号设置', icon: '⚙️' }
];

onMounted(() => {
  loadUser();
  loadProfileData();
  animatePage();
});

const loadUser = async () => {
  const storedUser = localStorage.getItem('user');
  if (storedUser) {
    user.value = JSON.parse(storedUser);
    
    if (!user.value.email) {
      try {
        const res = await userApi.getUserById(user.value.id);
        if (res.data && res.data.code === 200 && res.data.data) {
          user.value.email = res.data.data.email || '';
          localStorage.setItem('user', JSON.stringify(user.value));
        }
      } catch (e) {
        console.error('获取用户信息失败:', e);
        user.value.email = '';
      }
    }
    
    editForm.value = {
      nickname: user.value.nickname,
      email: user.value.email || '',
      avatar: user.value.avatar
    };
  }
};

const loadProfileData = async () => {
  const storedUser = localStorage.getItem('user');
  if (!storedUser) return;
  const u = JSON.parse(storedUser);
  // 从API获取最新用户数据（含createdAt）
  try {
    const meRes = await axios.get('/api/auth/me', { headers: { Authorization: `Bearer ${localStorage.getItem('token')}` } });
    if (meRes.data?.code === 200 && meRes.data?.data) {
      user.value = { ...user.value, ...meRes.data.data };
      localStorage.setItem('user', JSON.stringify(user.value));
    }
  } catch(e) {}
  try {
    const res = await profileApi.getProfile(u.id);
    if (res.data && res.data.code === 200 && res.data.data) {
      const data = res.data.data;
      // 判断是否为代码式噪音值（纯英文小写+下划线+数字混搭，非正常关键词）
      const isNoise = (s) => {
        if (!s || s.length < 2) return true;
        // 如果包含中文，视为正常内容
        if (/[一-龥]/.test(s)) return false;
        // 纯英文大写开头（如 Python, Java）→ 正常术语
        if (/^[A-Z][a-zA-Z]+$/.test(s)) return false;
        // 纯数字 → 噪音
        if (/^\d+/.test(s)) return true;
        // 含下划线 → 代码ID，噪音
        if (/_/.test(s)) return true;
        // 全小写英文且 ≤12 字符 → 很可能是代码片断
        if (/^[a-z]+$/.test(s) && s.length <= 12) return true;
        // 数字+英文混搭 → 噪音（如 3months, 15min）
        if (/\d/.test(s) && /[a-zA-Z]/.test(s)) return true;
        return false;
      };
      const rawNoise = new Set(['career_skill','exam升学','hobby_self','15min','30min-1h','1h+','weekend',
        'audio','text','practice','video','never','tech_bottleneck','communication','AI生成','text',
        'explore','share','alone','hint']);
      profileKeywords.value = (data.keywords || []).filter(k => !rawNoise.has(k) && !isNoise(k));
      profileInterests.value = (data.interests || []).filter(i => !rawNoise.has(i) && !isNoise(i));
      // 补充加载AI对话分析的真实维度数据
      try {
        const dimRes = await axios.get(`/api/profile/${u.id}/dimensions`, { headers: { Authorization: `Bearer ${localStorage.getItem('token')}` } });
        if (dimRes.data?.code === 200 && dimRes.data?.data) {
          const dims = dimRes.data.data;
          Object.values(dims).forEach(dim => {
            if (dim.dimension_value && dim.dimension_value !== '未评估' && dim.dimension_value !== '未提及') {
              // 维度值也经过噪音过滤
              if (dim.dimension_key === 'errorPatterns') {
                const pts = dim.dimension_value.split(/[,，、]/).filter(p => p && !rawNoise.has(p) && !isNoise(p));
                profileKeywords.value = [...new Set([...profileKeywords.value, ...pts])];
              }
              if (dim.dimension_key === 'motivation' || dim.dimension_key === 'knowledgeLevel') {
                const v = dim.dimension_value;
                if (!isNoise(v) && !profileInterests.value.includes(v)) {
                  profileInterests.value.push(v);
                }
              }
              // 存储维度到响应式数据供模板使用
              profileDimensions.value[dim.dimension_key] = dim.dimension_value;
            }
          });
        }
      } catch(e) {}
      profilePreferences.value = data.preferences || '';
    }
  } catch (e) {
    console.error('加载学习偏好失败:', e);
  }
};

const animatePage = () => {
  gsap.fromTo('.profile-header',
    { opacity: 0, y: -20 },
    { opacity: 1, y: 0, duration: 0.6 }
  );
  
  gsap.fromTo('.tab-btn',
    { opacity: 0, scale: 0.9 },
    { opacity: 1, scale: 1, duration: 0.3, stagger: 0.1, delay: 0.2 }
  );
};

const startEditing = () => {
  isEditing.value = true;
};

const cancelEditing = () => {
  isEditing.value = false;
  editForm.value = {
    nickname: user.value.nickname,
    email: user.value.email || '',
    avatar: user.value.avatar
  };
};

const saveProfile = () => {
  user.value.nickname = editForm.value.nickname;
  user.value.email = editForm.value.email;
  if (uploadedAvatar.value) {
    user.value.avatarUrl = uploadedAvatar.value;
    user.value.avatar = '👤';
  } else {
    user.value.avatar = editForm.value.avatar;
    user.value.avatarUrl = null;
  }
  localStorage.setItem('user', JSON.stringify(user.value));
  isEditing.value = false;
  window.dispatchEvent(new CustomEvent('userUpdated'));
};

const openEmailModal = () => {
  newEmail.value = user.value.email || '';
  showEmailModal.value = true;
};

const saveEmail = () => {
  if (!newEmail.value.trim()) {
    alert('请输入邮箱地址');
    return;
  }
  if (!/\S+@\S+\.\S+/.test(newEmail.value)) {
    alert('邮箱格式不正确');
    return;
  }
  user.value.email = newEmail.value.trim();
  localStorage.setItem('user', JSON.stringify(user.value));
  editForm.value.email = user.value.email;
  showEmailModal.value = false;
  window.dispatchEvent(new CustomEvent('userUpdated'));
};

const openPasswordModal = () => {
  passwordForm.value = {
    oldPassword: '',
    newPassword: '',
    confirmPassword: ''
  };
  showPasswordModal.value = true;
};

const savePassword = async () => {
  const { oldPassword, newPassword, confirmPassword } = passwordForm.value;
  
  if (!oldPassword.trim()) {
    alert('请输入旧密码');
    return;
  }
  
  if (!newPassword.trim()) {
    alert('请输入新密码');
    return;
  }
  
  if (newPassword.length < 6) {
    alert('新密码长度至少为6位');
    return;
  }
  
  if (newPassword !== confirmPassword) {
    alert('两次输入的新密码不一致');
    return;
  }
  
  try {
    const response = await authApi.changePassword({
      oldPassword: oldPassword.trim(),
      newPassword: newPassword.trim(),
      confirmPassword: confirmPassword.trim()
    });
    
    if (response.data.code === 200) {
      alert('密码修改成功！');
      showPasswordModal.value = false;
    } else {
      alert(response.data.message || '修改密码失败');
    }
  } catch (err) {
    console.error('修改密码失败:', err);
    if (err.response && err.response.data && err.response.data.message) {
      alert(err.response.data.message);
    } else {
      alert('修改密码失败，请检查网络连接');
    }
  }
};

const selectAvatar = (avatar) => {
  uploadedAvatar.value = null;
  editForm.value.avatar = avatar;
};

const triggerFileInput = () => {
  fileInputRef.value?.click();
};

const handleFileUpload = (event) => {
  const file = event.target.files?.[0];
  if (file) {
    if (!file.type.startsWith('image/')) {
      alert('请选择图片文件');
      return;
    }
    
    const reader = new FileReader();
    reader.onload = (e) => {
      uploadedAvatar.value = e.target?.result;
      editForm.value.avatar = 'uploaded';
    };
    reader.readAsDataURL(file);
  }
};

const logout = () => {
  localStorage.removeItem('isLoggedIn');
  localStorage.removeItem('user');
  localStorage.removeItem('profileCompleted');
  router.push('/');
};

const goBack = () => {
  router.push('/home');
};
</script>

<template>
  <div class="profile-page">
    <div class="profile-header">
      <button class="back-btn" @click="goBack">
        <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
          <path d="M15 10H5M5 10l5-5M5 10l5 5" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
        </svg>
        返回主页
      </button>
      <h1>个人中心</h1>
    </div>

    <div class="user-card">
      <div class="user-avatar-section">
        <div v-if="isEditing" class="avatar-selector">
          <div class="current-avatar" @click="triggerFileInput">
            <img v-if="uploadedAvatar" :src="uploadedAvatar" alt="上传的头像" class="uploaded-img" />
            <span v-else>{{ editForm.avatar }}</span>
            <div class="upload-overlay">
              <span>📷</span>
              <span>点击上传</span>
            </div>
          </div>
          <input ref="fileInputRef" type="file" accept="image/*" class="file-input" @change="handleFileUpload" />
          <div class="avatar-grid">
            <button 
              v-for="avatar in avatarOptions" 
              :key="avatar"
              :class="['avatar-option', { selected: editForm.avatar === avatar && !uploadedAvatar }]"
              @click="selectAvatar(avatar)"
            >
              {{ avatar }}
            </button>
          </div>
          <button class="upload-btn" @click="triggerFileInput">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
              <path d="M12 4v16M4 12h16" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
            </svg>
            本地上传头像
          </button>
        </div>
        <div v-else class="avatar-display">
          <div class="avatar-circle">
            <img v-if="user?.avatarUrl" :src="user.avatarUrl" alt="用户头像" class="avatar-img" />
            <span v-else>{{ user?.avatar }}</span>
          </div>
          <div class="level-badge">Lv.{{ user?.level }}</div>
        </div>
      </div>
      
      <div class="user-info-section">
        <div v-if="isEditing" class="edit-form">
          <div class="form-group">
            <label>昵称</label>
            <input v-model="editForm.nickname" type="text" class="form-input" />
          </div>
          <div class="form-group">
            <label>邮箱</label>
            <input v-model="editForm.email" type="email" class="form-input" />
          </div>
          <div class="edit-actions">
            <button class="cancel-btn" @click="cancelEditing">取消</button>
            <button class="save-btn" @click="saveProfile">保存</button>
          </div>
        </div>
        <div v-else class="info-display">
          <h2>{{ user?.nickname }}</h2>
          <p class="username">@{{ user?.username }}</p>
          <p v-if="user?.email" class="email">{{ user?.email }}</p>
          <button class="edit-btn" @click="startEditing">
            <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
              <path d="M2 11.5V14h2.5l7-7-2.5-2.5-7 7zM11.5 4.5l-2-2 1.5-1.5 2 2-1.5 1.5z" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
            </svg>
            编辑资料
          </button>
        </div>
      </div>
      
      <div class="quick-stats">
        <div v-for="stat in stats.slice(0, 4)" :key="stat.label" class="quick-stat">
          <span class="stat-icon">{{ stat.icon }}</span>
          <span class="stat-value">{{ stat.value }}</span>
          <span class="stat-label">{{ stat.label }}</span>
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

    <div class="tab-content">
      <div v-show="activeTab === 'profile'" class="content-section">
        <div class="section-card">
          <h3>基本信息</h3>
          <div class="info-grid">
            <div class="info-item">
              <span class="info-label">用户ID</span>
              <span class="info-value">{{ user?.id }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">注册时间</span>
              <span class="info-value">{{ user?.createdAt ? new Date(user.createdAt).toLocaleDateString() : '暂无' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">最后登录</span>
              <span class="info-value">今天</span>
            </div>
            <div class="info-item">
              <span class="info-label">账号状态</span>
              <span class="info-value status-active">正常</span>
            </div>
          </div>
        </div>
        
        <div class="section-card">
          <h3>学习偏好</h3>
          <div class="preferences">
            <div class="preference-item">
              <span class="pref-label">学习关键词</span>
              <div class="pref-tags">
                <template v-if="cleanKeywords.length > 0">
                  <span v-for="(kw, i) in cleanKeywords" :key="i" class="tag">{{ kw }}</span>
                </template>
                <span v-else class="tag empty-tag">未设置</span>
              </div>
            </div>
            <div class="preference-item">
              <span class="pref-label">兴趣领域</span>
              <div class="pref-tags">
                <template v-if="cleanInterests.length > 0">
                  <span v-for="(it, i) in cleanInterests" :key="i" class="tag interest-tag">{{ it }}</span>
                </template>
                <span v-else class="tag empty-tag">未设置</span>
              </div>
            </div>
            <div class="preference-item">
              <span class="pref-label">学习风格</span>
              <span class="pref-value">{{ profilePreferences || '未设置' }}</span>
            </div>
          </div>
        </div>
      </div>

      <div v-show="activeTab === 'stats'" class="content-section">
        <div class="stats-grid">
          <div v-for="stat in stats" :key="stat.label" class="stat-card">
            <div class="stat-icon-circle">{{ stat.icon }}</div>
            <div class="stat-content">
              <span class="stat-number">{{ stat.value }}</span>
              <span class="stat-name">{{ stat.label }}</span>
            </div>
          </div>
        </div>
        
        <div class="section-card">
          <h3>学习趋势</h3>
          <div class="trend-chart">
            <div class="chart-placeholder">
              <p>📊 最近30天学习数据</p>
              <div class="mini-chart" v-if="stats[0]?.value > 0">
                <div v-for="i in 7" :key="i" class="chart-bar" :style="{ height: `${Math.random() * 60 + 20}%` }"></div>
              </div>
              <div v-else class="no-data">
                <span class="no-data-icon">📈</span>
                <p>暂无学习数据</p>
                <p class="no-data-hint">开始学习后，这里会显示你的学习趋势</p>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div v-show="activeTab === 'achievements'" class="content-section">
        <div class="achievements-grid">
          <div 
            v-for="achievement in achievements" 
            :key="achievement.id"
            :class="['achievement-card', { unlocked: achievement.unlocked }]"
          >
            <div class="achievement-icon">{{ achievement.icon }}</div>
            <div class="achievement-info">
              <h4>{{ achievement.name }}</h4>
              <p>{{ achievement.desc }}</p>
            </div>
            <div v-if="achievement.unlocked" class="unlock-badge">已解锁</div>
            <div v-else class="lock-badge">未解锁</div>
          </div>
        </div>
      </div>

      <div v-show="activeTab === 'settings'" class="content-section">
        <div class="section-card">
          <h3>账号安全</h3>
          <div class="setting-item">
            <div class="setting-info">
              <span class="setting-label">修改密码</span>
              <span class="setting-desc">定期更换密码可以保护账号安全</span>
            </div>
            <button class="setting-btn" @click="openPasswordModal">修改</button>
          </div>
          <div class="setting-item">
            <div class="setting-info">
              <span class="setting-label">绑定邮箱</span>
              <span class="setting-desc">{{ user?.email || '未绑定' }}</span>
            </div>
            <button v-if="!user?.email" class="setting-btn" @click="openEmailModal">绑定</button>
            <span v-else class="email-binded">已绑定</span>
          </div>
        </div>
        
        <div class="section-card">
          <h3>通知设置</h3>
          <div class="setting-item">
            <div class="setting-info">
              <span class="setting-label">学习提醒</span>
              <span class="setting-desc">每日学习提醒通知</span>
            </div>
            <label class="toggle">
              <input type="checkbox" checked />
              <span class="toggle-slider"></span>
            </label>
          </div>
          <div class="setting-item">
            <div class="setting-info">
              <span class="setting-label">社区消息</span>
              <span class="setting-desc">接收社区互动通知</span>
            </div>
            <label class="toggle">
              <input type="checkbox" checked />
              <span class="toggle-slider"></span>
            </label>
          </div>
        </div>
        
        <div class="danger-zone">
          <h3>危险操作</h3>
          <button class="logout-btn" @click="logout">
            <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
              <path d="M8 10h8M3 10l3-3M3 10l3 3M8 6V4a2 2 0 0 1 2-2h6a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-6a2 2 0 0 1-2-2v-2" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
            </svg>
            退出登录
          </button>
        </div>
      </div>
    </div>

    <div v-if="showEmailModal" class="modal-overlay" @click.self="showEmailModal = false">
      <div class="modal-content">
        <div class="modal-header">
          <h3>{{ user?.email ? '修改邮箱' : '绑定邮箱' }}</h3>
          <button class="modal-close" @click="showEmailModal = false">×</button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label>邮箱地址</label>
            <input v-model="newEmail" type="email" class="form-input" placeholder="请输入邮箱地址" />
          </div>
        </div>
        <div class="modal-footer">
          <button class="modal-btn cancel" @click="showEmailModal = false">取消</button>
          <button class="modal-btn confirm" @click="saveEmail">确认</button>
        </div>
      </div>
    </div>

    <div v-if="showPasswordModal" class="modal-overlay" @click.self="showPasswordModal = false">
      <div class="modal-content">
        <div class="modal-header">
          <h3>修改密码</h3>
          <button class="modal-close" @click="showPasswordModal = false">×</button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label>旧密码</label>
            <input v-model="passwordForm.oldPassword" type="password" class="form-input" placeholder="请输入旧密码" />
          </div>
          <div class="form-group">
            <label>新密码</label>
            <input v-model="passwordForm.newPassword" type="password" class="form-input" placeholder="请输入新密码（至少6位）" />
          </div>
          <div class="form-group">
            <label>确认新密码</label>
            <input v-model="passwordForm.confirmPassword" type="password" class="form-input" placeholder="请再次输入新密码" />
          </div>
        </div>
        <div class="modal-footer">
          <button class="modal-btn cancel" @click="showPasswordModal = false">取消</button>
          <button class="modal-btn confirm" @click="savePassword">确认</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.profile-page {
  max-width: 1000px;
  margin: 0 auto;
  padding: 40px 20px;
}

.profile-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 32px;
}

.profile-header h1 {
  font-size: 28px;
  font-weight: 700;
  color: #2d3748;
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
}

.back-btn:hover {
  background: #f7fafc;
  color: #667eea;
}

.user-card {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 20px;
  padding: 32px;
  display: flex;
  gap: 32px;
  color: white;
  margin-bottom: 32px;
}

.user-avatar-section {
  flex-shrink: 0;
}

.avatar-display {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.avatar-circle {
  width: 100px;
  height: 100px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 48px;
  border: 4px solid white;
  position: relative;
  overflow: hidden;
}

.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.level-badge {
  padding: 6px 16px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 20px;
  font-size: 14px;
  font-weight: 600;
}

.avatar-selector {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.current-avatar {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 40px;
  border: 3px solid white;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  transition: all 0.3s ease;
}

.current-avatar:hover {
  background: rgba(255, 255, 255, 0.3);
}

.current-avatar:hover .upload-overlay {
  opacity: 1;
}

.uploaded-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.upload-overlay {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.3s ease;
  color: white;
  font-size: 12px;
}

.file-input {
  display: none;
}

.upload-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 10px;
  background: rgba(255, 255, 255, 0.2);
  border: 2px dashed white;
  border-radius: 10px;
  color: white;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.upload-btn:hover {
  background: rgba(255, 255, 255, 0.3);
}

.avatar-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
}

.avatar-option {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.1);
  border: 2px solid transparent;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.avatar-option:hover {
  background: rgba(255, 255, 255, 0.2);
}

.avatar-option.selected {
  border-color: white;
  background: rgba(255, 255, 255, 0.3);
}

.user-info-section {
  flex: 1;
}

.info-display h2 {
  font-size: 24px;
  font-weight: 700;
  margin-bottom: 8px;
}

.username {
  font-size: 14px;
  opacity: 0.8;
  margin-bottom: 4px;
}

.email {
  font-size: 14px;
  opacity: 0.8;
  margin-bottom: 16px;
}

.edit-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  background: rgba(255, 255, 255, 0.2);
  border: none;
  border-radius: 20px;
  color: white;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.edit-btn:hover {
  background: rgba(255, 255, 255, 0.3);
}

.edit-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.form-group label {
  font-size: 14px;
  opacity: 0.9;
}

.form-input {
  padding: 12px 16px;
  background: rgba(255, 255, 255, 0.2);
  border: none;
  border-radius: 10px;
  color: white;
  font-size: 15px;
}

.form-input::placeholder {
  color: rgba(255, 255, 255, 0.6);
}

.edit-actions {
  display: flex;
  gap: 12px;
}

.cancel-btn {
  padding: 10px 20px;
  background: rgba(255, 255, 255, 0.2);
  border: none;
  border-radius: 10px;
  color: white;
  cursor: pointer;
}

.save-btn {
  padding: 10px 20px;
  background: white;
  border: none;
  border-radius: 10px;
  color: #667eea;
  font-weight: 600;
  cursor: pointer;
}

.quick-stats {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 120px;
}

.quick-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.quick-stat .stat-icon {
  font-size: 20px;
  margin-bottom: 4px;
}

.quick-stat .stat-value {
  font-size: 20px;
  font-weight: 700;
}

.quick-stat .stat-label {
  font-size: 12px;
  opacity: 0.8;
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
}

.tab-btn.active {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.tab-icon {
  font-size: 18px;
}

.content-section {
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.section-card {
  background: white;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.05);
  margin-bottom: 20px;
}

.section-card h3 {
  font-size: 18px;
  font-weight: 700;
  color: #2d3748;
  margin-bottom: 20px;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.info-label {
  font-size: 13px;
  color: #a0aec0;
}

.info-value {
  font-size: 15px;
  color: #2d3748;
  font-weight: 500;
}

.status-active {
  color: #48bb78;
}

.preferences {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.preference-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.pref-label {
  font-size: 14px;
  color: #718096;
}

.pref-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  max-height: 200px;
  overflow-y: auto;
}

.tag {
  padding: 6px 14px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border-radius: 20px;
  font-size: 13px;
  white-space: nowrap;
  line-height: 1.4;
}

.empty-tag {
  background: #e2e8f0;
  color: #a0aec0;
}

.interest-tag {
  background: linear-gradient(135deg, #48bb78 0%, #38a169 100%);
}

.pref-value {
  font-size: 15px;
  color: #2d3748;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  margin-bottom: 20px;
}

.stat-card {
  background: white;
  border-radius: 16px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.05);
}

.stat-icon-circle {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  margin-bottom: 12px;
}

.stat-number {
  font-size: 28px;
  font-weight: 700;
  color: #2d3748;
}

.stat-name {
  font-size: 14px;
  color: #718096;
}

.trend-chart {
  background: #f7fafc;
  border-radius: 12px;
  padding: 20px;
}

.chart-placeholder p {
  color: #4a5568;
  font-weight: 500;
  margin-bottom: 16px;
}

.mini-chart {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  height: 100px;
}

.chart-bar {
  flex: 1;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 4px 4px 0 0;
}

.no-data {
  text-align: center;
  padding: 30px 20px;
  background: #f7fafc;
  border-radius: 12px;
}

.no-data-icon {
  font-size: 36px;
  display: block;
  margin-bottom: 12px;
}

.no-data p {
  color: #718096;
  font-size: 14px;
  margin: 0;
}

.no-data .no-data-hint {
  color: #a0aec0;
  font-size: 12px;
  margin-top: 8px;
}

.achievements-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}

.achievement-card {
  background: white;
  border-radius: 16px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.05);
  transition: all 0.3s ease;
}

.achievement-card.unlocked {
  border: 2px solid #48bb78;
}

.achievement-card:not(.unlocked) {
  opacity: 0.6;
}

.achievement-icon {
  font-size: 48px;
  margin-bottom: 12px;
}

.achievement-info h4 {
  font-size: 16px;
  font-weight: 600;
  color: #2d3748;
  margin-bottom: 4px;
}

.achievement-info p {
  font-size: 13px;
  color: #718096;
  margin-bottom: 12px;
}

.unlock-badge {
  padding: 6px 12px;
  background: #c6f6d5;
  color: #276749;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 600;
}

.lock-badge {
  padding: 6px 12px;
  background: #e2e8f0;
  color: #718096;
  border-radius: 20px;
  font-size: 12px;
}

.setting-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 0;
  border-bottom: 1px solid #e2e8f0;
}

.setting-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.setting-label {
  font-size: 15px;
  font-weight: 600;
  color: #2d3748;
}

.setting-desc {
  font-size: 13px;
  color: #718096;
}

.setting-btn {
  padding: 8px 16px;
  background: #f7fafc;
  border: none;
  border-radius: 8px;
  color: #667eea;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
}

.setting-btn:hover {
  background: #edf2f7;
}

.email-binded {
  padding: 8px 16px;
  background: #c6f6d5;
  color: #276749;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
}

.toggle {
  position: relative;
  width: 50px;
  height: 26px;
}

.toggle input {
  opacity: 0;
  width: 0;
  height: 0;
}

.toggle-slider {
  position: absolute;
  inset: 0;
  background: #e2e8f0;
  border-radius: 26px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.toggle-slider::before {
  content: '';
  position: absolute;
  width: 20px;
  height: 20px;
  left: 3px;
  top: 3px;
  background: white;
  border-radius: 50%;
  transition: all 0.3s ease;
}

.toggle input:checked + .toggle-slider {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.toggle input:checked + .toggle-slider::before {
  left: 27px;
}

.danger-zone {
  background: #fff5f5;
  border-radius: 16px;
  padding: 24px;
  border: 1px solid #feb2b2;
}

.danger-zone h3 {
  font-size: 18px;
  font-weight: 700;
  color: #c53030;
  margin-bottom: 16px;
}

.logout-btn {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 24px;
  background: #c53030;
  color: white;
  border: none;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
}

.logout-btn:hover {
  background: #9b2c2c;
}

@media (max-width: 768px) {
  .user-card {
    flex-direction: column;
    align-items: center;
    text-align: center;
  }
  
  .quick-stats {
    flex-direction: row;
    flex-wrap: wrap;
    justify-content: center;
  }
  
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  
  .achievements-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  
  .info-grid {
    grid-template-columns: 1fr;
  }
}

.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-content {
  background: white;
  border-radius: 16px;
  width: 100%;
  max-width: 400px;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.2);
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  border-bottom: 1px solid #e2e8f0;
}

.modal-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #2d3748;
}

.modal-close {
  background: none;
  border: none;
  font-size: 24px;
  color: #a0aec0;
  cursor: pointer;
  padding: 0;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.modal-close:hover {
  color: #4a5568;
}

.modal-body {
  padding: 24px;
}

.modal-body .form-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.modal-body .form-group label {
  font-size: 14px;
  font-weight: 600;
  color: #4a5568;
}

.modal-body .form-input {
  width: 100%;
  padding: 12px 16px;
  border: 2px solid #e2e8f0;
  border-radius: 10px;
  font-size: 15px;
  outline: none;
  box-sizing: border-box;
  color: #2d3748;
  background: white;
}

.modal-body .form-input:focus {
  border-color: #667eea;
}

.modal-footer {
  display: flex;
  gap: 12px;
  padding: 16px 24px;
  border-top: 1px solid #e2e8f0;
}

.modal-btn {
  flex: 1;
  padding: 12px;
  border: none;
  border-radius: 10px;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
}

.modal-btn.cancel {
  background: #f7fafc;
  color: #4a5568;
}

.modal-btn.cancel:hover {
  background: #edf2f7;
}

.modal-btn.confirm {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.modal-btn.confirm:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(102, 126, 234, 0.4);
}
</style>