<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { gsap } from 'gsap';

const router = useRouter();

const stats = [
  { value: '10K+', label: '活跃学习者' },
  { value: '50K+', label: '生成资源' },
  { value: '100+', label: '智能体' },
  { value: '98%', label: '满意度' }
];

const features = [
  { icon: '📚', title: '资源中心', desc: '浏览海量学习资源', path: '/resources', floatClass: 'float-pos-1' },
  { icon: '🎯', title: '个性化学习', desc: '定制专属学习路径', path: '/learning', floatClass: 'float-pos-2' },
  { icon: '👥', title: '学习小组', desc: '与学习者共同进步', path: '/study-groups', floatClass: 'float-pos-3' },
  { icon: '🤖', title: 'AI助手', desc: '智能答疑解惑', path: '/assistant', floatClass: 'float-pos-4' }
];

const announcements = ref([
  { tag: '系统', tagClass: 'tag-system', title: '多智能体学习平台正式上线', desc: '基于大模型的个性化资源生成与学习系统已全面开放，欢迎体验全新智能学习模式。', time: '2024-06-01' },
  { tag: '更新', tagClass: 'tag-update', title: 'AI智能体能力大幅升级', desc: '新增多领域专业智能体，覆盖编程、数学、语言等多个学科，提供更精准的学习支持。', time: '2024-06-05' },
  { tag: '活动', tagClass: 'tag-event', title: '社区学习挑战赛火热进行中', desc: '参与学习挑战赛，赢取丰厚奖励，与全国学习者共同进步，冲刺排行榜前列！', time: '2024-06-08' },
  { tag: '公告', tagClass: 'tag-notice', title: '新用户首次登录引导优化', desc: '新注册用户将获得个性化学习画像引导，帮助系统更好地为您定制专属学习方案。', time: '2024-06-10' }
]);

onMounted(() => {
  animateLanding();
});

const animateLanding = () => {
  gsap.fromTo('.hero-title',
    { opacity: 0, y: 30 },
    { opacity: 1, y: 0, duration: 0.8, ease: 'power3.out' }
  );
  
  gsap.fromTo('.hero-desc',
    { opacity: 0, y: 20 },
    { opacity: 1, y: 0, duration: 0.6, delay: 0.3 }
  );
  
  gsap.fromTo('.hero-btn',
    { opacity: 0, scale: 0.9 },
    { opacity: 1, scale: 1, duration: 0.5, stagger: 0.15, delay: 0.5 }
  );
  
  gsap.fromTo('.feature-float-card',
    { opacity: 0, scale: 0.8 },
    { opacity: 1, scale: 1, duration: 0.5, stagger: 0.1, delay: 0.7 }
  );

  gsap.fromTo('.announcement-item',
    { opacity: 0, x: -20 },
    { opacity: 1, x: 0, duration: 0.4, stagger: 0.1, delay: 0.9 }
  );
};

const goToFeature = (path) => {
  router.push(path);
};

const goToLogin = () => {
  router.push('/login');
};
</script>

<template>
  <div class="landing-page">
    <section class="hero-section">
      <div class="hero-content">
        <h1 class="hero-title">
          <span class="gradient-text">智能学习多智能体系统</span>
        </h1>
        <p class="hero-desc">
          基于大模型的个性化资源生成与学习平台<br/>
          为您提供智能、高效、定制化的学习体验
        </p>
        <div class="hero-buttons">
          <button class="hero-btn primary" @click="goToLogin">
            <span>开始学习</span>
            <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
              <path d="M10 3l7 7-7 7M3 10h14" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
            </svg>
          </button>
          <button class="hero-btn secondary">
            了解更多
          </button>
        </div>
      </div>
      <div class="hero-visual">
        <button 
          v-for="feature in features" 
          :key="feature.title"
          :class="['feature-float-card', feature.floatClass]"
          @click="goToFeature(feature.path)"
        >
          <span class="float-card-icon">{{ feature.icon }}</span>
          <span class="float-card-title">{{ feature.title }}</span>
          <span class="float-card-desc">{{ feature.desc }}</span>
        </button>
      </div>
    </section>

    <section class="stats-section">
      <div class="stats-container">
        <div v-for="stat in stats" :key="stat.label" class="stat-item">
          <span class="stat-value">{{ stat.value }}</span>
          <span class="stat-label">{{ stat.label }}</span>
        </div>
      </div>
    </section>

    <section class="announcement-section">
      <div class="section-header">
        <h2>📢 公告栏</h2>
        <p>最新系统动态与通知</p>
      </div>
      <div class="announcement-list">
        <div v-for="(ann, idx) in announcements" :key="idx" class="announcement-item">
          <div class="ann-tag" :class="ann.tagClass">{{ ann.tag }}</div>
          <div class="ann-content">
            <h4>{{ ann.title }}</h4>
            <p>{{ ann.desc }}</p>
          </div>
          <span class="ann-time">{{ ann.time }}</span>
        </div>
      </div>
    </section>

    <section class="cta-section">
      <div class="cta-card">
        <h2>准备好开始您的学习之旅了吗？</h2>
        <p>加入我们，体验智能学习的魅力</p>
        <button class="cta-btn" @click="goToLogin">
          立即注册
        </button>
      </div>
    </section>

    <footer class="landing-footer">
      <p>&copy; 2026 智学未来——高等教育个性化多智能体学习系统</p>
    </footer>
  </div>
</template>

<style scoped>
.landing-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #f5f7fa 0%, #e4e8ec 100%);
}

.hero-section {
  padding: 100px 40px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  max-width: 1400px;
  margin: 0 auto;
  gap: 60px;
}

.hero-content {
  flex: 1;
}

.hero-title {
  font-size: 48px;
  font-weight: 700;
  margin-bottom: 20px;
  line-height: 1.2;
}

