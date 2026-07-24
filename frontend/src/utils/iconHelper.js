/**
 * iconHelper — 公共图标工具函数
 *
 * 提取自 Home.vue / PersonalLearning.vue / KnowledgeResource.vue 的重复逻辑，
 * 统一管理学习目标和学习小组的图标匹配规则。
 */

/**
 * 根据学习目标标题返回对应的emoji图标
 * @param {string} title - 学习目标标题
 * @returns {string} emoji图标
 */
export function getGoalIcon(title) {
  if (!title) return '📚';
  const t = title.toLowerCase();
  if (t.includes('深度') || t.includes('ai') || t.includes('人工智能') || t.includes('机器学习') || t.includes('神经网络') || t.includes('深度学习')) return '🤖';
  if (t.includes('数据') || t.includes('结构') || t.includes('算法') || t.includes('leetcode') || t.includes('刷题')) return '🧮';
  if (t.includes('编程') || t.includes('python') || t.includes('代码') || t.includes('java') || t.includes('c++') || t.includes('javascript') || t.includes('js') || t.includes('typescript')) return '💻';
  if (t.includes('数学') || t.includes('代数') || t.includes('概率') || t.includes('微积分') || t.includes('线性代数') || t.includes('高数')) return '📐';
  if (t.includes('英语') || t.includes('语言') || t.includes('口语') || t.includes('托福') || t.includes('雅思') || t.includes('gre')) return '🌍';
  if (t.includes('设计') || t.includes('ui') || t.includes('ux') || t.includes('figma') || t.includes('sketch')) return '🎨';
  if (t.includes('网络') || t.includes('web') || t.includes('前端') || t.includes('后端') || t.includes('vue') || t.includes('react') || t.includes('angular')) return '🌐';
  if (t.includes('数据库') || t.includes('sql') || t.includes('mysql') || t.includes('postgres') || t.includes('mongodb')) return '🗄️';
  if (t.includes('安全') || t.includes('加密') || t.includes('渗透') || t.includes('漏洞')) return '🔒';
  if (t.includes('测试') || t.includes('自动化') || t.includes('selenium') || t.includes('junit')) return '🧪';
  if (t.includes('运维') || t.includes('devops') || t.includes('docker') || t.includes('kubernetes') || t.includes('k8s')) return '⚙️';
  if (t.includes('商业') || t.includes('分析') || t.includes('营销') || t.includes('管理')) return '💼';
  if (t.includes('写作') || t.includes('论文') || t.includes('报告')) return '✍️';
  if (t.includes('视频') || t.includes('剪辑') || t.includes('pr') || t.includes('ae')) return '🎬';
  return '📚';
}

/**
 * 根据学习小组名称返回对应的emoji图标
 * @param {string} name - 小组名称
 * @returns {string} emoji图标
 */
export function getGroupIcon(name) {
  if (!name) return '📚';
  const n = name.toLowerCase();
  if (n.includes('python') || n.includes('编程') || n.includes('算法')) return '💻';
  if (n.includes('数学') || n.includes('高数')) return '📐';
  if (n.includes('英语') || n.includes('口语')) return '🌍';
  if (n.includes('ai') || n.includes('人工智能') || n.includes('机器学习')) return '🤖';
  if (n.includes('设计') || n.includes('ui') || n.includes('ux')) return '🎨';
  if (n.includes('商业') || n.includes('案例')) return '💼';
  return '📚';
}

/**
 * 根据题目类型返回对应的emoji图标
 * @param {string} type - 题目类型
 * @returns {string} emoji图标
 */
export function getTypeEmoji(type) {
  return {
    '选择题': '🔤',
    '填空题': '✏️',
    '简答题': '📝',
    '编程题': '💻'
  }[type] || '❓';
}

/**
 * 根据难度标签返回对应的显示样式
 * @param {string} difficulty - 难度等级
 * @returns {{ emoji: string, color: string }}
 */
export function getDifficultyStyle(difficulty) {
  const map = {
    '入门': { emoji: '🟢', color: '#22c55e' },
    '基础': { emoji: '🟢', color: '#22c55e' },
    '中级': { emoji: '🟡', color: '#eab308' },
    '高级': { emoji: '🔴', color: '#ef4444' }
  };
  return map[difficulty] || { emoji: '⚪', color: '#999' };
}
