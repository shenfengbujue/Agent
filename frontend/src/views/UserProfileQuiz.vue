<script setup>
import { ref, reactive, computed } from 'vue';
import { useRouter } from 'vue-router';
import { gsap } from 'gsap';
import axios from 'axios';
import { profileApi } from '../api/index';

const router = useRouter();

const currentStep = ref(0);
const isSubmitting = ref(false);
const otherInput = ref('');
const showDialogueEnhance = ref(false);
const dialogueInput = ref('');
const dialogueAnalyzing = ref(false);
const dialogueResult = ref(null);

const baseQuestion = {
  id: 0,
  title: '嗨！欢迎来到智能学习平台！',
  description: '为了帮你量身定制接下来的学习路线，请选择您最近最想达成的目标是什么？',
  options: [
    { value: 'career_skill', label: '职场/实用技能', icon: '💼', desc: '（学Python、学剪辑，想解决工作难题）' },
    { value: 'exam升学', label: '应试/考证/升学', icon: '📚', desc: '（考研、四六级、考公，急需提分）' },
    { value: 'hobby_self', label: '兴趣/自我提升', icon: '🎯', desc: '（学画画、学吉他等）' }
  ],
  showWelcome: true
};

const branchQuestions = {
  career_skill: [
    {
      id: 1,
      title: '可用时间与专注度',
      description: '您每天大概有多少时间可以用于学习',
      options: [
        { value: '15min', label: '每天15分钟以内（碎片化）', icon: '⏳' },
        { value: '30min-1h', label: '每天半小时到1小时', icon: '🕐' },
        { value: '1h+', label: '每天1小时以上', icon: '🕑' },
        { value: 'weekend', label: '只有周末能集中学习', icon: '📅' }
      ]
    },
    {
      id: 2,
      title: '学习内容方式偏好',
      description: '您更喜欢哪种学习形式',
      options: [
        { value: 'audio', label: '听音频讲解', icon: '🎧' },
        { value: 'text', label: '看图文干货', icon: '📖' },
        { value: 'practice', label: '做轻量级互动练习', icon: '✏️' },
        { value: 'video', label: '看实操演示视频', icon: '🎬' }
      ]
    },
    {
      id: 3,
      title: '在实际工作中，您最希望通过学习解决哪类痛点？',
      description: '可多选',
      multiple: true,
      options: [
        { value: 'efficiency', label: '提升执行效率', icon: '⚡' },
        { value: 'quality', label: '提高产出质量', icon: '✅' },
        { value: 'communication', label: '增强沟通表达', icon: '💬' },
        { value: 'tech_bottleneck', label: '突破技术瓶颈', icon: '🔧' },
        { value: 'no_pain', label: '暂无具体痛点', icon: '🤷' }
      ]
    },
    {
      id: 4,
      title: '技术/工具熟练度',
      description: '您对相关技术或工具的掌握程度',
      options: [
        { value: 'never', label: '完全没用过，需要手把手教', icon: '🌱' },
        { value: 'basic', label: '用过一点，但不熟练', icon: '📚' },
        { value: 'proficient', label: '已经比较熟练，只需学进阶技巧', icon: '🎯' }
      ]
    }
  ],
  exam升学: [
    {
      id: 1,
      title: '考试时间',
      description: '您计划什么时候参加考试',
      options: [
        { value: '1month', label: '1个月内（短期冲刺）', icon: '⚡' },
        { value: '3months', label: '3个月左右（中期备考）', icon: '📅' },
        { value: '6months+', label: '半年及以上（长线复习）', icon: '📚' },
        { value: 'undecided', label: '还没确定具体时间（探索阶段）', icon: '🔍' }
      ]
    },
    {
      id: 2,
      title: '学习方式偏好',
      description: '您更喜欢哪种学习方法',
      options: [
        { value: 'practice_after_read', label: '看图文干货后练习对应题目/疯狂刷题', icon: '📝' },
        { value: 'video_text_method', label: '喜欢看图文解析学方法', icon: '🎬' },
        { value: 'interactive', label: '喜欢边学边做互动练习', icon: '✏️' }
      ]
    },
    {
      id: 3,
      title: '当前知识掌握度',
      description: '您目前对考试内容的掌握情况',
      options: [
        { value: 'zero', label: '完全零基础', icon: '🌱' },
        { value: 'forgotten', label: '学过但忘了很多', icon: '💭' },
        { value: 'reviewing', label: '正在系统复习中', icon: '📖' },
        { value: 'sprint', label: '已经刷过几轮题，处于冲刺阶段', icon: '🏃' }
      ]
    },
    {
      id: 4,
      title: '薄弱环节/错题痛点',
      description: '可多选',
      multiple: true,
      options: [
        { value: 'concepts', label: '基础概念记不住', icon: '🧠' },
        { value: 'problem_solving', label: '大题没有解题思路', icon: '🤔' },
        { value: 'calculation', label: '计算总是出错', icon: '🔢' },
        { value: 'speed', label: '做题速度太慢', icon: '⏱️' },
        { value: 'other', label: '其他', icon: '📝' }
      ],
      hasOtherInput: true
    }
  ],
  hobby_self: [
    {
      id: 1,
      title: '学习动机与期望',
      description: '您学习的主要目的是什么',
      options: [
        { value: 'hobby', label: '培养个人爱好', icon: '🎨' },
        { value: 'share', label: '想发朋友圈/社交平台分享', icon: '📱' },
        { value: 'relax', label: '缓解压力、放松身心', icon: '🧘' },
        { value: 'social', label: '拓展社交、结交同好', icon: '👥' }
      ]
    },
    {
      id: 2,
      title: '进度与成就偏好',
      description: '您更喜欢哪种学习模式',
      options: [
        { value: 'level', label: '喜欢一关一关解锁的闯关模式', icon: '🎮' },
        { value: 'explore', label: '喜欢自由探索的知识地图', icon: '🗺️' },
        { value: 'checkin', label: '喜欢每天打卡的养成模式', icon: '✅' }
      ]
    },
    {
      id: 3,
      title: '社交与互动意愿',
      description: '您在学习社区中的互动意愿',
      options: [
        { value: 'active', label: '非常愿意，喜欢和大家交流', icon: '💬' },
        { value: 'passive', label: '偶尔参与，看看别人的作品', icon: '👀' },
        { value: 'alone', label: '更喜欢一个人安静地学', icon: '🧑‍💻' }
      ]
    },
    {
      id: 4,
      title: '抗挫折倾向',
      description: '学习时遇到难点希望的处理方式',
      options: [
        { value: 'direct', label: '直接给我详细解析和答案', icon: '📝' },
        { value: 'hint', label: '给我一点小提示，让我自己先琢磨', icon: '💡' },
        { value: 'transition', label: '推送一些更简单的过渡内容帮我建立自信', icon: '🌟' }
      ]
    }
  ]
};

