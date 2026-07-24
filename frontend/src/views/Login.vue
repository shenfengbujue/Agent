<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { gsap } from 'gsap';
import { authApi } from '../api/index';

const router = useRouter();
const isLogin = ref(true);
const isLoading = ref(false);

const loginForm = ref({
  username: '',
  password: ''
});

const registerForm = ref({
  username: '',
  email: '',
  password: '',
  confirmPassword: '',
  nickname: ''
});

const errors = ref({
  username: '',
  password: '',
  email: '',
  confirmPassword: ''
});

onMounted(() => {
  animatePage();
});

const animatePage = () => {
  gsap.fromTo('.auth-container',
    { opacity: 0, y: 30 },
    { opacity: 1, y: 0, duration: 0.6, ease: 'power3.out' }
  );
  
  gsap.fromTo('.form-input',
    { opacity: 0, x: -20 },
    { opacity: 1, x: 0, duration: 0.4, stagger: 0.1, delay: 0.3 }
  );
};

const switchMode = () => {
  isLogin.value = !isLogin.value;
  errors.value = { username: '', password: '', email: '', confirmPassword: '' };
};

const validateLogin = () => {
  let valid = true;
  errors.value = { username: '', password: '', email: '', confirmPassword: '' };
  
  if (!loginForm.value.username.trim()) {
    errors.value.username = '请输入用户名';
    valid = false;
  }
  if (!loginForm.value.password.trim()) {
    errors.value.password = '请输入密码';
    valid = false;
  }
  
  return valid;
};

const validateRegister = () => {
  let valid = true;
  errors.value = { username: '', password: '', email: '', confirmPassword: '' };
  
  if (!registerForm.value.username.trim()) {
    errors.value.username = '请输入用户名';
    valid = false;
  }
  if (!registerForm.value.email.trim()) {
    errors.value.email = '请输入邮箱';
    valid = false;
  } else if (!/\S+@\S+\.\S+/.test(registerForm.value.email)) {
    errors.value.email = '邮箱格式不正确';
    valid = false;
  }
  if (!registerForm.value.password.trim()) {
    errors.value.password = '请输入密码';
    valid = false;
  } else if (registerForm.value.password.length < 6) {
    errors.value.password = '密码至少6位';
    valid = false;
  }
  if (registerForm.value.password !== registerForm.value.confirmPassword) {
    errors.value.confirmPassword = '两次密码不一致';
    valid = false;
  }
  
  return valid;
};

const handleLogin = async () => {
  if (!validateLogin()) return;
  
  isLoading.value = true;
  
  try {
    const response = await authApi.login({
      username: loginForm.value.username,
      password: loginForm.value.password
    });
    
    const result = response.data;
    if (result.code === 200 && result.data) {
      // 清理上一位用户缓存
      Object.keys(localStorage).filter(k => k.startsWith('agent_result_')).forEach(k => localStorage.removeItem(k));
      localStorage.setItem('token', result.data.token);
      localStorage.setItem('isLoggedIn', 'true');
      localStorage.setItem('profileCompleted', 'true');
      localStorage.setItem('user', JSON.stringify({
        id: result.data.userId || 1,
        username: result.data.username || loginForm.value.username,
        nickname: result.data.nickname || loginForm.value.username,
        level: result.data.level || 1,
        loginDays: result.data.loginDays || 0,
        avatar: result.data.avatar || '👤',
        createdAt: result.data.createdAt,
        lastLogin: result.data.lastLogin
      }));
      
      router.push('/home');
    } else {
      errors.value.password = result.message || '登录失败';
    }
  } catch (err) {
    console.error('Login failed:', err);
    if (err.response && err.response.data && err.response.data.message) {
      errors.value.password = err.response.data.message;
    } else {
      errors.value.password = '登录失败，请检查网络连接';
    }
  } finally {
    isLoading.value = false;
  }
};

const handleRegister = async () => {
  if (!validateRegister()) return;
  
  isLoading.value = true;
  
  try {
    const response = await authApi.register({
      username: registerForm.value.username,
      password: registerForm.value.password,
      email: registerForm.value.email,
      nickname: registerForm.value.nickname || null
    });
    
    const result = response.data;
    if (result.code === 200 && result.data) {
      // 清理上一位用户缓存
      Object.keys(localStorage).filter(k => k.startsWith('agent_result_')).forEach(k => localStorage.removeItem(k));
      localStorage.setItem('token', result.data.token);
      localStorage.setItem('isLoggedIn', 'true');
      localStorage.setItem('user', JSON.stringify({
        id: result.data.userId || 1,
        username: registerForm.value.username,
        nickname: registerForm.value.nickname || null,
        email: registerForm.value.email,
        level: 1,
        avatar: '👤'
      }));
    } else {
      errors.value.username = result.message || '注册失败';
      isLoading.value = false;
      return;
    }
    
    localStorage.setItem('profileCompleted', 'false');
    router.push('/quiz');
  } catch (err) {
    console.error('Register failed:', err);
    if (err.response && err.response.data && err.response.data.message) {
      errors.value.username = err.response.data.message;
    } else {
      errors.value.username = '注册失败，请检查网络连接';
    }
  } finally {
    isLoading.value = false;
  }
};

