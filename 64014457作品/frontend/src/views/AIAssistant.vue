<script setup>
import { ref, onMounted, nextTick, computed } from 'vue';
import { useRoute } from 'vue-router';

const route = useRoute();
defineOptions({ name: 'AIAssistant' });
import { gsap } from 'gsap';
import { agentApi, coordinatorApi, profileApi, knowledgeApi, chatApi } from '../api/index';
import { Network } from 'vis-network';
import { DataSet } from 'vis-data';
import { marked } from 'marked';

// Agent配置
const multiAgentMode = ref(true); // 多Agent协同模式开关
const primaryAgent = {
  id: 1,
  name: '学习助手',
  role: '学习辅导',
  description: '帮助您制定学习计划，解答学习问题',
  icon: '📚'
};

// Agent列表（多Agent协同中的6个智能体）
const agentList = [
  { name: '统筹解析智能体', icon: '🔍', description: '解析学习需求' },
  { name: '路径规划智能体', icon: '🗺️', description: '规划学习路径' },
  { name: '知识库检索智能体', icon: '📖', description: '检索知识内容' },
  { name: '联网搜索智能体', icon: '🌐', description: '拓展阅读推荐' },
  { name: '图生成智能体', icon: '🧠', description: '生成思维导图' },
  { name: '练习题生成智能体', icon: '✍️', description: '生成练习题' },
  { name: '格式化总结智能体', icon: '📋', description: '整合学习方案' }
];

const parseMarkdown = (content) => {
  if (!content) return '';
  return marked(content, { breaks: true, gfm: true });
};

const messages = ref([]);
const inputMessage = ref('');
const isProcessing = ref(false);
const agentSteps = ref([]);
const structuredResult = ref(null);
const streamingContent = ref('');
function authHeader() { return { Authorization: `Bearer ${localStorage.getItem('token')}` }; }
function welcomeMsg() { return { role: 'assistant', content: '你好！告诉我你想学什么，我会协调多个AI智能体为你生成专属学习方案！', agent: '学习助手', isMultiAgent: false }; }

function loadMessagesFromHistory(history) {
  if (!history?.length) { messages.value = [welcomeMsg()]; return; }
  messages.value = history.map((msg, idx) => {
    const isMulti = (msg.agentName || msg.agent) === '多智能体协同';
    const isLast = idx === history.length - 1;
    let ct = msg.content || '';
    let sr = null;
    if (isMulti && ct.includes('<!--STRUCTURED_DATA-->')) {
      try {
        const p = ct.split('<!--STRUCTURED_DATA-->');
        ct = p[0];
        sr = JSON.parse(p[1]);
      } catch(e) {}
    }
    return { role: msg.role, content: ct, agent: msg.agentName || msg.agent || '学习助手', createdAt: msg.createdAt, isMultiAgent: isMulti, collapsed: !isLast, activeTab: 'summary', structuredResult: sr };
  });
}
// activeTab已移至每条message内部，不再全局共享

const loadChatHistory = async () => {
  try {
    // 同时加载多Agent协同(agentId=0)和旧单Agent(agentId=1)的历史
    const [resMulti, resSingle] = await Promise.all([
      agentApi.getHistory(0).catch(() => ({ data: { data: [] } })),
      agentApi.getHistory(1).catch(() => ({ data: { data: [] } }))
    ]);
    const multiHistory = resMulti.data?.data || [];
    const singleHistory = resSingle.data?.data || [];

    // 合并并按时间排序
    const allHistory = [...multiHistory, ...singleHistory].sort(
      (a, b) => new Date(a.createdAt || 0) - new Date(b.createdAt || 0)
    );

    if (allHistory.length > 0) {
      messages.value = allHistory.map((msg, idx, arr) => {
        const isMulti = (msg.agentName || msg.agent) === '多智能体协同';
        // 只有最后一条消息默认展开，历史消息折叠
        const isLast = idx === arr.length - 1;
        let ct = msg.content || '';
        let sr = null;
        if (isMulti && ct.includes('<!--STRUCTURED_DATA-->')) {
          try { const p = ct.split('<!--STRUCTURED_DATA-->'); ct = p[0]; sr = JSON.parse(p[1]); } catch(e) {}
        }
        return {
          role: msg.role,
          content: ct,
          agent: msg.agentName || msg.agent || '多智能体协同',
          createdAt: msg.createdAt,
          isMultiAgent: isMulti,
          structuredResult: sr,
          activeTab: 'summary',
          collapsed: !isLast && msg.role === 'assistant'
        };
      });
      const lastMulti = [...messages.value].reverse().find(m => m.structuredResult);
      if (lastMulti) { structuredResult.value = lastMulti.structuredResult; }
    } else {
      // 即使历史为空，尝试从localStorage恢复上次结果
      const saved = loadSavedStructuredResult('');
      messages.value = [{
        role: 'assistant',
        content: saved ? '已恢复上次生成的学习方案' : '你好！我是智学未来——高等教育个性化多智能体学习系统。告诉我你想学什么，我会协调多个AI智能体为你生成专属学习方案！',
        agent: '学习助手',
        isMultiAgent: !!saved,
        activeTab: 'summary',
        structuredResult: saved || null
      }];
      if (saved) structuredResult.value = saved;
    }
  } catch (e) {
    const saved = loadSavedStructuredResult('');
    messages.value = [{
      role: 'assistant',
      content: saved ? '已恢复上次生成的学习方案' : '你好！我是智学未来——高等教育个性化多智能体学习系统。告诉我你想学什么，我会协调多个AI智能体为你生成专属学习方案！',
      agent: '学习助手',
      isMultiAgent: !!saved,
      activeTab: 'summary',
      structuredResult: saved || null
    }];
    if (saved) structuredResult.value = saved;
  }
};