const answers = reactive({});

const currentQuestions = computed(() => {
  const firstAnswer = answers[0];
  if (firstAnswer && branchQuestions[firstAnswer] && branchQuestions[firstAnswer].length > 0) {
    return [baseQuestion, ...branchQuestions[firstAnswer]];
  }
  return [baseQuestion];
});

const currentQuestion = computed(() => currentQuestions.value[currentStep.value]);
const progress = computed(() => ((currentStep.value + 1) / currentQuestions.value.length) * 100);
const isLastQuestion = computed(() => currentStep.value === currentQuestions.value.length - 1);

const selectOption = (value) => {
  const questionId = currentQuestion.value.id;
  
  if (currentQuestion.value.multiple) {
    if (!answers[questionId]) {
      answers[questionId] = [];
    }
    const index = answers[questionId].indexOf(value);
    if (index > -1) {
      answers[questionId].splice(index, 1);
      answers[questionId] = [...answers[questionId]];
    } else {
      answers[questionId] = [...answers[questionId], value];
    }
  } else {
    answers[questionId] = value;
  }
};

const nextStep = () => {
  if (isLastQuestion.value) {
    submitProfile();
  } else {
    gsap.to('.question-card', { opacity: 0, x: -30, duration: 0.3, onComplete: () => {
      currentStep.value++;
      gsap.fromTo('.question-card', { opacity: 0, x: 30 }, { opacity: 1, x: 0, duration: 0.3 });
    }});
  }
};