const goBack = () => {
  router.push('/');
};
</script>

<template>
  <div class="auth-page">
    <div class="auth-background">
      <div class="bg-shape shape-1"></div>
      <div class="bg-shape shape-2"></div>
      <div class="bg-shape shape-3"></div>
    </div>
    
    <div class="auth-container">
      <div class="auth-header">
        <button class="back-btn" @click="goBack">
          <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
            <path d="M15 10H5M5 10l5-5M5 10l5 5" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
          </svg>
          返回首页
        </button>
        <div class="logo">
          <img src="/assets/logo.jpeg" alt="智学未来" class="logo-img" />
          <span class="logo-text">智学未来</span>
        </div>
      </div>
      
      <div class="auth-card">
        <div class="card-header">
          <h2>{{ isLogin ? '登录账号' : '注册账号' }}</h2>
          <p>{{ isLogin ? '欢迎回来，继续您的学习之旅' : '加入我们，开启智能学习新体验' }}</p>
        </div>
        
        <div class="mode-switch">
          <button :class="['mode-btn', { active: isLogin }]" @click="isLogin = true">
            登录
          </button>
          <button :class="['mode-btn', { active: !isLogin }]" @click="isLogin = false">
            注册
          </button>
        </div>
        
        <form v-if="isLogin" class="auth-form" @submit.prevent="handleLogin">
          <div class="form-group">
            <label>用户名</label>
            <div class="input-wrapper">
              <svg class="input-icon" width="20" height="20" viewBox="0 0 20 20" fill="none">
                <path d="M10 10a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM2 18a6 6 0 0 1 6-6h4a6 6 0 0 1 6 6" stroke="#a0aec0" stroke-width="2" stroke-linecap="round"/>
              </svg>
              <input 
                v-model="loginForm.username" 
                type="text" 
                class="form-input"
                placeholder="请输入用户名"
              />
            </div>
            <span v-if="errors.username" class="error-msg">{{ errors.username }}</span>
          </div>
          
          <div class="form-group">
            <label>密码</label>
            <div class="input-wrapper">
              <svg class="input-icon" width="20" height="20" viewBox="0 0 20 20" fill="none">
                <path d="M5 10V6a5 5 0 0 1 10 0v4M3 10h14a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2z" stroke="#a0aec0" stroke-width="2" stroke-linecap="round"/>
              </svg>
              <input 
                v-model="loginForm.password" 
                type="password" 
                class="form-input"
                placeholder="请输入密码"
              />
            </div>
            <span v-if="errors.password" class="error-msg">{{ errors.password }}</span>
          </div>
          
          <div class="form-options">
            <label class="remember-me">
              <input type="checkbox" />
              <span>记住我</span>
            </label>
            <a href="#" class="forgot-link">忘记密码？</a>
          </div>
          
          <button :disabled="isLoading" class="submit-btn" type="submit">
            <span v-if="isLoading" class="loading-spinner"></span>
            <span v-else>登录</span>
          </button>
        </form>
        
        <form v-else class="auth-form" @submit.prevent="handleRegister">
          <div class="form-group">
            <label>用户名</label>
            <div class="input-wrapper">
              <svg class="input-icon" width="20" height="20" viewBox="0 0 20 20" fill="none">
                <path d="M10 10a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM2 18a6 6 0 0 1 6-6h4a6 6 0 0 1 6 6" stroke="#a0aec0" stroke-width="2" stroke-linecap="round"/>
              </svg>
              <input 
                v-model="registerForm.username" 
                type="text" 
                class="form-input"
                placeholder="请输入用户名"
              />
            </div>
            <span v-if="errors.username" class="error-msg">{{ errors.username }}</span>
          </div>
          
          <div class="form-group">
            <label>昵称</label>
            <div class="input-wrapper">
              <svg class="input-icon" width="20" height="20" viewBox="0 0 20 20" fill="none">
                <path d="M3 5h14M3 10h10M3 15h6" stroke="#a0aec0" stroke-width="2" stroke-linecap="round"/>
              </svg>
              <input 
                v-model="registerForm.nickname" 
                type="text" 
                class="form-input"
                placeholder="请输入昵称（可选）"
              />
            </div>
          </div>
          
          <div class="form-group">
            <label>邮箱</label>
            <div class="input-wrapper">
              <svg class="input-icon" width="20" height="20" viewBox="0 0 20 20" fill="none">
                <path d="M2 6l8 5 8-5M2 6v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V6" stroke="#a0aec0" stroke-width="2" stroke-linecap="round"/>
              </svg>
              <input 
                v-model="registerForm.email" 
                type="email" 
                class="form-input"
                placeholder="请输入邮箱"
              />
            </div>
            <span v-if="errors.email" class="error-msg">{{ errors.email }}</span>
          </div>
          
          <div class="form-group">
            <label>密码</label>
            <div class="input-wrapper">
              <svg class="input-icon" width="20" height="20" viewBox="0 0 20 20" fill="none">
                <path d="M5 10V6a5 5 0 0 1 10 0v4M3 10h14a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2z" stroke="#a0aec0" stroke-width="2" stroke-linecap="round"/>
              </svg>
              <input 
                v-model="registerForm.password" 
                type="password" 
                class="form-input"
                placeholder="请输入密码（至少6位）"
              />
            </div>
            <span v-if="errors.password" class="error-msg">{{ errors.password }}</span>
          </div>
          
          <div class="form-group">
            <label>确认密码</label>
            <div class="input-wrapper">
              <svg class="input-icon" width="20" height="20" viewBox="0 0 20 20" fill="none">
                <path d="M5 10V6a5 5 0 0 1 10 0v4M3 10h14a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2z" stroke="#a0aec0" stroke-width="2" stroke-linecap="round"/>
              </svg>
              <input 
                v-model="registerForm.confirmPassword" 
                type="password" 
                class="form-input"
                placeholder="请再次输入密码"
              />
            </div>
            <span v-if="errors.confirmPassword" class="error-msg">{{ errors.confirmPassword }}</span>
          </div>
          
          <button :disabled="isLoading" class="submit-btn" type="submit">
            <span v-if="isLoading" class="loading-spinner"></span>
            <span v-else>注册</span>
          </button>
        </form>
        
        <div class="auth-footer">
          <p>
            {{ isLogin ? '还没有账号？' : '已有账号？' }}
            <a @click="switchMode" class="switch-link">
              {{ isLogin ? '立即注册' : '立即登录' }}
            </a>
          </p>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #f5f7fa 0%, #e4e8ec 100%);
  position: relative;
  overflow: hidden;
}