const sendMessage = async () => {
  const msg = inputMessage.value.trim();
  if (!msg || isProcessing.value) return;

  messages.value.push({ role: 'user', content: msg });
  inputMessage.value = '';
  isProcessing.value = true;
  structuredResult.value = null;
  // 立即显示Agent状态面板
  agentSteps.value = [
    { agent: '统筹解析智能体', icon: '🔍', status: 'pending', message: '等待中...' },
    { agent: '路径规划智能体', icon: '🗺️', status: 'pending', message: '等待中...' },
    { agent: '知识库检索智能体', icon: '📖', status: 'pending', message: '等待中...' },
    { agent: '联网搜索智能体', icon: '🌐', status: 'pending', message: '等待中...' },
    { agent: '图生成智能体', icon: '🧠', status: 'pending', message: '等待中...' },
    { agent: '练习题生成智能体', icon: '✍️', status: 'pending', message: '等待中...' },
    { agent: '格式化总结智能体', icon: '📋', status: 'pending', message: '等待中...' }
  ];

  scrollToBottom();

  // 增量模式：在query后追加上下文
  let fullQuery = msg;
  if (route.query.continueBase) {
    const existingTitle = route.query.title || '已有内容';
    const existingContent = route.query.content || '';
    fullQuery = `【继续完善】我正在学习"${existingTitle}"。以下是已有内容摘要：${existingContent.substring(0, 300)}。请基于此生成更多补充的知识模块和练习题（不要重复已有内容）。我的额外需求：${msg}`;
  }

  if (multiAgentMode.value) {
    await sendMultiAgentQuery(fullQuery);
  } else {
    await sendSimpleChat(fullQuery);
  }
};

const sendMultiAgentQuery = async (query) => {
  let finalResult = null;
  streamingContent.value = '';
  // 立即创建带空structuredResult的消息 — tab框架从一开始就可见
  const streamMsgIdx = messages.value.length;
  messages.value.push({
    role: 'assistant',
    content: '',
    agent: '多智能体协同',
    isMultiAgent: true,
    streaming: true,
    activeTab: 'summary',
    structuredResult: { response: '⏳ 正在生成学习方案...' }
  });
  const accumulatedData = {};
  // 实时更新消息的structuredResult
  const updateMsgSR = (key, value) => {
    const msg = messages.value[streamMsgIdx];
    if (!msg) return;
    if (!msg.structuredResult) msg.structuredResult = {};
    msg.structuredResult = { ...msg.structuredResult, [key]: value };
  };
  const abortController = new AbortController();
  const token = localStorage.getItem('token');

  try {
    // 使用SSE流式接口获取实时进度
    const response = await fetch('/api/agents/process-query-stream', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': token ? `Bearer ${token}` : ''
      },
      body: JSON.stringify({ query }),
      signal: abortController.signal
    });

    if (!response.ok) throw new Error(`SSE连接失败: HTTP ${response.status}`);

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    let summaryStreaming = '';

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split('\n');
      buffer = lines.pop() || '';

      for (const line of lines) {
        if (!line.trim()) continue;
        try {
          const event = JSON.parse(line);

          if (event.type === 'stream' && event.agent) {
            const token = (event.token && event.token !== 'null') ? event.token : '';
            streamingContent.value += token;
            // 格式化总结智能体的流式输出 → 直接显示在总览tab
            if (event.agent === '格式化总结智能体') {
              summaryStreaming += token;
              updateMsgSR('response', summaryStreaming);
            }
            const stepIdx = agentSteps.value.findIndex(s => s.agent === event.agent);
            if (stepIdx >= 0 && agentSteps.value[stepIdx].status !== 'success') {
              agentSteps.value[stepIdx] = { ...agentSteps.value[stepIdx], status: 'running', message: '生成中...' };
            }
            scrollToBottom();
          } else if (event.type === 'complete') {
            finalResult = { ...event, ...accumulatedData };
          } else if (event.agent && event.agent !== 'unknown') {
            const agentName = event.agent;
            const stepIdx = agentSteps.value.findIndex(s => s.agent === agentName);
            const isSuccess = event.status !== 'error';

            const camelKey = {
              '统筹解析智能体': 'requirements',
              '路径规划智能体': 'learningPath',
              '知识库检索智能体': 'knowledge',
              '联网搜索智能体': 'webSearch',
              '图生成智能体': 'graph',
              '练习题生成智能体': 'exercises',
              '格式化总结智能体': 'summary'
            }[agentName];

            if (camelKey && event.data) {
              const agentData = (event.data.data && typeof event.data.data === 'object') ? event.data.data : event.data;
              accumulatedData[camelKey] = agentData;
              // 立刻更新消息structuredResult — 对应tab即时可见
              updateMsgSR(camelKey, agentData);
              // 格式化总结完成时用完整markdown替换流式文本
              if (camelKey === 'summary' && event.message && event.message.length > summaryStreaming.length) {
                updateMsgSR('response', event.message);
              }
            }

            if (stepIdx >= 0) {
              agentSteps.value[stepIdx] = {
                ...agentSteps.value[stepIdx],
                status: isSuccess ? 'success' : 'error',
                message: isSuccess ? `完成 (${event.durationMs || 0}ms)` : (event.message || '执行失败')
              };
              for (let i = stepIdx + 1; i < agentSteps.value.length; i++) {
                if (agentSteps.value[i].status === 'pending') {
                  agentSteps.value[i] = { ...agentSteps.value[i], status: 'running', message: '处理中...' };
                  break;
                }
              }
            }
          }
        } catch (e) {
          // 跳过无法解析的行
        }
      }
    }

    // SSE流结束，有任一Agent数据即可展示
    if (!finalResult) {
      const fallbackResp = await coordinatorApi.processQuery(query);
      finalResult = fallbackResp.data;
    }

    // 标记所有剩余pending/running的步骤为完成
    agentSteps.value = agentSteps.value.map(s => {
      if (s.status === 'pending' || s.status === 'running') {
        return { ...s, status: 'success', message: '完成' };
      }
      return s;
    });

    // 填充完整结构化结果
    structuredResult.value = {
      mode: finalResult.mode,
      requirements: finalResult.requirements,
      response: finalResult.response || summaryStreaming,
      learningPath: finalResult.learningPath,
      knowledge: finalResult.knowledge,
      webSearch: finalResult.webSearch,
      graph: finalResult.graph,
      exercises: finalResult.exercises,
      summary: finalResult.summary,
      executionSteps: finalResult.executionSteps
    };

    // 完成时更新消息（保留structuredResult，只去掉streaming标记）
    const subject = finalResult.requirements?.subject || '学习方案';
    const level = finalResult.requirements?.level || '';
    const msg = messages.value[streamMsgIdx];
    if (msg) {
      msg.content = `已为您生成 **${subject}**${level ? '(' + level + ')' : ''}的专属学习方案，查看下方详情`;
      msg.streaming = false;
      msg.structuredResult = JSON.parse(JSON.stringify(structuredResult.value));
    }

    try {
      const runId = finalResult.runId || Date.now().toString(36);
      localStorage.setItem(`agent_result_${runId}`, JSON.stringify(structuredResult.value));
      localStorage.setItem('agent_result_user', JSON.parse(localStorage.getItem('user') || '{}').id);
    } catch (e) { /* ignore */ }

    setTimeout(() => {
      const nodes = structuredResult.value?.graph?.nodes;
      const edges = structuredResult.value?.graph?.edges;
      if (nodes?.length) renderKnowledgeGraph(nodes, edges || []);
    }, 500);

  } catch (err) {
    console.error('Multi-agent query failed:', err);
    if (err.name !== 'AbortError') {
      agentSteps.value = agentSteps.value.map(s => ({ ...s, status: 'error', message: '执行失败' }));
      if (messages.value[streamMsgIdx]) {
        messages.value[streamMsgIdx].content = '抱歉，处理时遇到了问题。请稍后重试。';
        messages.value[streamMsgIdx].streaming = false;
      }
    }
  } finally {
    isProcessing.value = false;
    await nextTick();
    scrollToBottom();
  }
};