const prevStep = () => {
  if (currentStep.value > 0) {
    gsap.to('.question-card', { opacity: 0, x: 30, duration: 0.3, onComplete: () => {
      currentStep.value--;
      gsap.fromTo('.question-card', { opacity: 0, x: -30 }, { opacity: 1, x: 0, duration: 0.3 });
    }});
  }
};

const submitProfile = async () => {
  isSubmitting.value = true;
  
  try {
    const learningGoal = answers[0];
    
    let profileData = {
      profileData: JSON.stringify(answers),
      learningGoal: learningGoal
    };

    if (learningGoal === 'career_skill') {
      profileData.timeAvailability = answers[1];
      profileData.learningStyle = answers[2];
      profileData.workPainPoints = Array.isArray(answers[3]) ? answers[3].join(',') : answers[3];
      profileData.skillLevel = answers[4];
    } else if (learningGoal === 'exam升学') {
      profileData.examTime = answers[1];
      profileData.learningStyle = answers[2];
      profileData.knowledgeLevel = answers[3];
      profileData.weakPoints = Array.isArray(answers[4]) ? answers[4].join(',') : answers[4];
    } else if (learningGoal === 'hobby_self') {
      profileData.motivation = answers[1];
      profileData.achievementStyle = answers[2];
      profileData.socialWillingness = answers[3];
      profileData.frustrationHandling = answers[4];
    }

    const response = await axios.post('/api/auth/profile', profileData);

    const responseJson = response.data;
    if (responseJson.code === 200 || responseJson.code === 0) {
      const user = JSON.parse(localStorage.getItem('user') || '{}');
      user.profileCompleted = true;
      user.profile = answers;
      localStorage.setItem('user', JSON.stringify(user));
      localStorage.setItem('profileCompleted', 'true');

      // 同步更新用户画像关键词和兴趣到 UserProfile 表
      try {
        const userId = user.id;
        if (userId) {
          // 从答案中提取有意义的关键词文本（过滤短代码和选项值）
          const rawNoise = new Set(['explore','share','alone','hint','15min','30min-1h','1h+','weekend',
            'audio','text','practice','video','never','career_skill','exam升学','hobby_self']);
          const allAnswerText = Object.values(answers)
            .map(v => Array.isArray(v) ? v.join(',') : v)
            .filter(v => v && v.length >= 3 && !rawNoise.has(v))
            .join(' ');
          if (allAnswerText) await profileApi.updateKeywords(userId, allAnswerText);

          // 添加学习目标为兴趣
          const goalLabels = {
            'career_skill': '职场技能',
            'exam升学': '升学考试',
            'hobby_self': '兴趣自学'
          };
          const goalLabel = goalLabels[learningGoal] || learningGoal;
          await profileApi.addInterest(userId, goalLabel);
        }
      } catch (profileErr) {
        console.error('同步用户画像失败:', profileErr);
      }

      // 显示对话式画像补充入口
      showDialogueEnhance.value = true;
    } else {
      alert('保存失败: ' + responseJson.message);
    }
  } catch (error) {
    console.error('保存用户画像失败:', error);
    if (error.response && error.response.data && error.response.data.message) {
      alert('保存失败: ' + error.response.data.message);
    } else {
      alert('保存失败: ' + error.message);
    }
  }

  isSubmitting.value = false;
  if (!showDialogueEnhance.value) {
    router.push('/home');
  }
};

const submitDialogue = async () => {
  if (!dialogueInput.value.trim()) return;
  dialogueAnalyzing.value = true;
  try {
    const user = JSON.parse(localStorage.getItem('user') || '{}');
    const userId = user.id;
    const res = await profileApi.analyzeDialogue(userId, dialogueInput.value);
    dialogueResult.value = res.data?.data || res.data;
  } catch (e) {
    console.error('对话画像分析失败:', e);
    dialogueResult.value = { error: '分析失败，请稍后重试' };
  }
  dialogueAnalyzing.value = false;
};

