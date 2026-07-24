import { ref, onUnmounted } from 'vue';

/**
 * useSSE — SSE (Server-Sent Events) 流式连接管理 Composable
 *
 * 封装 EventSource/fetch ReadableStream 逻辑，支持:
 * - 自动重连（指数退避，最多3次）
 * - 多事件类型回调：token / agent_step / status / error / complete
 * - AbortController 取消
 * - 组件卸载时自动断开连接
 *
 * 用法:
 *   const { isStreaming, error, connect, abort } = useSSE();
 *   connect('/api/agents/process-query-stream', {
 *     body: JSON.stringify({ query: '...' }),
 *     onToken: (agent, token) => { ... },
 *     onAgentStep: (agent, data) => { ... },
 *     onComplete: (result) => { ... },
 *     onError: (err) => { ... }
 *   });
 *
 * @returns {{ isStreaming, error, connect, abort }}
 */
export function useSSE() {
  const isStreaming = ref(false);
  const error = ref(null);
  const retryCount = ref(0);
  const MAX_RETRIES = 3;

  let abortController = null;
  let reader = null;

  /**
   * 连接到SSE端点
   *
   * @param {string} url - SSE端点URL
   * @param {object} options
   * @param {object} options.body - POST请求体(JSON字符串)
   * @param {function} options.onToken - 逐token回调 (agentName, token) => void
   * @param {function} options.onAgentStep - Agent步骤完成回调 (agentName, data) => void
   * @param {function} options.onStatus - 状态更新回调 (phase, message) => void
   * @param {function} options.onComplete - 流完成回调 (finalResult) => void
   * @param {function} options.onError - 错误回调 (error) => void
   */
  const connect = async (url, options = {}) => {
    const {
      body,
      onToken,
      onAgentStep,
      onStatus,
      onComplete,
      onError
    } = options;

    abortController = new AbortController();
    isStreaming.value = true;
    error.value = null;
    retryCount.value = 0;

    const token = localStorage.getItem('token');

    try {
      const response = await fetch(url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': token ? `Bearer ${token}` : ''
        },
        body: body,
        signal: abortController.signal
      });

      if (!response.ok) {
        throw new Error(`SSE连接失败: HTTP ${response.status}`);
      }

      reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        // 保留最后一个可能不完整的行
        buffer = lines.pop() || '';

        for (const line of lines) {
          if (!line.trim()) continue;

          try {
            const data = JSON.parse(line);

            switch (data.type) {
              case 'stream':
                if (onToken && data.agent && data.token) {
                  onToken(data.agent, data.token);
                }
                break;

              case 'agent_step':
                if (onAgentStep && data.agent && data.data) {
                  onAgentStep(data.agent, data.data);
                }
                // 也兼容旧格式: { agent: 'xxx', data: {...} }
                if (onAgentStep && data.agent && !data.type) {
                  onAgentStep(data.agent, data.data);
                }
                break;

              case 'status':
                if (onStatus && data.phase) {
                  onStatus(data.phase, data.message || '');
                }
                break;

              case 'error':
                error.value = data.message || '未知错误';
                if (onError) onError(data.message);
                break;

              case 'complete':
                isStreaming.value = false;
                if (onComplete) onComplete(data.result);
                return;

              default:
                // 未识别的事件类型，尝试作为agent_step处理
                if (onToken && data.token) {
                  onToken(data.agent || 'unknown', data.token);
                }
            }
          } catch (parseError) {
            // 跳过无法解析的行（可能是SSE注释或心跳）
            if (line.startsWith(':') || line.startsWith('data:')) {
              continue;
            }
          }
        }
      }

      // 处理buffer中剩余的数据
      if (buffer.trim()) {
        try {
          const data = JSON.parse(buffer);
          if (data.type === 'complete' && onComplete) {
            onComplete(data.result);
          }
        } catch (e) {
          // 忽略最后的不完整数据
        }
      }

      isStreaming.value = false;

    } catch (err) {
      if (err.name === 'AbortError') {
        isStreaming.value = false;
        return;
      }

      console.error('SSE error:', err);
      error.value = err.message;

      // 自动重连（指数退避）
      if (retryCount.value < MAX_RETRIES) {
        retryCount.value++;
        const delay = Math.min(1000 * Math.pow(2, retryCount.value), 8000);
        console.log(`SSE重连 ${retryCount.value}/${MAX_RETRIES}，等待${delay}ms...`);
        await new Promise(resolve => setTimeout(resolve, delay));
        return connect(url, options);
      }

      isStreaming.value = false;
      if (onError) onError(err);
    }
  };

  /**
   * 断开SSE连接
   */
  const abort = () => {
    if (abortController) {
      abortController.abort();
      abortController = null;
    }
    if (reader) {
      try { reader.cancel(); } catch (e) { /* 忽略 */ }
      reader = null;
    }
    isStreaming.value = false;
  };

  // 组件卸载时自动断开连接
  onUnmounted(() => {
    abort();
  });

  return {
    isStreaming,
    error,
    connect,
    abort
  };
}