const sendSimpleChat = async (msg) => {
  const token = localStorage.getItem('token');
  // 先插入空消息占位
  const msgIdx = messages.value.length;
  messages.value.push({
    role: 'assistant',
    content: '',
    agent: '学习助手',
    isMultiAgent: false,
    streaming: true
  });
  scrollToBottom();

  try {
    const response = await fetch(`/api/agents/${primaryAgent.id}/chat-stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': token ? `Bearer ${token}` : ''
      },
      body: JSON.stringify({ message: msg })
    });

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      // 按SSE格式分割：data: ...\n\n
      const lines = buffer.split('\n\n');
      buffer = lines.pop(); // 保留不完整的最后一段
      for (const line of lines) {
        if (!line.startsWith('data: ')) continue;
        const payload = line.substring(6);
        if (payload === '[DONE]') continue;
        try {
          const json = JSON.parse(payload);
          if (json.token) {
            messages.value[msgIdx].content += json.token;
          }
          if (json.error) {
            messages.value[msgIdx].content = '抱歉，服务暂时不可用。';
          }
        } catch (e) { /* skip parse errors */ }
      }
      scrollToBottom();
    }
    messages.value[msgIdx].streaming = false;

  } catch (err) {
    messages.value[msgIdx].content = '抱歉，服务暂时不可用。';
    messages.value[msgIdx].streaming = false;
  } finally {
    isProcessing.value = false;
    await nextTick();
    scrollToBottom();
  }
};

const scrollToBottom = () => {
  nextTick(() => {
    const container = document.querySelector('.chat-messages');
    if (container) container.scrollTop = container.scrollHeight;
  });
};

const getStepStatusClass = (status) => {
  return {
    running: 'step-running',
    success: 'step-success',
    error: 'step-error',
    pending: 'step-pending',
    idle: 'step-idle'
  }[status] || '';
};

const getStepStatusIcon = (status) => {
  return {
    running: '⏳',
    success: '✅',
    error: '❌',
    pending: '⬜',
    idle: '💤'
  }[status] || '⬜';
};

// 知识图谱渲染（vis.js）
let graphNetwork = null;
const renderKnowledgeGraph = async (nodesData, edgesData) => {
  await nextTick();
  const container = document.getElementById('knowledge-graph-container');
  if (!container || !nodesData?.length) return;

  if (graphNetwork) { graphNetwork.destroy(); graphNetwork = null; }

  const nodeTypeColors = {
    TOPIC: { bg: '#667eea', border: '#5a67d8' },
    MODULE: { bg: '#48bb78', border: '#38a169' },
    CONCEPT: { bg: '#ed8936', border: '#dd6b20' },
    SKILL: { bg: '#9f7aea', border: '#805ad5' }
  };

  const nodes = new DataSet(nodesData.map(n => ({
    id: n.id,
    label: n.label,
    title: `<b>${n.label}</b><br>类型: ${n.nodeType || 'CONCEPT'}<br>难度: ${n.difficulty || '基础'}<br>${n.description || ''}`,
    color: nodeTypeColors[n.nodeType] || nodeTypeColors.CONCEPT,
    font: { size: 14, color: '#2d3748' },
    shape: n.nodeType === 'TOPIC' ? 'star' : n.nodeType === 'SKILL' ? 'diamond' : 'dot',
    size: n.nodeType === 'TOPIC' ? 40 : n.nodeType === 'MODULE' ? 30 : 20,
    borderWidth: 2
  })));

  const relationColors = {
    PREREQUISITE: { color: '#e53e3e', dashes: false, width: 2 },
    CONTAINS: { color: '#667eea', dashes: false, width: 2 },
    RELATED_TO: { color: '#a0aec0', dashes: true, width: 1 },
    NEXT: { color: '#48bb78', dashes: false, width: 2 }
  };

  const edges = new DataSet(edgesData.map((e, i) => ({
    id: i,
    from: e.source,
    to: e.target,
    label: e.label || '',
    arrows: 'to',
    ...(relationColors[e.relationType] || relationColors.RELATED_TO)
  })));

  graphNetwork = new Network(container, { nodes, edges }, {
    physics: { solver: 'forceAtlas2Based', stabilization: { iterations: 100 } },
    interaction: { hover: true, tooltipDelay: 200, zoomView: true, dragView: true },
    edges: { smooth: { type: 'curvedCW', roundness: 0.2 }, font: { size: 10 } }
  });
};

// 保存到资源中心
// 一键保存：将本次多Agent生成的全部内容合并为一个完整资源
const saveAllToResourceCenter = async (msgStructuredResult) => {
  // 优先使用传入的消息级数据，避免全局变量被后续操作覆盖
  const sr = msgStructuredResult || structuredResult.value;
  if (!sr) return;

  const subject = sr.requirements?.subject || '学习方案';
  const title = `${subject} - 完整学习方案`;
  const difficulty = sr.requirements?.level || '基础';
  const totalDays = sr.learningPath?.stages
    ?.reduce((sum, s) => sum + (s.days || 0), 0) || 0;
  const duration = totalDays ? `${totalDays}天` : '';

  // content从##开始，不重复#标题
  let content = '';
  const stages = sr.learningPath?.stages;
  if (stages?.length) {
    content += `## 🗺️ 学习路径\n\n`;
    stages.forEach((s, i) => {
      content += `**阶段${i + 1}：${s.name}**（${s.difficulty}，${s.days}天）\n`;
      content += `- 目标：${s.goal}\n`;
      content += `- 模块：${(s.modules || []).join('、')}\n\n`;
    });
  }

  // 知识模块
  const modules = sr.knowledge;
  if (Array.isArray(modules)) {
    content += `## 📖 知识模块\n\n`;
    modules.forEach((mod, i) => {
      content += `### ${i + 1}. ${mod.name}\n\n`;
      content += `**基础知识**\n${mod.basicKnowledge || ''}\n\n`;
      if (mod.corePoints?.length) {
        content += `**核心重点**\n${mod.corePoints.map(p => '- ' + p).join('\n')}\n\n`;
      }
      if (mod.commonMistakes?.length) {
        content += `**⚠️ 易错混淆**\n${mod.commonMistakes.map(m => '- ' + m).join('\n')}\n\n`;
      }
      content += `---\n\n`;
    });
  }

  // 练习题
  const exercises = sr.exercises?.exercises;
  if (exercises?.length) {
    content += `## ✍️ 练习题\n\n`;
    exercises.forEach((ex, i) => {
      content += `**第${i + 1}题**（${ex.type} | ${ex.difficulty}）\n`;
      content += `${ex.question}\n\n`;
      if (ex.options?.length) {
        content += ex.options.map((o, oi) => `${String.fromCharCode(65 + oi)}. ${o}`).join('\n') + '\n\n';
      }
      content += `<details><summary>答案与解析</summary>\n\n**答案：**${ex.answer}\n\n**解析：**${ex.analysis}\n\n</details>\n\n`;
      content += `---\n\n`;
    });
  }

  // 思维导图
  if (sr.graph?.textOutline) {
    content += `## 🧠 思维导图大纲\n\n\`\`\`\n${sr.graph.textOutline}\n\`\`\`\n\n`;
  }

  // 拓展阅读
  const resources = sr.webSearch?.resources;
  if (resources?.length) {
    content += `## 🌐 拓展阅读\n\n`;
    resources.forEach(r => {
      content += `- **${r.title}**（${r.type} | ${r.difficulty}）：${r.summary}\n`;
    });
  }

  // 保存
  try {
    // 保存结构化数据用于互动展示（习题可做、图谱可交互）
    const planData = JSON.stringify({
      requirements: sr.requirements,
      learningPath: sr.learningPath,
      knowledge: sr.knowledge,
      graph: sr.graph,
      exercises: sr.exercises,
      webSearch: sr.webSearch,
      summary: sr.summary
    });

    const res = await knowledgeApi.saveGenerated({
      title, content, type: 'article', category: 'AI生成',
      difficulty, duration, planData
    });
    const code = res.data?.code;
    if (code === 200 || code === 0) {
      alert(`✅ 完整学习方案已保存到资源中心！\n可在"资源中心 → AI生成内容"中查看和完善。`);
    } else if (res.data?.code === 401) {
      alert('保存失败：登录已过期，请重新登录');
    } else {
      alert('保存失败: ' + (res.data?.message || res.data?.msg || '请检查网络连接'));
      console.error('Save error details:', res.data);
    }
  } catch (e) {
    console.error('保存异常:', e);
    alert('保存失败：' + (e.response?.data?.message || e.message || '请重试'));
  }
};

// 从localStorage恢复结构化结果（仅恢复当前用户的数据）
const loadSavedStructuredResult = (contentHint) => {
  try {
    const currentUser = JSON.parse(localStorage.getItem('user') || '{}');
    const currentUserId = currentUser.id;
    const savedUserId = localStorage.getItem('agent_result_user');
    // 必须属于当前用户，否则不恢复
    if (!currentUserId || String(savedUserId) !== String(currentUserId)) return null;
    const keys = Object.keys(localStorage).filter(k => k.startsWith('agent_result_') && k !== 'agent_result_user');
    if (keys.length === 0) return null;
    keys.sort((a, b) => b.localeCompare(a));
    for (const key of keys) {
      try {
        const data = JSON.parse(localStorage.getItem(key));
        if (data && data.response && data.response.length > 10) {
          return data;
        }
      } catch (e) { /* skip corrupt entries */ }
    }
  } catch (e) { /* ignore */ }
  return null;
};

// 删除单条对话
const deleteSingleHistory = async (agentId, index) => {
  try { await chatApi.deleteHistory(agentId); loadChatHistory(); } catch(e) { console.error(e); }
};
// 清空全部历史
const clearAllHistory = async () => {
  if (!confirm('确定要清空所有对话历史吗？此操作不可恢复。')) return;
  try {
    await chatApi.clearAllHistory();
    messages.value = [];
    localStorage.removeItem('agent_result_1');
  } catch(e) { console.error(e); }
};

onMounted(async () => {
  // 初始化Agent状态面板（始终显示）
  agentSteps.value = [
    { agent: '统筹解析智能体', icon: '🔍', status: 'idle', message: '就绪' },
    { agent: '路径规划智能体', icon: '🗺️', status: 'idle', message: '就绪' },
    { agent: '知识库检索智能体', icon: '📖', status: 'idle', message: '就绪' },
    { agent: '联网搜索智能体', icon: '🌐', status: 'idle', message: '就绪' },
    { agent: '图生成智能体', icon: '🧠', status: 'idle', message: '就绪' },
    { agent: '练习题生成智能体', icon: '✍️', status: 'idle', message: '就绪' },
    { agent: '格式化总结智能体', icon: '📋', status: 'idle', message: '就绪' }
  ];

  // 尝试加载历史记录，无历史则显示欢迎消息
  await loadChatHistory();

  // 检测是否从资源中心"继续完善"跳转过来
  if (route.query.continueBase) {
    const existingTitle = route.query.title || '已有内容';
    inputMessage.value = `请为"${existingTitle}"生成更多补充内容（知识模块、练习题、思维导图等），不要与已有内容重复。`;
  }
});
</script>

<template>
  <div class="ai-assistant-page">

    <!-- 多Agent状态面板 -->
    <div v-if="agentSteps.length > 0" class="agent-panel">
      <div class="panel-header">
        <span>🤖 多智能体协同处理</span>
        <div class="panel-actions">
          <button class="mode-toggle" @click="multiAgentMode = !multiAgentMode">
            {{ multiAgentMode ? '多Agent模式' : '简单模式' }}
          </button>
          <button class="clear-btn" @click="clearAllHistory" title="清空当前对话">🗑️</button>
        </div>
      </div>
      <div class="agent-steps">
        <div
          v-for="step in agentSteps"
          :key="step.agent"
          :class="['step-item', getStepStatusClass(step.status)]"
        >
          <span class="step-icon">{{ step.icon }}</span>
          <span class="step-agent">{{ step.agent }}</span>
          <span class="step-status">{{ getStepStatusIcon(step.status) }} {{ step.message }}</span>
        </div>
      </div>
    </div>

    <!-- 聊天消息区 -->
    <div class="chat-messages" ref="chatContainer">
      <div
        v-for="(message, index) in messages"
        :key="index"
        :class="['message', message.role === 'user' ? 'user-message' : 'assistant-message', { collapsed: message.collapsed }]"
      >
        <!-- 折叠的历史消息 -->
        <div v-if="message.collapsed" class="collapsed-bar" @click="message.collapsed = false">
          <span class="collapsed-dot"></span>
          <span class="collapsed-summary">{{ message.content.substring(0, 40) }}{{ message.content.length > 40 ? '...' : '' }}</span>
          <span class="collapsed-time">{{ message.createdAt ? new Date(message.createdAt).toLocaleString('zh-CN', {month:'numeric',day:'numeric',hour:'2-digit',minute:'2-digit'}) : '' }}</span>
          <span class="collapsed-expand">点击展开 ▼</span>
        </div>

        <template v-else>
        <div class="message-avatar">{{ message.role === 'user' ? '👤' : '🤖' }}</div>
        <div class="message-content">
          <div class="message-header">
            <span class="message-agent" v-if="message.agent">{{ message.agent }}<span v-if="message.isMultiAgent" class="multi-badge">多Agent协同</span></span>
            <span class="message-time" v-if="message.createdAt">{{ new Date(message.createdAt).toLocaleString('zh-CN', {month:'numeric',day:'numeric',hour:'2-digit',minute:'2-digit'}) }}</span>
            <button v-if="message.isMultiAgent && index < messages.length-1" class="collapse-btn" @click="message.collapsed = true" title="折叠">▲</button>
          </div>
          <div v-if="!message.structuredResult" class="message-text" v-html="parseMarkdown(message.content)"></div>
          <span v-if="message.streaming" class="streaming-cursor">▌</span>

          <!-- 多Agent结构化结果 -->
          <div v-if="message.isMultiAgent && message.structuredResult" class="structured-result">
            <!-- Tab切换 -->
            <div class="result-tabs">
              <button
                v-for="tab in ['summary', 'path', 'knowledge', 'graph', 'exercises', 'resources']"
                :key="tab"
                :class="['result-tab', { active: (message.activeTab || 'summary') === tab }]"
                @click="message.activeTab = tab; if (tab === 'graph') { setTimeout(() => { const nodes = message.structuredResult?.graph?.nodes; const edges = message.structuredResult?.graph?.edges; if (nodes?.length) renderKnowledgeGraph(nodes, edges || []); }, 100); }"
              >
                {{ {
                  summary: '📋 总览',
                  path: '🗺️ 学习路径',
                  knowledge: '📖 知识模块',
                  graph: '🧠 思维导图',
                  exercises: '✍️ 练习题',
                  resources: '🌐 拓展阅读'
                }[tab] }}
              </button>
            </div>

            <!-- 总览 -->
            <div v-show="(message.activeTab || 'summary') === 'summary'" class="tab-content">
              <div class="save-all-bar">
                <button class="save-all-btn" @click="saveAllToResourceCenter(message.structuredResult)">💾 保存完整方案到资源中心</button>
              </div>
              <div v-if="message.structuredResult.response" class="content-section" v-html="parseMarkdown(message.structuredResult.response)"></div>
              <div v-if="message.structuredResult.requirements" class="requirement-tags">
                <span class="req-tag" v-if="message.structuredResult.requirements.subject">
                  📚 {{ message.structuredResult.requirements.subject }}
                </span>
                <span class="req-tag" v-if="message.structuredResult.requirements.level">
                  📊 {{ message.structuredResult.requirements.level }}
                </span>
                <span class="req-tag" v-if="message.structuredResult.requirements.goal">
                  🎯 {{ message.structuredResult.requirements.goal }}
                </span>
              </div>
            </div>

            <!-- 学习路径 -->
            <div v-show="(message.activeTab || 'summary') === 'path'" class="tab-content">
              <div v-if="message.structuredResult.learningPath" class="content-section">
                <div v-if="message.structuredResult.learningPath?.stages" class="path-timeline">
                  <div
                    v-for="(stage, i) in message.structuredResult.learningPath?.stages"
                    :key="i"
                    class="path-stage"
                  >
                    <div class="stage-number">{{ i + 1 }}</div>
                    <div class="stage-content">
                      <h4>{{ stage.name }} <span class="difficulty-badge">{{ stage.difficulty }}</span></h4>
                      <p>{{ stage.goal }}</p>
                      <div class="stage-meta">
                        <span>⏱️ {{ stage.days }}天 · {{ stage.dailyMinutes || '?' }}分钟/天</span>
                      </div>
                      <div class="stage-modules">
                        <span v-for="mod in stage.modules" :key="mod" class="module-tag">{{ mod }}</span>
                      </div>
                    </div>
                  </div>
                </div>
                <div v-else class="content-section" v-html="parseMarkdown(message.structuredResult.learningPath.markdownContent || '暂无学习路径数据')"></div>
              </div>
            </div>

            <!-- 知识模块 — knowledge直接就是模块数组 [{name, basicKnowledge, ...}] -->
            <div v-show="(message.activeTab || 'summary') === 'knowledge'" class="tab-content">
              <div v-if="Array.isArray(message.structuredResult.knowledge) && message.structuredResult.knowledge.length" class="content-section">
                <div v-for="(mod, i) in message.structuredResult.knowledge" :key="i" class="knowledge-card">
                  <div class="knowledge-card-header">
                    <span class="knowledge-num">{{ i + 1 }}</span>
                    <h4>📖 {{ mod.name }}</h4>
                  </div>
                  <div class="knowledge-section">
                    <h5>📘 基础知识</h5>
                    <div class="knowledge-text" v-html="parseMarkdown(mod.basicKnowledge || '暂无')"></div>
                  </div>
                  <div class="knowledge-section">
                    <h5>🎯 核心重点</h5>
                    <ul class="knowledge-list">
                      <li v-for="pt in (mod.corePoints || [])" :key="pt">{{ pt }}</li>
                    </ul>
                  </div>
                  <div class="knowledge-section mistakes">
                    <h5>⚠️ 易错混淆</h5>
                    <ul class="knowledge-list">
                      <li v-for="ms in (mod.commonMistakes || [])" :key="ms">{{ ms }}</li>
                    </ul>
                  </div>
                </div>
              </div>
              <div v-else class="content-section" v-html="parseMarkdown(message.structuredResult.knowledge?.markdownContent || '暂无知识模块数据')"></div>
            </div>

            <!-- 知识图谱 -->
            <div v-show="(message.activeTab || 'summary') === 'graph'" class="tab-content">
              <div v-if="message.structuredResult.graph?.nodes?.length" class="content-section">
                <div id="knowledge-graph-container" style="width:100%;height:500px;border:1px solid #e2e8f0;border-radius:12px;background:#fafbfc;"></div>
                <div class="graph-legend">
                  <span class="legend-item"><span class="legend-dot" style="background:#e53e3e"></span>前驱必学</span>
                  <span class="legend-item"><span class="legend-dot" style="background:#667eea"></span>包含</span>
                  <span class="legend-item"><span class="legend-dot" style="background:#48bb78"></span>推荐顺序</span>
                  <span class="legend-item"><span class="legend-dot" style="background:#a0aec0;border-style:dashed"></span>关联</span>
                </div>
                <details>
                  <summary>📝 文本大纲（备用）</summary>
                  <pre class="text-outline">{{ message.structuredResult.graph?.textOutline || '暂无' }}</pre>
                </details>
              </div>
              <div v-else class="content-section" style="text-align:center;padding:40px;color:#a0aec0;">
                <span style="font-size:48px">🧠</span>
                <p>暂无知识图谱数据</p>
                <p style="font-size:12px">生成学习方案后将自动构建知识图谱</p>
              </div>
            </div>

            <!-- 练习题 -->
            <div v-show="(message.activeTab || 'summary') === 'exercises'" class="tab-content">
              <div v-if="message.structuredResult.exercises?.exercises" class="content-section">
                <div v-for="(ex, i) in (message.structuredResult.exercises?.exercises || [])" :key="i" class="exercise-card">
                  <div class="exercise-header">
                    <span class="ex-number">第{{ i + 1 }}题</span>
                    <span class="ex-type">{{ ex.type }}</span>
                    <span class="ex-difficulty">{{ ex.difficulty }}</span>
                    <span class="ex-module">{{ ex.module }}</span>
                  </div>
                  <p class="ex-question"><strong>{{ i + 1 }}.</strong> {{ ex.question }}</p>
                  <div v-if="ex.options?.length" class="ex-options">
                    <div v-for="(opt, oi) in ex.options" :key="oi" class="ex-option">
                      {{ String.fromCharCode(65 + oi) }}. {{ opt }}
                    </div>
                  </div>
                  <details class="ex-answer">
                    <summary>查看答案与解析</summary>
                    <p><strong>答案：</strong>{{ ex.answer }}</p>
                    <p><strong>解析：</strong>{{ ex.analysis }}</p>
                    <p v-if="ex.commonMistake"><strong>⚠️ 易错提醒：</strong>{{ ex.commonMistake }}</p>
                    <p v-if="ex.knowledgePoint"><strong>📌 知识点：</strong>{{ ex.knowledgePoint }}</p>
                  </details>
                </div>
              </div>
              <div v-else class="content-section" v-html="parseMarkdown(message.structuredResult.exercises?.markdownContent || '暂无练习题数据')"></div>
            </div>

            <!-- 拓展阅读 -->
            <div v-show="(message.activeTab || 'summary') === 'resources'" class="tab-content">
              <div v-if="message.structuredResult.webSearch?.resources" class="content-section">
                <div v-for="(res, i) in (message.structuredResult.webSearch?.resources || [])" :key="i" class="resource-card">
                  <h4>{{ res.title }}</h4>
                  <p>{{ res.summary }}</p>
                  <div class="resource-meta">
                    <span class="tag">{{ res.type }}</span>
                    <span class="tag">{{ res.difficulty }}</span>
                    <span class="tag">👍 {{ res.recommendReason }}</span>
                  </div>
                </div>
              </div>
              <div v-else class="content-section" v-html="parseMarkdown(message.structuredResult.webSearch?.markdownContent || '暂无拓展阅读数据')"></div>
            </div>
          </div>
        </div>
      </template> <!-- end v-else (non-collapsed) -->
      </div> <!-- end v-for message div -->

      <!-- 输入中指示器 -->
      <div v-if="isProcessing && agentSteps.length === 0" class="typing-indicator">
        <span></span><span></span><span></span>
      </div>
    </div>

    <!-- 输入区 -->
    <div class="chat-input-area">
      <div class="quick-prompts">
        <span class="prompt-hint">💡 试试：</span>
        <button class="prompt-btn" @click="inputMessage='我要考英语四级，基础薄弱'">英语四级</button>
        <button class="prompt-btn" @click="inputMessage='我想学Python，零基础，每天1小时'">Python入门</button>
        <button class="prompt-btn" @click="inputMessage='我要考计算机二级，零基础'">计算机二级</button>
      </div>
      <div class="input-row">
      <textarea
        v-model="inputMessage"
        @keydown.enter.exact.prevent="sendMessage"
        @input="e => { e.target.style.height='auto'; e.target.style.height=Math.min(e.target.scrollHeight, 150)+'px'; }"
        placeholder="输入你的学习需求..."
        rows="2"
        :disabled="isProcessing"
      ></textarea>
      <button
        class="send-btn"
        @click="sendMessage"
        :disabled="isProcessing || !inputMessage.trim()"
      >
        <span v-if="isProcessing">⏳</span>
        <span v-else>🚀</span>
      </button>
    </div>
  </div>
  </div> <!-- close ai-assistant-page -->
</template>

<style scoped>
.ai-assistant-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 140px);
  max-width: 1000px;
  margin: 0 auto;
  padding: 20px;
}

