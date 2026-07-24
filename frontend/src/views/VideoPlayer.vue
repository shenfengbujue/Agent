<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { gsap } from 'gsap';
import { knowledgeApi } from '../api/index';

const router = useRouter();
const route = useRoute();

const knowledgeItem = ref(null);
const loading = ref(true);
const isPlaying = ref(false);
const currentVideoTime = ref(0);
const selectedSectionIndex = ref(0);
const duration = ref(0);
let playInterval = null;

onMounted(async () => {
  const resourceId = parseInt(route.query.resourceId);
  const sectionIndex = parseInt(route.query.sectionIndex) || 0;
  selectedSectionIndex.value = sectionIndex;
  
  if (resourceId) {
    await fetchKnowledgeItem(resourceId);
  }
  animatePage();
});

onUnmounted(() => {
  if (playInterval) {
    clearInterval(playInterval);
  }
});

const fetchKnowledgeItem = async (id) => {
  loading.value = true;
  try {
    const res = await knowledgeApi.getKnowledgeItemById(id);
    if (res.data && res.data.data) {
      knowledgeItem.value = res.data.data;
      duration.value = parseInt(res.data.data.duration?.replace('分钟', '') || '30') * 60;
      const section = res.data.data.sections?.[selectedSectionIndex.value];
      if (section?.startTime) {
        currentVideoTime.value = section.startTime;
      }
    }
  } catch (err) {
    console.error('Failed to fetch knowledge item:', err);
  }
  loading.value = false;
};

const animatePage = () => {
  gsap.fromTo('.content-header', { opacity: 0, y: -20 }, { opacity: 1, y: 0, duration: 0.6 });
  gsap.fromTo('.main-content', { opacity: 0, y: 20 }, { opacity: 1, y: 0, duration: 0.5, delay: 0.2 });
};

const goBack = () => {
  router.back();
};

const togglePlay = () => {
  isPlaying.value = !isPlaying.value;
  if (isPlaying.value) {
    playInterval = setInterval(() => {
      if (currentVideoTime.value < duration.value && isPlaying.value) {
        currentVideoTime.value++;
      } else {
        clearInterval(playInterval);
        isPlaying.value = false;
      }
    }, 1000);
  } else if (playInterval) {
    clearInterval(playInterval);
  }
};

const formatTime = (seconds) => {
  const mins = Math.floor(seconds / 60);
  const secs = seconds % 60;
  return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
};

const progressPercent = computed(() => {
  return (currentVideoTime.value / duration.value) * 100;
});

const currentSection = computed(() => {
  return knowledgeItem.value?.sections?.[selectedSectionIndex.value];
});

const jumpToSection = (index) => {
  selectedSectionIndex.value = index;
  const section = knowledgeItem.value?.sections?.[index];
  if (section?.startTime) {
    currentVideoTime.value = section.startTime;
  }
};

const playPrevSection = () => {
  if (selectedSectionIndex.value > 0) {
    jumpToSection(selectedSectionIndex.value - 1);
  }
};

const playNextSection = () => {
  if (selectedSectionIndex.value < (knowledgeItem.value?.sections?.length || 0) - 1) {
    jumpToSection(selectedSectionIndex.value + 1);
  }
};

const skipForward = () => {
  currentVideoTime.value = Math.min(currentVideoTime.value + 10, duration.value);
};

const skipBackward = () => {
  currentVideoTime.value = Math.max(currentVideoTime.value - 10, 0);
};
</script>