const finishDialogue = () => {
  showDialogueEnhance.value = false;
  router.push('/home');
};

const skipQuiz = () => {
  const user = JSON.parse(localStorage.getItem('user') || '{}');
  user.profileCompleted = true;
  localStorage.setItem('user', JSON.stringify(user));
  localStorage.setItem('profileCompleted', 'true');
  router.push('/home');
};
</script>

<template>
  <div class="quiz-page">
    <div class="quiz-background">
      <div class="bg-circle circle-1"></div>
      <div class="bg-circle circle-2"></div>
      <div class="bg-circle circle-3"></div>
    </div>
    
    <div class="quiz-container">
      <div class="quiz-header">
        <div class="logo-section">
          <img src="/assets/logo.jpeg" alt="智学未来" class="quiz-logo-img" />
          <span class="logo-text">智学未来</span>
        </div>
        <button class="skip-btn" @click="skipQuiz">跳过</button>
      </div>
      
      <div v-if="!currentQuestion.showWelcome" class="progress-section">
        <div class="progress-bar">
          <div class="progress-fill" :style="{ width: progress + '%' }"></div>
        </div>
        <span class="progress-text">{{ currentStep }} / {{ currentQuestions.length - 1 }}</span>
      </div>
      
      <div class="question-card">
        <div v-if="!currentQuestion.showWelcome" class="question-number">
          <span class="number">{{ String(currentStep).padStart(2, '0') }}</span>
          <span class="divider"></span>
          <span class="total">{{ String(currentQuestions.length - 1).padStart(2, '0') }}</span>
        </div>
        
        <h2 class="question-title">
          {{ currentQuestion.title }}
          <span v-if="currentQuestion.multiple" class="multiple-tag">（多选题）</span>
        </h2>
        <p class="question-desc">{{ currentQuestion.description }}</p>
        
        <div class="options-grid">
          <button 
            v-for="option in currentQuestion.options" 
            :key="option.value"
            :class="['option-btn', { selected: currentQuestion.multiple ? answers[currentQuestion.id]?.includes(option.value) : answers[currentQuestion.id] === option.value }]"
            @click="selectOption(option.value)"
          >
            <span class="option-icon">{{ option.icon }}</span>
            <span class="option-label">{{ option.label }}</span>
            <span v-if="option.desc" class="option-desc">{{ option.desc }}</span>
          </button>
        </div>
        
        <div v-if="currentQuestion.hasOtherInput && answers[currentQuestion.id]?.includes('other')" class="other-input-container">
          <input 
            v-model="otherInput" 
            type="text" 
            class="other-input" 
            placeholder="请输入具体的薄弱环节或错题痛点..."
          />
        </div>
        
        <div class="nav-buttons">
          <button v-if="currentStep > 0" class="nav-btn secondary" @click="prevStep">
            <svg width="18" height="18" viewBox="0 0 20 20" fill="none">
              <path d="M15 10H5M5 10l5-5M5 10l5 5" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
            </svg>
            上一步
          </button>
          <button 
            :disabled="isSubmitting" 
            class="nav-btn primary" 
            @click="isLastQuestion ? submitProfile() : nextStep()"
          >
            <span v-if="isSubmitting" class="loading-spinner"></span>
            <span v-else>{{ isLastQuestion ? '完成画像' : '下一步' }}</span>
            <svg v-if="!isLastQuestion && !isSubmitting" width="18" height="18" viewBox="0 0 20 20" fill="none">
              <path d="M5 10h10M10 5l5 5-5 5" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
            </svg>
          </button>
        </div>
      </div>
      
      <!-- 对话式画像补充入口 -->
      <div v-if="showDialogueEnhance" class="dialogue-enhance-overlay">
        <div class="dialogue-enhance-card">
          <h3>📝 通过对话完善画像</h3>
          <p class="dialogue-desc">用一句话描述你的学习需求、目标或困惑，AI将自动分析并完善你的学习画像</p>
          <textarea
            v-model="dialogueInput"
            class="dialogue-input"
            placeholder="例如：我正在准备考研英语，词汇量一般，阅读理解比较薄弱，每天能学2小时..."
            rows="4"
            :disabled="dialogueAnalyzing"
          ></textarea>
          <div class="dialogue-actions">
            <button class="dialogue-submit" @click="submitDialogue" :disabled="dialogueAnalyzing || !dialogueInput.trim()">
              <span v-if="dialogueAnalyzing">分析中...</span>
              <span v-else>✨ 开始分析</span>
            </button>
            <button class="dialogue-skip" @click="finishDialogue">跳过，直接开始</button>
          </div>
          <!-- 分析结果 -->
          <div v-if="dialogueResult" class="dialogue-result">
            <h4>分析结果</h4>
            <div v-if="dialogueResult.dimensions" class="dimension-list">
              <div v-for="(value, key) in dialogueResult.dimensions" :key="key" class="dimension-item">
                <span class="dim-key">{{ key }}</span>
                <span class="dim-value">{{ value }}</span>
              </div>
            </div>
            <div v-if="dialogueResult.subjects" class="dialogue-subjects">
              <span v-for="s in dialogueResult.subjects" :key="s" class="subject-tag">{{ s }}</span>
            </div>
            <button class="dialogue-finish" @click="finishDialogue">完成，进入学习</button>
          </div>
        </div>
      </div>

      <div class="footer-hint">
        <p>完成用户画像可以获得更个性化的学习推荐</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.quiz-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #f5f7fa 0%, #e4e8ec 100%);
  position: relative;
  overflow: hidden;
  padding: 20px;
}