/* Agent面板 */
.agent-panel {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 16px;
  padding: 16px 20px;
  margin-bottom: 16px;
  color: white;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  font-weight: 700;
}

.mode-toggle {
  padding: 6px 14px;
  background: rgba(255,255,255,0.2);
  border: none;
  border-radius: 20px;
  color: white;
  font-size: 12px;
  cursor: pointer;
}
.panel-actions { display:flex; gap:8px; align-items:center; }
.clear-btn { padding:4px 8px; background:rgba(255,255,255,0.15); color:#fbb; border:1px solid rgba(255,100,100,0.4); border-radius:6px; cursor:pointer; font-size:12px; }
.clear-btn:hover { background:rgba(255,100,100,0.25); }
.history-toggle-btn { padding:4px 10px; background:rgba(255,255,255,0.15); color:#fff; border:1px solid rgba(255,255,255,0.3); border-radius:20px; cursor:pointer; font-size:12px; white-space:nowrap; transition:all 0.2s; }
.history-toggle-btn:hover { background:rgba(255,255,255,0.25); }

/* 历史侧边栏 */
.history-sidebar { position:fixed; top:0; left:-320px; width:300px; height:100vh; background:#fff; z-index:200; transition:left 0.3s ease; overflow-y:auto; box-shadow:2px 0 20px rgba(0,0,0,0.1); }
.history-sidebar.open { left:0; }
.history-overlay { position:fixed; inset:0; background:rgba(0,0,0,0.3); z-index:199; }
.history-header { display:flex; justify-content:space-between; align-items:center; padding:24px 20px 16px; border-bottom:1px solid #e2e8f0; }
.history-header h3 { color:#2d3748; margin:0; font-size:1.05em; }
.new-chat-btn { padding:8px 16px; background:linear-gradient(135deg,#667eea,#764ba2); color:#fff; border:none; border-radius:20px; cursor:pointer; font-size:0.85em; font-weight:600; transition:all 0.2s; }
.new-chat-btn:hover { transform:translateY(-1px); box-shadow:0 4px 12px rgba(102,126,234,0.3); }
.history-list { padding:8px 12px; }
.history-item { display:flex; align-items:center; gap:10px; padding:12px 14px; border-radius:10px; cursor:pointer; transition:all 0.15s; margin-bottom:2px; }
.history-item:hover { background:#f7f8ff; }
.history-item.active { background:#f0f2ff; border:1px solid #c4cffc; }
.history-title { flex:1; font-size:0.88em; color:#2d3748; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; font-weight:500; }
.history-time { font-size:0.72em; color:#a0aec0; white-space:nowrap; }
.history-del { background:none; border:none; color:#cbd5e0; cursor:pointer; font-size:0.85em; padding:4px; border-radius:6px; transition:0.15s; }
.history-del:hover { color:#e53e3e; background:#fff5f5; }
.history-empty { color:#a0aec0; text-align:center; padding:40px 20px; font-size:0.9em; }

.agent-steps {
  display: flex;
  flex-direction: column;
  gap: 2px;
  position: relative;
}
.step-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 8px;
  border-radius: 6px;
  font-size: 12px;
  transition: all 0.3s;
  color: rgba(255,255,255,0.85);
}
.step-icon { font-size: 14px; width: 20px; text-align: center; }
.step-agent { flex: 1; min-width: 0; }
.step-status { flex-shrink: 0; font-size: 11px; opacity: 0.9; }
.step-running { background: rgba(255,255,255,0.2); color: #fff; }
.step-success { color: #c6f6d5; }
.step-error { color: #feb2b2; }
.step-pending { opacity: 0.45; }

/* 聊天消息 */
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px 4px;
}

/* 折叠历史 */
.collapsed-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  background: #f7fafc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
  font-size: 13px;
}
.collapsed-bar:hover { background: #edf2f7; border-color: #667eea; }
.collapsed-dot { width: 8px; height: 8px; border-radius: 50%; background: #667eea; flex-shrink: 0; }
.collapsed-summary { color: #4a5568; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.collapsed-time { color: #a0aec0; font-size: 12px; white-space: nowrap; }
.collapsed-expand { color: #667eea; font-size: 12px; white-space: nowrap; }

.message {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
  animation: msgIn 0.3s ease;
}
@keyframes msgIn { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: translateY(0); } }
.message.collapsed { display: block; margin-bottom: 8px; }

.message-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.message-time { font-size: 11px; color: #a0aec0; margin-left: auto; }
.collapse-btn { background: none; border: none; color: #a0aec0; cursor: pointer; font-size: 12px; padding: 2px 6px; border-radius: 4px; }
.collapse-btn:hover { color: #667eea; background: #edf2f7; }

.message-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
  background: #f7fafc;
}

.message-content {
  flex: 1;
  min-width: 0;
}

.message-agent {
  font-size: 12px;
  color: #667eea;
  font-weight: 600;
  margin-bottom: 4px;
}

.multi-badge {
  padding: 2px 8px;
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: white;
  border-radius: 10px;
  font-size: 10px;
  margin-left: 8px;
}

.message-text {
  background: #f7fafc;
  border-radius: 12px;
  padding: 14px 18px;
  line-height: 1.6;
}

.streaming-cursor {
  display: inline;
  color: #667eea;
  font-weight: bold;
  animation: blink 1s step-end infinite;
}

@keyframes blink {
  50% { opacity: 0; }
}

.user-message .message-text {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.user-message {
  flex-direction: row-reverse;
}

/* 结构化结果 */
.structured-result {
  margin-top: 12px;
  background: white;
  border-radius: 16px;
  box-shadow: 0 4px 20px rgba(0,0,0,0.08);
  overflow: hidden;
}

.result-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  padding: 12px;
  background: #f7fafc;
  border-bottom: 1px solid #e2e8f0;
}

.result-tab {
  padding: 8px 14px;
  border: none;
  border-radius: 8px;
  background: none;
  font-size: 13px;
  cursor: pointer;
  color: #718096;
  transition: all 0.2s;
}

.result-tab:hover { background: #edf2f7; }
.result-tab.active {
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: white;
}

.tab-content {
  padding: 20px;
  max-height: 500px;
  overflow-y: auto;
}

/* 需求标签 */
.requirement-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 12px;
}

.req-tag {
  padding: 8px 16px;
  background: linear-gradient(135deg, #667eea15, #764ba215);
  border: 1px solid #667eea30;
  border-radius: 20px;
  font-size: 14px;
}

/* 学习路径时间线 */
.path-timeline { position: relative; padding-left: 40px; }
.path-timeline::before {
  content: '';
  position: absolute;
  left: 18px;
  top: 0;
  bottom: 0;
  width: 3px;
  background: linear-gradient(to bottom, #667eea, #764ba2);
}

.path-stage { position: relative; margin-bottom: 24px; }
.stage-number {
  position: absolute;
  left: -40px;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
}

.stage-content h4 { margin: 0 0 4px; color: #2d3748; }
.stage-content p { color: #718096; font-size: 14px; margin: 0 0 8px; }

.difficulty-badge {
  padding: 2px 10px;
  border-radius: 10px;
  font-size: 11px;
  background: #e2e8f0;
  color: #4a5568;
}

.stage-meta { font-size: 13px; color: #a0aec0; margin-bottom: 8px; }

.stage-modules { display: flex; flex-wrap: wrap; gap: 6px; }
.module-tag {
  padding: 4px 10px;
  background: #edf2f7;
  border-radius: 12px;
  font-size: 12px;
  color: #4a5568;
}

/* 知识模块 */
.knowledge-card {
  padding: 24px;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  margin-bottom: 20px;
  background: white;
  transition: box-shadow 0.2s;
}
.knowledge-card:hover {
  box-shadow: 0 4px 16px rgba(102,126,234,0.08);
}
.knowledge-card-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
  padding-bottom: 14px;
  border-bottom: 2px solid #f0f0ff;
}
.knowledge-card-header h4 {
  margin: 0;
  font-size: 17px;
  color: #2d3748;
}
.knowledge-num {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 14px;
  flex-shrink: 0;
}

.knowledge-section {
  margin-bottom: 18px;
  padding: 12px 16px;
  background: #fafbfc;
  border-radius: 10px;
  border-left: 3px solid #667eea;
}
.knowledge-section.mistakes {
  border-left-color: #e53e3e;
  background: #fff5f5;
}
.knowledge-section h5 {
  color: #667eea;
  margin: 0 0 10px;
  font-size: 14px;
  font-weight: 700;
}
.knowledge-section.mistakes h5 {
  color: #e53e3e;
}
.knowledge-text {
  line-height: 1.8;
  color: #4a5568;
  font-size: 14px;
}
.knowledge-list {
  margin: 0;
  padding-left: 18px;
  line-height: 1.8;
  color: #4a5568;
}
.knowledge-list li {
  margin-bottom: 6px;
}

/* 练习题 */
.exercise-card {
  padding: 20px 24px;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  margin-bottom: 18px;
  background: white;
  transition: box-shadow 0.2s;
}
.exercise-card:hover {
  box-shadow: 0 2px 12px rgba(0,0,0,0.04);
}

.exercise-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}
.ex-number {
  padding: 4px 12px;
  border-radius: 14px;
  font-size: 13px;
  font-weight: 700;
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: white;
}
.ex-type, .ex-difficulty, .ex-module {
  padding: 4px 12px;
  border-radius: 10px;
  font-size: 12px;
  background: #edf2f7;
  color: #4a5568;
}

.ex-question {
  font-size: 15px;
  line-height: 1.7;
  color: #2d3748;
  margin-bottom: 14px;
}

.ex-options { margin-bottom: 14px; }
.ex-option {
  padding: 10px 16px;
  background: #f7fafc;
  border-radius: 10px;
  margin-bottom: 6px;
  font-size: 14px;
  color: #4a5568;
  transition: background 0.2s;
}
.ex-option:hover { background: #edf2f7; }

.ex-answer {
  margin-top: 14px;
  padding: 14px 18px;
  background: #f0fff4;
  border-radius: 10px;
  border: 1px solid #c6f6d5;
}
.ex-answer summary {
  cursor: pointer;
  color: #38a169;
  font-weight: 700;
  font-size: 14px;
}
.ex-answer p {
  margin: 8px 0 4px;
  line-height: 1.7;
}

/* 总览区 */
.content-section {
  line-height: 1.8;
  color: #2d3748;
}

.tab-content {
  padding: 24px;
  max-height: 600px;
  overflow-y: auto;
}

/* 拓展阅读 */
.resource-card {
  padding: 16px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  margin-bottom: 12px;
}

.resource-card h4 { margin: 0 0 8px; color: #2d3748; }

.resource-meta { display: flex; gap: 8px; margin-top: 8px; }
.resource-meta .tag {
  padding: 4px 10px;
  background: #edf2f7;
  border-radius: 10px;
  font-size: 12px;
}

/* 知识图谱图例 */
.graph-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  padding: 10px 16px;
  margin-top: 8px;
}
.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #718096;
}
.legend-dot {
  width: 24px;
  height: 4px;
  border-radius: 2px;
}

/* Mermaid渲染（保留兼容） */
.mermaid-render {
  background: white;
  border-radius: 12px;
  padding: 20px;
  overflow-x: auto;
  display: flex;
  justify-content: center;
}

.mermaid-render :deep(svg) {
  max-width: 100%;
  height: auto;
}

.text-outline {
  font-family: monospace;
  font-size: 13px;
  white-space: pre-wrap;
  color: #4a5568;
}

/* 输入区 */
.quick-prompts {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}
.prompt-hint { font-size: 12px; color: #a0aec0; }
.prompt-btn {
  padding: 4px 12px;
  background: #edf2f7;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  font-size: 12px;
  color: #667eea;
  cursor: pointer;
  transition: all 0.2s;
}
.prompt-btn:hover { background: #667eea15; border-color: #667eea; }

.chat-input-area {
  display: flex;
  flex-direction: column;
  padding: 16px 0;
  border-top: 1px solid #e2e8f0;
  margin-top: 16px;
}
.input-row {
  display: flex;
  gap: 12px;
}

.chat-input-area textarea {
  flex: 1;
  padding: 14px 18px;
  border: 2px solid #e2e8f0;
  border-radius: 14px;
  font-size: 15px;
  resize: none;
  outline: none;
  transition: border-color 0.3s;
}

.chat-input-area textarea:focus { border-color: #667eea; }

.send-btn {
  width: 52px;
  height: 52px;
  border: none;
  border-radius: 14px;
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: white;
  font-size: 22px;
  cursor: pointer;
  transition: all 0.3s;
}

.send-btn:hover:not(:disabled) { transform: scale(1.05); }
.send-btn:disabled { opacity: 0.5; cursor: not-allowed; }

.typing-indicator {
  display: flex;
  gap: 6px;
  padding: 12px;
}

.typing-indicator span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #667eea;
  animation: typing 1.4s infinite;
}

.typing-indicator span:nth-child(2) { animation-delay: 0.2s; }
.typing-indicator span:nth-child(3) { animation-delay: 0.4s; }

@keyframes typing {
  0%, 60%, 100% { transform: translateY(0); opacity: 0.4; }
  30% { transform: translateY(-8px); opacity: 1; }
}

/* 保存按钮 */
.save-all-bar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 16px;
}
.save-all-btn {
  padding: 10px 20px;
  background: linear-gradient(135deg, #48bb78, #38a169);
  color: white;
  border: none;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}
.save-all-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(72, 187, 120, 0.3);
}
.save-item-btn {
  padding: 6px 12px;
  background: #edf2f7;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s;
  margin-left: auto;
  flex-shrink: 0;
}
.save-item-btn:hover {
  background: #c6f6d5;
  border-color: #48bb78;
}
.save-item-btn.small {
  padding: 4px 10px;
  font-size: 12px;
}

@media (max-width: 768px) {
  .agent-steps { flex-direction: column; }
  .result-tabs { overflow-x: auto; flex-wrap: nowrap; }
}
</style>
