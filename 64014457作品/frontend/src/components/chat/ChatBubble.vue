<script setup>
import MarkdownRenderer from '@/components/common/MarkdownRenderer.vue';

/**
 * ChatBubble — 聊天气泡组件
 *
 * 用于对话式界面（画像构建、AI助手等场景）。
 * 支持user/assistant两种角色样式，内嵌Markdown渲染。
 */
defineProps({
  role: {
    type: String,
    default: 'assistant',
    validator: (v) => ['user', 'assistant', 'system'].includes(v)
  },
  content: {
    type: String,
    default: ''
  },
  timestamp: {
    type: String,
    default: ''
  },
  agentName: {
    type: String,
    default: ''
  },
  isLoading: {
    type: Boolean,
    default: false
  }
});

defineEmits(['retry', 'copy']);
</script>

<template>
  <div
    class="chat-bubble"
    :class="[`role-${role}`, { 'is-loading': isLoading }]"
  >
    <!-- Agent头像/标识 -->
    <div v-if="role !== 'system'" class="bubble-avatar">
      <span v-if="role === 'user'" class="avatar-icon">👤</span>
      <span v-else class="avatar-icon">🤖</span>
    </div>

    <!-- 消息内容 -->
    <div class="bubble-body">
      <!-- Agent名称（仅assistant显示） -->
      <div v-if="role === 'assistant' && agentName" class="bubble-agent-name">
        {{ agentName }}
      </div>

      <!-- Markdown内容 -->
      <div class="bubble-content">
        <MarkdownRenderer
          v-if="!isLoading"
          :content="content"
          :streaming="role === 'assistant' && !content.endsWith('\n')"
        />
        <div v-else class="loading-dots">
          <span /><span /><span />
        </div>
      </div>

      <!-- 时间戳 -->
      <div v-if="timestamp" class="bubble-time">{{ timestamp }}</div>

      <!-- 操作按钮（仅assistant消息） -->
      <div v-if="role === 'assistant' && !isLoading && content" class="bubble-actions">
        <button class="action-btn" title="复制" @click="$emit('copy')">
          📋
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chat-bubble {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  animation: bubbleIn 0.3s ease;
}
.role-user {
  flex-direction: row-reverse;
}
.role-system {
  justify-content: center;
  text-align: center;
  color: #888;
  font-size: 0.85em;
}

.bubble-avatar {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
}
.role-user .bubble-avatar {
  background: linear-gradient(135deg, #667eea, #764ba2);
}
.role-assistant .bubble-avatar {
  background: linear-gradient(135deg, #43e97b, #38f9d7);
}

.bubble-body {
  max-width: 75%;
  padding: 12px 16px;
  border-radius: 12px;
  position: relative;
}
.role-user .bubble-body {
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: white;
  border-bottom-right-radius: 4px;
}
.role-assistant .bubble-body {
  background: #f3f4f6;
  border-bottom-left-radius: 4px;
}
.role-system .bubble-body {
  background: none;
  max-width: 90%;
}

.bubble-agent-name {
  font-size: 0.75em;
  font-weight: 600;
  color: #667eea;
  margin-bottom: 4px;
}

.bubble-content {
  font-size: 0.95em;
  line-height: 1.6;
}

.bubble-time {
  font-size: 0.7em;
  color: #999;
  margin-top: 6px;
  text-align: right;
}

.bubble-actions {
  display: flex;
  gap: 4px;
  margin-top: 6px;
  opacity: 0;
  transition: opacity 0.2s;
}
.chat-bubble:hover .bubble-actions {
  opacity: 1;
}
.action-btn {
  background: none;
  border: none;
  cursor: pointer;
  font-size: 14px;
  padding: 2px 4px;
  border-radius: 4px;
}
.action-btn:hover {
  background: rgba(0,0,0,0.08);
}

.loading-dots {
  display: flex;
  gap: 4px;
  padding: 8px 0;
}
.loading-dots span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #999;
  animation: dotBounce 1.4s infinite;
}
.loading-dots span:nth-child(2) { animation-delay: 0.2s; }
.loading-dots span:nth-child(3) { animation-delay: 0.4s; }

@keyframes bubbleIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}
@keyframes dotBounce {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.3; }
  40% { transform: scale(1); opacity: 1; }
}

@media (max-width: 768px) {
  .bubble-body { max-width: 85%; }
}
</style>