.gradient-text {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.hero-desc {
  font-size: 18px;
  color: #718096;
  line-height: 1.8;
  margin-bottom: 40px;
}

.hero-buttons {
  display: flex;
  gap: 20px;
}

.hero-btn {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px 36px;
  border-radius: 30px;
  font-size: 18px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
}

.hero-btn.primary {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  box-shadow: 0 8px 30px rgba(102, 126, 234, 0.3);
}

.hero-btn.primary:hover {
  transform: translateY(-3px);
  box-shadow: 0 12px 40px rgba(102, 126, 234, 0.4);
}

.hero-btn.secondary {
  background: white;
  color: #667eea;
  border: 2px solid #667eea;
}

.hero-btn.secondary:hover {
  background: #f7fafc;
}

.hero-visual {
  position: relative;
  width: 420px;
  height: 420px;
}

.feature-float-card {
  position: absolute;
  background: white;
  border-radius: 18px;
  padding: 18px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.1);
  cursor: pointer;
  border: none;
  width: 160px;
  transition: all 0.3s ease;
  animation: float 3s ease-in-out infinite;
}

.feature-float-card:hover {
  transform: translateY(-6px) scale(1.05);
  box-shadow: 0 15px 40px rgba(102, 126, 234, 0.25);
  border: 2px solid rgba(102, 126, 234, 0.3);
}

.float-card-icon {
  font-size: 32px;
}

.float-card-title {
  font-size: 15px;
  font-weight: 700;
  color: #2d3748;
}

.float-card-desc {
  font-size: 11px;
  color: #a0aec0;
  text-align: center;
  line-height: 1.4;
}

.float-pos-1 { top: 5%; left: 5%; animation-delay: 0s; }
.float-pos-2 { top: 5%; right: 5%; animation-delay: 0.5s; }
.float-pos-3 { bottom: 5%; left: 5%; animation-delay: 1s; }
.float-pos-4 { bottom: 5%; right: 5%; animation-delay: 1.5s; }

@keyframes float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-12px); }
}

.announcement-section {
  padding: 80px 40px;
  max-width: 1000px;
  margin: 0 auto;
}

.section-header {
  text-align: center;
  margin-bottom: 50px;
}

.section-header h2 {
  font-size: 36px;
  font-weight: 700;
  color: #2d3748;
  margin-bottom: 12px;
}

.section-header p {
  color: #718096;
  font-size: 18px;
}

.announcement-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.announcement-item {
  display: flex;
  align-items: center;
  gap: 20px;
  background: white;
  border-radius: 16px;
  padding: 20px 28px;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.06);
  transition: all 0.3s ease;
}

.announcement-item:hover {
  transform: translateX(6px);
  box-shadow: 0 8px 25px rgba(102, 126, 234, 0.15);
  border-left: 4px solid #667eea;
}

.ann-tag {
  flex-shrink: 0;
  padding: 4px 14px;
  border-radius: 12px;
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
}

.tag-system { background: #e8f0fe; color: #4285f4; }
.tag-update { background: #e6f4ea; color: #34a853; }
.tag-event { background: #fce8e6; color: #ea4335; }
.tag-notice { background: #fff3e0; color: #f9ab00; }

.ann-content {
  flex: 1;
  min-width: 0;
}

.ann-content h4 {
  font-size: 16px;
  font-weight: 600;
  color: #2d3748;
  margin-bottom: 4px;
}

.ann-content p {
  font-size: 14px;
  color: #718096;
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ann-time {
  flex-shrink: 0;
  font-size: 13px;
  color: #a0aec0;
}

.stats-section {
  padding: 60px 40px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.stats-container {
  display: flex;
  justify-content: center;
  gap: 80px;
  max-width: 1200px;
  margin: 0 auto;
}

.stat-item {
  text-align: center;
  color: white;
}

.stat-value {
  font-size: 48px;
  font-weight: 700;
  margin-bottom: 8px;
}

.stat-label {
  font-size: 16px;
  opacity: 0.9;
}

.cta-section {
  padding: 80px 40px;
  max-width: 1000px;
  margin: 0 auto;
}

.cta-card {
  background: white;
  border-radius: 24px;
  padding: 60px;
  text-align: center;
  box-shadow: 0 15px 50px rgba(0, 0, 0, 0.1);
}

.cta-card h2 {
  font-size: 32px;
  font-weight: 700;
  color: #2d3748;
  margin-bottom: 16px;
}

.cta-card p {
  color: #718096;
  font-size: 18px;
  margin-bottom: 30px;
}

.cta-btn {
  padding: 18px 48px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 30px;
  font-size: 18px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
}

.cta-btn:hover {
  transform: translateY(-3px);
  box-shadow: 0 10px 30px rgba(102, 126, 234, 0.4);
}

.landing-footer {
  padding: 30px;
  text-align: center;
  background: #2d3748;
  color: #a0aec0;
}

@media (max-width: 768px) {
  .hero-section {
    flex-direction: column;
    text-align: center;
    padding: 60px 20px;
  }
  
  .hero-title {
    font-size: 32px;
  }
  
  .hero-visual {
    width: 100%;
    height: 300px;
  }

  .feature-float-card {
    width: 130px;
    padding: 14px;
    gap: 4px;
  }

  .float-card-icon { font-size: 26px; }
  .float-card-title { font-size: 13px; }
  .float-card-desc { font-size: 10px; }

  .announcement-item {
    flex-wrap: wrap;
    gap: 10px;
  }

  .ann-time {
    margin-left: auto;
  }
  
  .stats-container {
    flex-wrap: wrap;
    gap: 40px;
  }
  
  .stat-value {
    font-size: 36px;
  }
}
</style>