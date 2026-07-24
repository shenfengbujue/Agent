<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { gsap } from 'gsap';

const router = useRouter();
const route = useRoute();

const isLoggedIn = ref(false);
const currentUser = ref(null);
const isMenuOpen = ref(false);
const activeNav = ref('Home');

const hiddenNavRoutes = ['/quiz'];

const navItems = [
  { name: 'Home', label: '首页', icon: '🏠', path: '/home' },
  { name: 'ResourceCenter', label: '资源中心', icon: '📚', path: '/resources' },
  { name: 'PersonalLearning', label: '个性化学习', icon: '🎯', path: '/learning' },
  { name: 'StudyGroup', label: '学习小组', icon: '👥', path: '/study-groups' },
  { name: 'AIAssistant', label: 'AI助手', icon: '🤖', path: '/assistant' }
];

const checkLoginStatus = () => {
  isLoggedIn.value = localStorage.getItem('isLoggedIn') === 'true';
  const storedUser = localStorage.getItem('user');
  if (storedUser) {
    currentUser.value = JSON.parse(storedUser);
  }
};

const handleNavClick = (item) => {
  activeNav.value = item.name;
  router.push(item.path);
  isMenuOpen.value = false;
};

const handleAvatarClick = () => {
  router.push('/profile');
};

watch(route, (newRoute) => {
  checkLoginStatus();
  const matchedRoute = navItems.find(item => newRoute.path === item.path);
  if (matchedRoute) {
    activeNav.value = matchedRoute.name;
  }
});

const handleStorageChange = (e) => {
  if (e.key === 'user') {
    const storedUser = localStorage.getItem('user');
    if (storedUser) {
      currentUser.value = JSON.parse(storedUser);
    }
  }
};

const handleUserUpdated = () => {
  const storedUser = localStorage.getItem('user');
  if (storedUser) {
    currentUser.value = JSON.parse(storedUser);
  }
};

onMounted(() => {
  checkLoginStatus();
  animateNav();
  window.addEventListener('storage', handleStorageChange);
  window.addEventListener('userUpdated', handleUserUpdated);
});

onUnmounted(() => {
  window.removeEventListener('storage', handleStorageChange);
  window.removeEventListener('userUpdated', handleUserUpdated);
});

const animateNav = () => {
  gsap.fromTo('.nav-item',
    { opacity: 0, y: -10 },
    { opacity: 1, y: 0, duration: 0.3, stagger: 0.1 }
  );
};
</script>