<template>
  <div class="video-player-page">
    <div class="content-header">
      <button class="back-btn" @click="goBack">
        <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
          <path d="M15 10H5M5 10l5-5M5 10l5 5" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
        </svg>
        返回
      </button>
      <div class="header-info">
        <h1>{{ knowledgeItem?.title || '视频播放' }}</h1>
        <span class="current-section">第 {{ selectedSectionIndex + 1 }} / {{ knowledgeItem?.sections?.length || 0 }} 节</span>
      </div>
    </div>
    
    <div class="content-wrapper">
      <main class="main-content">
        <div v-if="loading" class="loading-state">
          <div class="loader"></div>
          <p>加载中...</p>
        </div>
        
        <div v-else-if="knowledgeItem" class="video-player-view">
          <div class="video-container">
            <div class="video-player">
              <div class="video-screen">
                <div v-if="currentSection?.videoUrl" class="video-source">
                  <video 
                    ref="videoElement" 
                    :src="currentSection.videoUrl" 
                    controls 
                    class="actual-video"
                    @timeupdate="(e) => currentVideoTime = Math.floor(e.target.currentTime)"
                    @loadedmetadata="(e) => duration = Math.floor(e.target.duration)"
                    @play="isPlaying = true"
                    @pause="isPlaying = false"
                  >
                    您的浏览器不支持视频播放
                  </video>
                </div>
                <div v-else class="video-placeholder">
                  <button class="play-button" @click="togglePlay">
                    <span v-if="!isPlaying">▶</span>
                    <span v-else>⏸</span>
                  </button>
                  <div class="video-icon">🎬</div>
                  <h3>{{ currentSection?.title }}</h3>
                  <p>{{ currentSection?.content }}</p>
                </div>
              </div>
              
              <div class="video-controls-bar">
                <div class="control-left">
                  <button class="control-btn" @click="skipBackward">
                    ⏪ 10s
                  </button>
                  <button class="control-btn play-main" @click="togglePlay">
                    {{ isPlaying ? '⏸' : '▶' }}
                  </button>
                  <button class="control-btn" @click="skipForward">
                    10s ⏩
                  </button>
                </div>
                <div class="progress-control">
                  <span class="time-label">{{ formatTime(currentVideoTime) }}</span>
                  <div class="progress-bar-container">
                    <div class="progress-bar" @click="(e) => {
                      const rect = e.currentTarget.getBoundingClientRect();
                      const percent = (e.clientX - rect.left) / rect.width;
                      currentVideoTime = Math.floor(percent * duration);
                    }">
                      <div class="progress-fill" :style="{ width: progressPercent + '%' }"></div>
                      <div class="progress-thumb" :style="{ left: progressPercent + '%' }"></div>
                    </div>
                  </div>
                  <span class="time-label">{{ formatTime(duration) }}</span>
                </div>
                <div class="control-right">
                  <button class="control-btn" @click="playPrevSection" :disabled="selectedSectionIndex === 0">
                    ⏮ 上一节
                  </button>
                  <button class="control-btn" @click="playNextSection" :disabled="selectedSectionIndex === (knowledgeItem?.sections?.length || 0) - 1">
                    下一节 ⏭
                  </button>
                </div>
              </div>
            </div>
          </div>
          
          <div class="video-info">
            <div class="info-card">
              <h3>{{ currentSection?.title }}</h3>
              <p>{{ currentSection?.content }}</p>
              <div v-if="currentSection?.materials" class="materials-tag">
                📖 {{ currentSection.materials }}
              </div>
            </div>
          </div>
          
          <div class="chapter-list">
            <h3>📋 课程章节</h3>
            <div class="chapters">
              <div 
                v-for="(section, idx) in knowledgeItem?.sections" 
                :key="idx"
                :class="['chapter-item', { active: idx === selectedSectionIndex }]"
                @click="jumpToSection(idx)"
              >
                <div class="chapter-number">
                  <span v-if="idx === selectedSectionIndex">▶</span>
                  <span v-else>{{ idx + 1 }}</span>
                </div>
                <div class="chapter-info">
                  <div class="chapter-title">{{ section.title }}</div>
                  <div class="chapter-time">
                    <span v-if="section.startTime">{{ formatTime(section.startTime) }}</span>
                    <span v-if="section.videoUrl"> | 🎬 视频课程</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
        
        <div v-else class="empty-state">
          <div class="empty-icon">🎬</div>
          <h3>视频不存在</h3>
          <p>无法找到该视频资源</p>
          <button class="empty-btn" @click="goBack">
            返回
          </button>
        </div>
      </main>
    </div>
  </div>
