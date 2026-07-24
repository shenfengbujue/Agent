import axios from 'axios';

const API_BASE_URL = '/api';

// 配置 axios 拦截器：自动携带 token
axios.interceptors.request.use(config => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

axios.interceptors.response.use(response => {
  return response;
}, error => {
  console.error('API Error:', error.response?.data || error.message);
  return Promise.reject(error);
});

// ==================== Auth API ====================
export const authApi = {
  login: (data) => axios.post(`${API_BASE_URL}/auth/login`, data),
  register: (data) => axios.post(`${API_BASE_URL}/auth/register`, data),
  getMe: () => axios.get(`${API_BASE_URL}/auth/me`),
  changePassword: (data) => axios.post(`${API_BASE_URL}/auth/change-password`, data),
  updateProfile: (data) => axios.post(`${API_BASE_URL}/auth/profile`, data),
  getProfile: () => axios.get(`${API_BASE_URL}/auth/profile`),
};

// ==================== Agent API ====================
export const agentApi = {
  getAllAgents: () => axios.get(`${API_BASE_URL}/agents`),
  getEnabledAgents: () => axios.get(`${API_BASE_URL}/agents/enabled`),
  getAgentById: (id) => axios.get(`${API_BASE_URL}/agents/${id}`),
  createAgent: (agent) => axios.post(`${API_BASE_URL}/agents`, agent),
  updateAgent: (id, agent) => axios.put(`${API_BASE_URL}/agents/${id}`, agent),
  deleteAgent: (id) => axios.delete(`${API_BASE_URL}/agents/${id}`),
  generateResource: (agentId, prompt) => axios.post(`${API_BASE_URL}/agents/${agentId}/generate`, { prompt }),
  chat: (agentId, message) => axios.post(`${API_BASE_URL}/agents/${agentId}/chat`, { message }),
  getHistory: (agentId) => axios.get(`${API_BASE_URL}/chat/history/${agentId}`),
  getConversations: () => axios.get(`${API_BASE_URL}/chat/conversations`),
  processQuery: (query) => axios.post(`${API_BASE_URL}/agents/process-query`, { query })
};

// ==================== Resource API ====================
export const resourceApi = {
  getAllResources: () => axios.get(`${API_BASE_URL}/resources`),
  getFeaturedResources: () => axios.get(`${API_BASE_URL}/resources/featured`),
  getTopResources: () => axios.get(`${API_BASE_URL}/resources/top`),
  getLatestResources: () => axios.get(`${API_BASE_URL}/resources/latest`),
  getResourcesByCategory: (category) => axios.get(`${API_BASE_URL}/resources/category/${category}`),
  getResourceById: (id) => axios.get(`${API_BASE_URL}/resources/${id}`),
  createResource: (resource) => axios.post(`${API_BASE_URL}/resources`, resource),
  updateResource: (id, resource) => axios.put(`${API_BASE_URL}/resources/${id}`, resource),
  deleteResource: (id) => axios.delete(`${API_BASE_URL}/resources/${id}`),
  likeResource: (id) => axios.post(`${API_BASE_URL}/resources/${id}/like`)
};

// ==================== User API ====================
export const userApi = {
  login: (username, password) => axios.post(`${API_BASE_URL}/auth/login`, { username, password }),
  register: (userData) => axios.post(`${API_BASE_URL}/auth/register`, userData),
  getCurrentUser: () => axios.get(`${API_BASE_URL}/users/me`),
  getAllUsers: () => axios.get(`${API_BASE_URL}/users`),
  getUserById: (id) => axios.get(`${API_BASE_URL}/users/${id}`),
  getLeaderboard: () => axios.get(`${API_BASE_URL}/users/leaderboard`),
  getUserProgress: (userId) => axios.get(`${API_BASE_URL}/users/${userId}/progress`)
};

// ==================== Study Group API ====================
export const groupApi = {
  createGroup: (data) => axios.post(`${API_BASE_URL}/social/group/create`, data),
  joinGroup: (groupId) => axios.post(`${API_BASE_URL}/social/group/${groupId}/join`),
  leaveGroup: (groupId) => axios.delete(`${API_BASE_URL}/social/group/${groupId}/leave`),
  getMyGroups: () => axios.get(`${API_BASE_URL}/social/group/my`),
  getAllGroups: () => axios.get(`${API_BASE_URL}/social/group/all`),
  getGroupRankings: () => axios.get(`${API_BASE_URL}/social/group/rankings`),
  getGroupById: (groupId) => axios.get(`${API_BASE_URL}/social/group/${groupId}`),
  getGroupMembers: (groupId) => axios.get(`${API_BASE_URL}/social/group/${groupId}/members`),
  deleteGroup: (groupId) => axios.delete(`${API_BASE_URL}/social/group/${groupId}`),
  uploadResource: (groupId, data) => axios.post(`${API_BASE_URL}/social/group/${groupId}/resource/upload`, data),
  getGroupResources: (groupId) => axios.get(`${API_BASE_URL}/social/group/${groupId}/resources`),
  deleteResource: (resourceId) => axios.delete(`${API_BASE_URL}/social/group/resource/${resourceId}`),
};

// ==================== Social / Group API (compatible aliases) ====================
export const socialApi = {
  createGroup: (groupData) => axios.post(`${API_BASE_URL}/social/group/create`, groupData),
  joinGroup: (groupId) => axios.post(`${API_BASE_URL}/social/group/${groupId}/join`),
  leaveGroup: (groupId) => axios.delete(`${API_BASE_URL}/social/group/${groupId}/leave`),
  getMyGroups: () => axios.get(`${API_BASE_URL}/social/group/my`),
  getGroupMembers: (groupId) => axios.get(`${API_BASE_URL}/social/group/${groupId}/members`),
  createPost: (data) => axios.post(`${API_BASE_URL}/social/post/create`, data),
  getGroupPosts: (groupId) => axios.get(`${API_BASE_URL}/social/post/group/${groupId}`),
  addComment: (postId, commentData) => axios.post(`${API_BASE_URL}/social/comment/add`, commentData),
  getActivityFeed: () => axios.get(`${API_BASE_URL}/social/leaderboard`)
};

// ==================== Post API ====================
export const postApi = {
  createPost: (data) => axios.post(`${API_BASE_URL}/social/post/create`, data),
  getGroupPosts: (groupId) => axios.get(`${API_BASE_URL}/social/post/group/${groupId}`),
  deletePost: (postId) => axios.delete(`${API_BASE_URL}/social/post/${postId}`),
};

// ==================== Comment API ====================
export const commentApi = {
  addComment: (data) => axios.post(`${API_BASE_URL}/social/comment/add`, data),
  getPostComments: (postId) => axios.get(`${API_BASE_URL}/social/comment/post/${postId}`),
  deleteComment: (commentId) => axios.delete(`${API_BASE_URL}/social/comment/${commentId}`),
};

// ==================== Tutoring API ====================
export const tutoringApi = {
  requestTutoring: (topic, description) => axios.post(`${API_BASE_URL}/social/tutoring/request`, null, { params: { topic, description } }),
  getMyTutoring: () => axios.get(`${API_BASE_URL}/social/tutoring/my`),
  completeTutoring: (id, rating, feedback) => axios.put(`${API_BASE_URL}/social/tutoring/${id}/complete`, null, { params: { rating, feedback } }),
};

// ==================== Achievement API ====================
export const achievementApi = {
  getAchievements: () => axios.get(`${API_BASE_URL}/social/achievements`),
  awardAchievement: (achievementType) => axios.post(`${API_BASE_URL}/social/achievements/award`, null, { params: { achievementType } }),
};

// ==================== Leaderboard API ====================
export const leaderboardApi = {
  getLeaderboard: (course, limit) => axios.get(`${API_BASE_URL}/social/leaderboard`, { params: { course, limit } }),
};

// ==================== Progress API ====================
export const progressApi = {
  getProgress: (userId) => axios.get(`${API_BASE_URL}/users/${userId}/progress`),
  createOrUpdateProgress: (userId, progressData) => axios.put(`${API_BASE_URL}/users/${userId}/progress`, progressData),
  completeProgress: (userId, progressId) => axios.put(`${API_BASE_URL}/users/${userId}/progress/${progressId}/complete`),
  deleteProgress: (userId, progressId) => axios.delete(`${API_BASE_URL}/users/${userId}/progress/${progressId}`)
};

// ==================== Knowledge API ====================
export const knowledgeApi = {
  // 搜索
  searchKnowledge: (query, category, limit) => axios.get(`${API_BASE_URL}/knowledge/search`, { params: { query, category, limit } }),
  generateResources: (goalTitle, prompt) => axios.post(`${API_BASE_URL}/knowledge/generate`, { goalTitle, prompt }),
  getKnowledgeItemById: (id) => axios.get(`${API_BASE_URL}/knowledge/item/${id}`),
  getKnowledgeItemByTitle: (title) => axios.get(`${API_BASE_URL}/knowledge/item`, { params: { title } }),
  toggleLike: (id) => axios.post(`${API_BASE_URL}/knowledge/item/${id}/like`),
  recordView: (id) => axios.post(`${API_BASE_URL}/knowledge/item/${id}/view`),
  // 意图识别与分发
  query: (query) => axios.post(`${API_BASE_URL}/knowledge/query`, { query }),
  queryByBase: (baseId, query) => axios.post(`${API_BASE_URL}/knowledge/query/${baseId}`, { query }),
  recognizeIntent: (query) => axios.post(`${API_BASE_URL}/knowledge/intent/recognize`, { query }),
  // 知识库管理
  getAllBases: () => axios.get(`${API_BASE_URL}/knowledge/bases`),
  getBaseById: (id) => axios.get(`${API_BASE_URL}/knowledge/bases/${id}`),
  createBase: (data) => axios.post(`${API_BASE_URL}/knowledge/bases`, data),
  updateBase: (id, data) => axios.put(`${API_BASE_URL}/knowledge/bases/${id}`, data),
  deleteBase: (id) => axios.delete(`${API_BASE_URL}/knowledge/bases/${id}`),
  // 知识条目管理
  getEntriesByBaseId: (baseId) => axios.get(`${API_BASE_URL}/knowledge/entries/${baseId}`),
  getEntryById: (id) => axios.get(`${API_BASE_URL}/knowledge/entry/${id}`),
  createEntry: (data) => axios.post(`${API_BASE_URL}/knowledge/entries`, data),
  updateEntry: (id, data) => axios.put(`${API_BASE_URL}/knowledge/entries/${id}`, data),
  deleteEntry: (id) => axios.delete(`${API_BASE_URL}/knowledge/entries/${id}`),
  // 意图规则管理
  getAllIntentRules: () => axios.get(`${API_BASE_URL}/knowledge/intents`),
  createIntentRule: (data) => axios.post(`${API_BASE_URL}/knowledge/intents`, data),
  updateIntentRule: (id, data) => axios.put(`${API_BASE_URL}/knowledge/intents/${id}`, data),
  deleteIntentRule: (id) => axios.delete(`${API_BASE_URL}/knowledge/intents/${id}`),
  // 数据导入
  importAll: () => axios.post(`${API_BASE_URL}/knowledge/import/all`),
  // 保存AI生成内容到知识库
  saveGenerated: (data) => axios.post(`${API_BASE_URL}/knowledge/save-generated`, data),
  // 获取AI生成内容列表
  getAIGenerated: () => axios.get(`${API_BASE_URL}/knowledge/ai-generated`),
  // 知识图谱
  getGraph: (topic) => axios.get(`${API_BASE_URL}/knowledge/graph/${encodeURIComponent(topic)}`),
  getGraphTopics: () => axios.get(`${API_BASE_URL}/knowledge/graph-topics`)
};

// ==================== Study Goal API ====================
export const goalApi = {
  getGoalsByUserId: (userId) => axios.get(`${API_BASE_URL}/goals/user/${userId}`),
  getGoalById: (id) => axios.get(`${API_BASE_URL}/goals/${id}`),
  createGoal: (userId, goal) => axios.post(`${API_BASE_URL}/goals/user/${userId}`, goal),
  updateGoal: (userId, goalId, goal) => axios.put(`${API_BASE_URL}/goals/user/${userId}/${goalId}`, goal),
  deleteGoal: (userId, goalId) => axios.delete(`${API_BASE_URL}/goals/user/${userId}/${goalId}`)
};

// ==================== User Profile API ====================
export const profileApi = {
  getProfile: (userId) => axios.get(`${API_BASE_URL}/profile/${userId}`),
  updateProfile: (userId, data) => axios.put(`${API_BASE_URL}/profile/${userId}`, data),
  updateKeywords: (userId, text) => axios.post(`${API_BASE_URL}/profile/${userId}/keywords`, { text }),
  addInterest: (userId, interest) => axios.post(`${API_BASE_URL}/profile/${userId}/interests`, { interest }),
  extractKeywords: (text) => axios.post(`${API_BASE_URL}/profile/extract`, { text }),
  // 对话式画像分析
  analyzeDialogue: (userId, dialogueText) => axios.post(`${API_BASE_URL}/profile/${userId}/dialogue`, { dialogueText }),
  // 获取结构化画像维度
  getDimensions: (userId) => axios.get(`${API_BASE_URL}/profile/${userId}/dimensions`),
  // 根据学习进度更新画像
  updateFromLearningProgress: (topic, category, progressPercentage) =>
    axios.post(`${API_BASE_URL}/auth/profile/learning-progress`, { topic, category, progressPercentage })
};

// ==================== Chat History API ====================
export const chatApi = {
  getHistory: (agentId) => axios.get(`${API_BASE_URL}/chat/history/${agentId}`),
  getConversations: () => axios.get(`${API_BASE_URL}/chat/conversations`),
  deleteHistory: (agentId) => axios.delete(`${API_BASE_URL}/chat/history/${agentId}`),
  clearAllHistory: () => axios.delete(`${API_BASE_URL}/chat/history`)
};

// ==================== Multi-Agent Coordinator API ====================
export const coordinatorApi = {
  processQuery: (query) => axios.post(`${API_BASE_URL}/agents/process-query`, { query }),
  // 获取多智能体协同处理进度（SSE流式，后续版本）
  processQueryStream: (query) => fetch(`${API_BASE_URL}/agents/process-query-stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${localStorage.getItem('token')}`
    },
    body: JSON.stringify({ query })
  })
};