.auth-background {
  position: absolute;
  inset: 0;
  overflow: hidden;
}

.bg-shape {
  position: absolute;
  border-radius: 50%;
  opacity: 0.1;
}

.shape-1 {
  width: 400px;
  height: 400px;
  background: #667eea;
  top: -100px;
  right: -100px;
}

.shape-2 {
  width: 300px;
  height: 300px;
  background: #764ba2;
  bottom: -50px;
  left: -50px;
}

.shape-3 {
  width: 200px;
  height: 200px;
  background: #48bb78;
  top: 50%;
  left: 20%;
}

.auth-container {
  width: 100%;
  max-width: 420px;
  padding: 20px;
  z-index: 1;
}

.auth-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
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

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
}

.logo-img {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  object-fit: cover;
}

.logo-text {
  font-size: 18px;
  font-weight: 700;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.auth-card {
  background: white;
  border-radius: 24px;
  padding: 40px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.1);
}

.card-header {
  text-align: center;
  margin-bottom: 24px;
}

.card-header h2 {
  font-size: 28px;
  font-weight: 700;
  color: #2d3748;
  margin-bottom: 8px;
}

.card-header p {
  color: #718096;
  font-size: 14px;
}

.mode-switch {
  display: flex;
  background: #f7fafc;
  border-radius: 12px;
  padding: 4px;
  margin-bottom: 24px;
}

.mode-btn {
  flex: 1;
  padding: 12px;
  border: none;
  border-radius: 10px;
  background: none;
  color: #718096;
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
}

.mode-btn.active {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.form-group label {
  font-size: 14px;
  font-weight: 600;
  color: #4a5568;
}

.input-wrapper {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #f7fafc;
  border-radius: 12px;
  padding: 14px 16px;
  border: 2px solid transparent;
  transition: all 0.3s ease;
}

.input-wrapper:focus-within {
  border-color: #667eea;
  background: white;
}

.input-icon {
  flex-shrink: 0;
}

.form-input {
  flex: 1;
  border: none;
  background: none;
  font-size: 15px;
  color: #2d3748;
  outline: none;
}

.form-input::placeholder {
  color: #a0aec0;
}

.error-msg {
  font-size: 13px;
  color: #e53e3e;
}

.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.remember-me {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: #4a5568;
  cursor: pointer;
}

.remember-me input {
  width: 16px;
  height: 16px;
  accent-color: #667eea;
}

.forgot-link {
  font-size: 14px;
  color: #667eea;
  text-decoration: none;
}

.forgot-link:hover {
  text-decoration: underline;
}

.submit-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 12px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  margin-top: 8px;
}

.submit-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(102, 126, 234, 0.4);
}

.submit-btn:disabled {
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

.auth-footer {
  text-align: center;
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid #e2e8f0;
}

.auth-footer p {
  color: #718096;
  font-size: 14px;
}

.switch-link {
  color: #667eea;
  font-weight: 600;
  cursor: pointer;
}

.switch-link:hover {
  text-decoration: underline;
}

@media (max-width: 768px) {
  .auth-card {
    padding: 30px 20px;
  }
  
  .card-header h2 {
    font-size: 24px;
  }
}
</style>