.quiz-background {
  position: absolute;
  inset: 0;
  overflow: hidden;
}

.bg-circle {
  position: absolute;
  border-radius: 50%;
  opacity: 0.15;
}

.circle-1 {
  width: 500px;
  height: 500px;
  background: #667eea;
  top: -150px;
  right: -100px;
}

.circle-2 {
  width: 350px;
  height: 350px;
  background: #764ba2;
  bottom: -100px;
  left: -50px;
}

.circle-3 {
  width: 200px;
  height: 200px;
  background: #48bb78;
  top: 30%;
  left: 10%;
}

.quiz-container {
  width: 100%;
  max-width: 500px;
  position: relative;
  z-index: 1;
}

.quiz-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 32px;
}

.logo-section {
  display: flex;
  align-items: center;
  gap: 12px;
}

.quiz-logo-img {
  width: 48px;
  height: 48px;
  border-radius: 10px;
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

.skip-btn {
  padding: 10px 20px;
  background: rgba(255, 255, 255, 0.8);
  border: 2px solid #e2e8f0;
  border-radius: 20px;
  color: #718096;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
}

.skip-btn:hover {
  border-color: #667eea;
  color: #667eea;
}

.progress-section {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 32px;
}

.progress-bar {
  flex: 1;
  height: 8px;
  background: #e2e8f0;
  border-radius: 4px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 4px;
  transition: width 0.3s ease;
}

.progress-text {
  font-size: 14px;
  font-weight: 600;
  color: #667eea;
  min-width: 60px;
  text-align: right;
}

.question-card {
  background: white;
  border-radius: 24px;
  padding: 40px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.1);
}

.question-number {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;
}

.number, .total {
  font-size: 32px;
  font-weight: 700;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.divider {
  width: 30px;
  height: 2px;
  background: #e2e8f0;
}

.question-title {
  font-size: 24px;
  font-weight: 700;
  color: #2d3748;
  margin-bottom: 8px;
}

.multiple-tag {
  font-size: 14px;
  font-weight: 500;
  color: #667eea;
  background: rgba(102, 126, 234, 0.1);
  padding: 4px 10px;
  border-radius: 10px;
  margin-left: 8px;
}

.question-desc {
  font-size: 15px;
  color: #718096;
  margin-bottom: 32px;
}

.options-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 16px;
  margin-bottom: 32px;
}