<template>
  <div class="app-container">
    <nav v-if="isLoggedIn && !hiddenNavRoutes.includes(route.path)" class="navbar">
      <div class="nav-content">
        <div class="nav-logo" @click="handleNavClick(navItems[0])">
          <div class="logo-icon">
            <img src="/assets/logo.jpeg" alt="智学未来" class="logo-img" />
          </div>
          <span class="logo-text">智学未来</span>
        </div>
        
        <div class="nav-links">
          <button
            v-for="item in navItems"
            :key="item.name"
            :class="['nav-item', { active: activeNav === item.name }]"
            @click="handleNavClick(item)"
          >
            <span class="nav-icon">{{ item.icon }}</span>
            <span class="nav-label">{{ item.label }}</span>
          </button>
        </div>
        
        <div class="nav-user" @click="handleAvatarClick">
            <div class="user-info">
              <span class="user-name">{{ currentUser?.nickname }}</span>
              <span class="user-level">LVL {{ currentUser?.level }}</span>
            </div>
            <div class="user-avatar">
              <img v-if="currentUser?.avatarUrl" :src="currentUser.avatarUrl" alt="用户头像" class="avatar-img" />
              <span v-else>{{ currentUser?.avatar }}</span>
            </div>
          </div>
        
        <button class="mobile-menu-btn" @click="isMenuOpen = !isMenuOpen">
          <svg v-if="!isMenuOpen" width="24" height="24" viewBox="0 0 24 24" fill="none">
            <path d="M4 6h16M4 12h16M4 18h16" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
          </svg>
          <svg v-else width="24" height="24" viewBox="0 0 24 24" fill="none">
            <path d="M6 18L18 6M6 6l12 12" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
          </svg>
        </button>
      </div>
      
      <div v-if="isMenuOpen" class="mobile-menu">
        <button
          v-for="item in navItems"
          :key="item.name"
          :class="['mobile-nav-item', { active: activeNav === item.name }]"
          @click="handleNavClick(item)"
        >
          <span class="nav-icon">{{ item.icon }}</span>
          <span class="nav-label">{{ item.label }}</span>
        </button>
        <button class="mobile-nav-item profile-btn" @click="handleAvatarClick">
          <span class="nav-icon">👤</span>
          <span class="nav-label">个人中心</span>
        </button>
      </div>
    </nav>
    
    <main class="main-content">
      <router-view v-slot="{ Component }">
        <keep-alive :include="['AIAssistant']">
          <component :is="Component" />
        </keep-alive>
      </router-view>
    </main>
    
    <footer v-if="isLoggedIn && !hiddenNavRoutes.includes(route.path)" class="footer">
      <div class="footer-content">
        <p>© 2026 智学未来——高等教育个性化多智能体学习系统</p>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.app-container {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: linear-gradient(135deg, #f5f7fa 0%, #e4e8ec 100%);
}

.navbar {
  background: white;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.05);
  position: sticky;
  top: 0;
  z-index: 100;
}

.nav-content {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 70px;
}

.nav-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
}

.logo-icon {
  display: flex;
  align-items: center;
  justify-content: center;
}

.logo-img {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  object-fit: cover;
}

.logo-text {
  font-size: 20px;
  font-weight: 700;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.nav-links {
  display: flex;
  gap: 4px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 16px;
  border-radius: 10px;
  background: none;
  border: none;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  color: #4a5568;
  transition: all 0.3s ease;
}

.nav-item:hover {
  background: #f7fafc;
  color: #667eea;
}

.nav-item.active {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.nav-icon {
  font-size: 16px;
}

.nav-label {
  display: block;
}

.nav-user {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 16px;
  background: #f7fafc;
  border-radius: 25px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.nav-user:hover {
  background: #edf2f7;
}

.user-info {
  display: flex;
  flex-direction: column;
}

.user-name {
  font-size: 14px;
  font-weight: 600;
  color: #2d3748;
}

.user-level {
  font-size: 12px;
  color: #667eea;
  font-weight: 500;
}

.user-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  overflow: hidden;
}

.user-avatar .avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.mobile-menu-btn {
  display: none;
  background: none;
  border: none;
  color: #4a5568;
  cursor: pointer;
  padding: 8px;
}

.mobile-menu {
  display: none;
  padding: 16px 20px;
  background: white;
  border-top: 1px solid #e2e8f0;
}

.mobile-nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 16px;
  background: none;
  border: none;
  cursor: pointer;
  font-size: 16px;
  font-weight: 500;
  color: #4a5568;
  border-radius: 10px;
  transition: all 0.3s ease;
}

.mobile-nav-item:hover {
  background: #f7fafc;
}

.mobile-nav-item.active {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.profile-btn {
  margin-top: 8px;
  border-top: 1px solid #e2e8f0;
  padding-top: 20px;
}

.main-content {
  flex: 1;
  padding: 0;
}

.footer {
  background: white;
  padding: 20px;
  margin-top: auto;
}

.footer-content {
  max-width: 1400px;
  margin: 0 auto;
  text-align: center;
}

.footer-content p {
  color: #a0aec0;
  font-size: 14px;
}

@media (max-width: 768px) {
  .nav-links {
    display: none;
  }
  
  .nav-user {
    display: none;
  }
  
  .mobile-menu-btn {
    display: block;
  }
  
  .mobile-menu {
    display: block;
  }
}
</style>