</template>

<style scoped>
.video-player-page {
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
  margin-bottom: 4px;
}

.current-section {
  font-size: 13px;
  color: #667eea;
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

.video-player-view {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.video-container {
  background: white;
  border-radius: 20px;
  padding: 20px;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.08);
}

.video-player {
  border-radius: 12px;
  overflow: hidden;
}

.video-screen {
  position: relative;
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%);
  aspect-ratio: 16/9;
  display: flex;
  align-items: center;
  justify-content: center;
}

.actual-video {
  width: 100%;
  height: 100%;
}

.video-placeholder {
  text-align: center;
  color: white;
  padding: 40px;
}

.video-icon {
  font-size: 80px;
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

.video-controls-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  background: #2d3748;
  gap: 20px;
}

.control-left, .control-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.control-btn {
  padding: 8px 16px;
  background: rgba(255, 255, 255, 0.15);
  border: none;
  border-radius: 8px;
  color: white;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.control-btn:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.25);
}

.control-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.control-btn.play-main {
  width: 48px;
  height: 48px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 50%;
  font-size: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.progress-control {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
}

.time-label {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.8);
  min-width: 50px;
}

.progress-bar-container {
  flex: 1;
  max-width: 600px;
}

.progress-bar {
  height: 6px;
  background: rgba(255, 255, 255, 0.3);
  border-radius: 3px;
  cursor: pointer;
  position: relative;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #667eea 0%, #764ba2 100%);
  border-radius: 3px;
  transition: width 0.3s ease;
}

.progress-thumb {
  position: absolute;
  top: 50%;
  transform: translate(-50%, -50%);
  width: 16px;
  height: 16px;
  background: white;
  border-radius: 50%;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.3);
  transition: left 0.3s ease;
}

.video-info {
  background: white;
  border-radius: 20px;
  padding: 24px;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.08);
}

.info-card h3 {
  font-size: 18px;
  font-weight: 700;
  color: #2d3748;
  margin-bottom: 12px;
}

.info-card p {
  font-size: 15px;
  color: #4a5568;
  line-height: 1.8;
  margin-bottom: 16px;
}

.materials-tag {
  display: inline-block;
  padding: 8px 16px;
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.1) 0%, rgba(118, 75, 162, 0.1) 100%);
  border-radius: 8px;
  font-size: 14px;
  color: #667eea;
}

.chapter-list {
  background: white;
  border-radius: 20px;
  padding: 24px;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.08);
}

.chapter-list h3 {
  font-size: 18px;
  font-weight: 600;
  color: #2d3748;
  margin-bottom: 20px;
}

.chapters {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.chapter-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: #f7fafc;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s ease;
  border: 2px solid transparent;
}

.chapter-item:hover {
  background: #edf2f7;
}

.chapter-item.active {
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.1) 0%, rgba(118, 75, 162, 0.1) 100%);
  border-color: #667eea;
}

.chapter-number {
  width: 32px;
  height: 32px;
  background: #e2e8f0;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
  color: #4a5568;
  flex-shrink: 0;
}

.chapter-item.active .chapter-number {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.chapter-info {
  flex: 1;
  min-width: 0;
}

.chapter-title {
  font-size: 15px;
  font-weight: 600;
  color: #2d3748;
  margin-bottom: 4px;
}

.chapter-time {
  font-size: 13px;
  color: #718096;
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
  
  .video-controls-bar {
    flex-wrap: wrap;
    padding: 12px;
  }
  
  .control-left, .control-right {
    flex-wrap: wrap;
  }
  
  .progress-control {
    order: 3;
    width: 100%;
  }
  
  .video-container {
    padding: 12px;
  }
}
</style>