.option-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 20px;
  background: #f7fafc;
  border: 2px solid transparent;
  border-radius: 16px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.option-btn:hover {
  background: #edf2f7;
  border-color: #667eea;
}

.option-btn.selected {
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.1) 0%, rgba(118, 75, 162, 0.1) 100%);
  border-color: #667eea;
}

.option-icon {
  font-size: 32px;
}

.option-label {
  font-size: 14px;
  font-weight: 500;
  color: #2d3748;
  text-align: center;
}

.option-desc {
  font-size: 12px;
  color: #a0aec0;
  text-align: center;
  margin-top: 4px;
}

.other-input-container {
  margin-bottom: 32px;
}

.other-input {
  width: 100%;
  padding: 14px 16px;
  border: 2px solid #e2e8f0;
  border-radius: 12px;
  font-size: 14px;
  color: #2d3748;
  background: #f7fafc;
  transition: all 0.3s ease;
}

.other-input:focus {
  outline: none;
  border-color: #667eea;
  background: white;
}

.other-input::placeholder {
  color: #a0aec0;
}

.nav-buttons {
  display: flex;
  gap: 16px;
}

.nav-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 16px;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
}

.nav-btn.primary {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
}

.nav-btn.primary:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(102, 126, 234, 0.4);
}

.nav-btn.primary:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.nav-btn.secondary {
  background: #f7fafc;
  color: #4a5568;
  border: 2px solid #e2e8f0;
}

.nav-btn.secondary:hover {
  background: #edf2f7;
  border-color: #a0aec0;
}

.loading-spinner {
  width: 18px;
  height: 18px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.footer-hint {
  text-align: center;
  margin-top: 24px;
}

.footer-hint p {
  font-size: 14px;
  color: #a0aec0;
}

@media (max-width: 768px) {
  .question-card {
    padding: 30px 20px;
  }

  .options-grid {
    grid-template-columns: 1fr;
  }

  .question-title {
    font-size: 20px;
  }
}

/* 对话式画像补充 */
.dialogue-enhance-overlay {
  position: fixed; inset: 0; background: rgba(0,0,0,0.5);
  display: flex; align-items: center; justify-content: center; z-index: 100;
}
.dialogue-enhance-card {
  background: white; border-radius: 16px; padding: 32px;
  max-width: 520px; width: 90%; box-shadow: 0 20px 60px rgba(0,0,0,0.15);
}
.dialogue-enhance-card h3 { margin-bottom: 8px; font-size: 1.3em; }
.dialogue-desc { color: #666; font-size: 0.9em; margin-bottom: 16px; }
.dialogue-input {
  width: 100%; padding: 12px; border: 1px solid #ddd;
  border-radius: 8px; font-size: 0.95em; resize: vertical; font-family: inherit;
}
.dialogue-input:focus { outline: none; border-color: #667eea; }
.dialogue-actions { display: flex; gap: 12px; margin-top: 12px; }
.dialogue-submit {
  flex: 1; padding: 12px 24px;
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: white; border: none; border-radius: 8px; cursor: pointer; font-size: 1em;
}
.dialogue-submit:disabled { opacity: 0.5; cursor: not-allowed; }
.dialogue-skip { padding: 12px 24px; background: #f0f0f0; border: none; border-radius: 8px; cursor: pointer; }
.dialogue-result { margin-top: 20px; padding: 16px; background: #f8f9ff; border-radius: 8px; }
.dialogue-result h4 { margin-bottom: 10px; }
.dimension-list { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.dimension-item { display: flex; justify-content: space-between; padding: 6px 10px; background: white; border-radius: 6px; font-size: 0.85em; }
.dim-key { color: #888; }
.dim-value { font-weight: 600; color: #333; }
.dialogue-subjects { margin-top: 10px; display: flex; gap: 6px; flex-wrap: wrap; }
.subject-tag { background: #667eea20; color: #667eea; padding: 4px 12px; border-radius: 20px; font-size: 0.85em; }
.dialogue-finish {
  width: 100%; padding: 10px; margin-top: 12px;
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: white; border: none; border-radius: 8px; cursor: pointer;
}
</style>