<script setup>
import { ref, onMounted, computed } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { gsap } from 'gsap';
import { groupApi, postApi, commentApi } from '../api/index';

const router = useRouter();
const route = useRoute();

const groupId = computed(() => Number(route.params.id));
const currentUser = ref(null);
const group = ref(null);
const isMember = ref(false);
const activeTab = ref('discussions');
const newPostContent = ref('');
const replyContent = ref({});
const showReplyInput = ref({});

const mockGroups = [
  {
    id: 1,
    name: 'Python 学习小组',
    description: '一起学习Python编程，从入门到进阶，互相讨论解决编程难题。',
    category: 'programming',
    icon: '🐍',
    memberCount: 28,
    maxMembers: 50,
    currentMembers: 24,
    isPublic: true,
    createdAt: '2024-12-01',
    topics: ['基础语法', '数据分析', '爬虫', 'Web开发'],
    owner: { name: 'Python大师', avatar: '🐍' }
  },
  {
    id: 2,
    name: '机器学习研习社',
    description: '深度学习、神经网络、自然语言处理等AI前沿技术研讨。',
    category: 'ai',
    icon: '🧠',
    memberCount: 35,
    maxMembers: 40,
    currentMembers: 32,
    isPublic: true,
    createdAt: '2024-11-15',
    topics: ['深度学习', 'NLP', '计算机视觉', '强化学习'],
    owner: { name: 'AI探索者', avatar: '🤖' }
  },
  {
    id: 3,
    name: '高等数学互助组',
    description: '微积分、线性代数、概率论等高等数学知识共享与答疑。',
    category: 'math',
    icon: '📐',
    memberCount: 22,
    maxMembers: 30,
    currentMembers: 18,
    isPublic: true,
    createdAt: '2024-12-10',
    topics: ['微积分', '线性代数', '概率论', '离散数学'],
    owner: { name: '数学达人', avatar: '🔢' }
  },
  {
    id: 4,
    name: '英语口语角',
    description: '每日英语口语练习，托福雅思备考交流，英语学习资源分享。',
    category: 'english',
    icon: '🗣️',
    memberCount: 45,
    maxMembers: 60,
    currentMembers: 38,
    isPublic: true,
    createdAt: '2024-10-20',
    topics: ['口语练习', '托福', '雅思', '商务英语'],
    owner: { name: '英语达人', avatar: '🌍' }
  },
  {
    id: 5,
    name: '前端开发社区',
    description: 'Vue、React、Angular等前端框架学习，CSS技巧交流。',
    category: 'programming',
    icon: '⚛️',
    memberCount: 30,
    maxMembers: 45,
    currentMembers: 26,
    isPublic: true,
    createdAt: '2024-11-28',
    topics: ['Vue', 'React', 'CSS', 'TypeScript'],
    owner: { name: '前端架构师', avatar: '🎨' }
  },
  {
    id: 6,
    name: 'UI/UX 设计交流',
    description: '用户界面和用户体验设计交流，Figma、Sketch等工具使用心得。',
    category: 'design',
    icon: '🎨',
    memberCount: 18,
    maxMembers: 25,
    currentMembers: 15,
    isPublic: true,
    createdAt: '2024-12-05',
    topics: ['UI设计', 'UX研究', 'Figma', '设计系统'],
    owner: { name: '设计美学', avatar: '🖌️' }
  },
  {
    id: 7,
    name: '算法与数据结构',
    description: 'LeetCode刷题小组，面试算法准备，编程竞赛讨论。',
    category: 'programming',
    icon: '🧩',
    memberCount: 40,
    maxMembers: 50,
    currentMembers: 36,
    isPublic: true,
    createdAt: '2024-09-15',
    topics: ['LeetCode', '面试', '竞赛', '图论'],
    owner: { name: '算法狂魔', avatar: '⚡' }
  },
  {
    id: 8,
    name: '商业案例分析',
    description: 'MBA经典案例分析，商业模式探讨，创业经验交流。',
    category: 'business',
    icon: '📊',
    memberCount: 15,
    maxMembers: 30,
    currentMembers: 12,
    isPublic: true,
    createdAt: '2024-12-08',
    topics: ['案例分析', '商业模式', '创业', '市场营销'],
    owner: { name: '商业精英', avatar: '💼' }
  }
];

const mockMembers = {
  1: [
    { id: 101, name: 'Python大师', avatar: '🐍', role: 'owner', joinedAt: '2024-12-01', posts: 15, passCount: 8, resourceCount: 5, streak: 12, level: 5 },
    { id: 102, name: '数据分析小白', avatar: '📊', role: 'member', joinedAt: '2024-12-03', posts: 8, passCount: 5, resourceCount: 3, streak: 7, level: 3 },
    { id: 103, name: '爬虫爱好者', avatar: '🕷️', role: 'member', joinedAt: '2024-12-05', posts: 6, passCount: 4, resourceCount: 2, streak: 5, level: 2 },
    { id: 104, name: 'Web开发者', avatar: '🌐', role: 'member', joinedAt: '2024-12-08', posts: 4, passCount: 3, resourceCount: 1, streak: 3, level: 2 },
    { id: 105, name: '编程新手', avatar: '💻', role: 'member', joinedAt: '2024-12-10', posts: 2, passCount: 1, resourceCount: 0, streak: 1, level: 1 },
  ],
  2: [
    { id: 201, name: 'AI探索者', avatar: '🤖', role: 'owner', joinedAt: '2024-11-15', posts: 20, passCount: 12, resourceCount: 8, streak: 15, level: 6 },
    { id: 202, name: '深度学习达人', avatar: '🧠', role: 'member', joinedAt: '2024-11-18', posts: 12, passCount: 8, resourceCount: 4, streak: 10, level: 4 },
    { id: 203, name: 'NLP研究员', avatar: '📝', role: 'member', joinedAt: '2024-11-20', posts: 10, passCount: 6, resourceCount: 3, streak: 8, level: 3 },
    { id: 204, name: 'CV工程师', avatar: '👁️', role: 'member', joinedAt: '2024-11-25', posts: 7, passCount: 5, resourceCount: 2, streak: 6, level: 3 },
  ],
  3: [
    { id: 301, name: '数学达人', avatar: '🔢', role: 'owner', joinedAt: '2024-12-10', posts: 18, passCount: 15, resourceCount: 6, streak: 14, level: 5 },
    { id: 302, name: '微积分爱好者', avatar: '📐', role: 'member', joinedAt: '2024-12-12', posts: 9, passCount: 8, resourceCount: 3, streak: 7, level: 4 },
    { id: 303, name: '线性代数高手', avatar: '📈', role: 'member', joinedAt: '2024-12-15', posts: 6, passCount: 5, resourceCount: 2, streak: 5, level: 3 },
  ],
  4: [
    { id: 401, name: '英语达人', avatar: '🌍', role: 'owner', joinedAt: '2024-10-20', posts: 25, passCount: 18, resourceCount: 10, streak: 20, level: 7 },
    { id: 402, name: '托福备考者', avatar: '📚', role: 'member', joinedAt: '2024-10-25', posts: 15, passCount: 12, resourceCount: 6, streak: 12, level: 5 },
    { id: 403, name: '雅思学员', avatar: '✈️', role: 'member', joinedAt: '2024-11-01', posts: 10, passCount: 8, resourceCount: 4, streak: 8, level: 4 },
  ],
  5: [
    { id: 501, name: '前端架构师', avatar: '🎨', role: 'owner', joinedAt: '2024-11-28', posts: 16, passCount: 10, resourceCount: 7, streak: 13, level: 5 },
    { id: 502, name: 'Vue开发者', avatar: '💚', role: 'member', joinedAt: '2024-12-01', posts: 11, passCount: 7, resourceCount: 4, streak: 9, level: 4 },
    { id: 503, name: 'React爱好者', avatar: '⚛️', role: 'member', joinedAt: '2024-12-03', posts: 8, passCount: 6, resourceCount: 3, streak: 6, level: 3 },
  ],
  6: [
    { id: 601, name: '设计美学', avatar: '🖌️', role: 'owner', joinedAt: '2024-12-05', posts: 12, passCount: 6, resourceCount: 8, streak: 10, level: 4 },
    { id: 602, name: 'UI设计师', avatar: '🎯', role: 'member', joinedAt: '2024-12-07', posts: 7, passCount: 4, resourceCount: 5, streak: 5, level: 3 },
    { id: 603, name: 'UX研究员', avatar: '🔍', role: 'member', joinedAt: '2024-12-09', posts: 5, passCount: 3, resourceCount: 3, streak: 4, level: 2 },
  ],
  7: [
    { id: 701, name: '算法狂魔', avatar: '⚡', role: 'owner', joinedAt: '2024-09-15', posts: 30, passCount: 25, resourceCount: 12, streak: 30, level: 8 },
    { id: 702, name: 'LeetCode选手', avatar: '🔥', role: 'member', joinedAt: '2024-09-20', posts: 20, passCount: 18, resourceCount: 6, streak: 20, level: 6 },
    { id: 703, name: '面试准备者', avatar: '💼', role: 'member', joinedAt: '2024-10-01', posts: 14, passCount: 10, resourceCount: 4, streak: 15, level: 5 },
  ],
  8: [
    { id: 801, name: '商业精英', avatar: '💼', role: 'owner', joinedAt: '2024-12-08', posts: 10, passCount: 5, resourceCount: 6, streak: 8, level: 4 },
    { id: 802, name: 'MBA学员', avatar: '📚', role: 'member', joinedAt: '2024-12-10', posts: 6, passCount: 3, resourceCount: 3, streak: 5, level: 3 },
  ]
};

const groupMembers = ref([]);

const memberRanking = computed(() => {
  return [...groupMembers.value].sort((a, b) => {
    const scoreA = (a.passCount || 0) * 2 + (a.resourceCount || 0) + (a.streak || 0) * 3 + (a.posts || 0);
    const scoreB = (b.passCount || 0) * 2 + (b.resourceCount || 0) + (b.streak || 0) * 3 + (b.posts || 0);
    return scoreB - scoreA;
  });
});

const actualMemberCount = computed(() => groupMembers.value.length);

const getMemberScore = (member) => {
  return (member.passCount || 0) * 2 + (member.resourceCount || 0) + (member.streak || 0) * 3 + (member.posts || 0);
};

const getMedal = (rank) => {
  if (rank === 1) return '🥇';
  if (rank === 2) return '🥈';
  if (rank === 3) return '🥉';
  return null;
};

const groupPosts = ref([]);
const groupResources = ref([]);
const showUploadModal = ref(false);
const newResource = ref({
  title: '',
  description: '',
  resourceType: 'PDF',
  fileUrl: '',
  fileName: '',
  fileSize: 0,
  fileBase64: ''
});
const fileInputRef = ref(null);
const uploadFileName = ref('');
const uploadFileSize = ref(0);

const isGroupOwner = computed(() => {
  if (!currentUser.value || !group.value) return false;
  return String(currentUser.value.id) === String(group.value.creatorId);
});

onMounted(async () => {
  const user = localStorage.getItem('user');
  if (user) {
    currentUser.value = JSON.parse(user);
  }

  await loadGroupDetail();
  await loadPosts();
  await loadResources();
  animatePage();
});

const loadGroupDetail = async () => {
  try {
    const res = await groupApi.getGroupById(groupId.value);
    if (res.data && res.data.code === 200 && res.data.data) {
      const found = res.data.data;
      group.value = {
        id: found.id,
        name: found.groupName || found.name,
        description: found.description || '暂无描述',
        category: (found.course || 'other').toLowerCase(),
        icon: getGroupIcon(found.course || found.groupName),
        memberCount: found.memberCount || 1,
        maxMembers: found.maxMembers || 50,
        currentMembers: found.memberCount || 1,
        isPublic: true,
        createdAt: found.createdAt,
        topics: found.course ? [found.course] : [],
        owner: { name: found.creatorId || '系统', avatar: '👤' },
        creatorId: found.creatorId
      };
      await loadGroupMembers();
      return;
    }
  } catch (e) {
    console.error('从后端加载小组详情失败:', e);
    group.value = null;
    groupMembers.value = [];
    isMember.value = false;
  }
};

const loadGroupMembers = async () => {
  try {
    const res = await groupApi.getGroupMembers(groupId.value);
    if (res.data && res.data.code === 200 && res.data.data) {
      const members = res.data.data.map(m => ({
        id: m.userId || m.id,
        userId: m.userId,
        nickname: m.nickname,
        username: m.username,
        name: m.nickname || m.username || m.userId || '用户',
        avatar: '👤',
        role: m.role === 'CREATOR' ? 'owner' : 'member',
        joinedAt: m.joinedAt ? m.joinedAt.split('T')[0] : new Date().toISOString().split('T')[0],
        posts: m.posts || 0,
        passCount: m.passCount || 0,
        resourceCount: m.resourceCount || 0,
        streak: m.streak || 0,
        level: m.level || 1
      }));
      if (currentUser.value) {
        const currentId = String(currentUser.value.id);
        members.forEach(m => {
          const memberUserId = String(m.userId || m.id);
          if (memberUserId === currentId) {
            m.id = currentUser.value.id;
            m.name = currentUser.value.nickname;
            m.avatar = currentUser.value.avatar || '👤';
            m.avatarUrl = currentUser.value.avatarUrl;
            m.level = currentUser.value.level || 1;
          }
        });
        isMember.value = members.some(m => String(m.userId || m.id) === currentId);
      } else {
        isMember.value = false;
      }
      groupMembers.value = members;
    }
  } catch (e) {
    console.error('加载小组成员失败:', e);
    groupMembers.value = [];
  }
};

const loadPosts = async () => {
  try {
    console.log('加载帖子: groupId=', groupId.value);
    const res = await postApi.getGroupPosts(groupId.value);
    console.log('获取帖子结果:', res.data);
    
    if (res.data && res.data.code === 200 && res.data.data) {
      const posts = [];
      console.log('帖子数量:', res.data.data.length);
      
      for (const p of res.data.data) {
        console.log('处理帖子:', p);
        
        try {
          const commentsRes = await commentApi.getPostComments(p.id);
          const comments = commentsRes.data && commentsRes.data.code === 200 && commentsRes.data.data
            ? commentsRes.data.data.map(r => ({
                id: r.id,
                author: { name: r.userId || '用户', avatar: '👤' },
                content: r.content,
                createdAt: r.createdAt ? formatTime(r.createdAt) : '刚刚',
                userId: r.userId
              }))
            : [];
          
          const post = {
            id: p.id,
            content: p.title || p.content,
            author: { name: p.userId || '用户', avatar: '👤' },
            createdAt: p.createdAt ? formatTime(p.createdAt) : '刚刚',
            likes: p.likeCount || 0,
            liked: false,
            comments: comments,
            userId: p.userId
          };
          
          if (currentUser.value) {
            const currentId = String(currentUser.value.id);
            if (String(p.userId) === currentId) {
              post.author.name = currentUser.value.nickname;
              post.author.avatar = currentUser.value.avatar || '😊';
              post.author.avatarUrl = currentUser.value.avatarUrl;
            }
            
            comments.forEach(c => {
              if (String(c.author.name) === currentId) {
                c.author.name = currentUser.value.nickname;
                c.author.avatar = currentUser.value.avatar || '😊';
                c.author.avatarUrl = currentUser.value.avatarUrl;
              }
            });
          }
          
          posts.push(post);
        } catch (commentErr) {
          console.error('加载评论失败:', commentErr);
        }
      }
      groupPosts.value = posts;
      console.log('帖子加载完成，总数:', posts.length);
    } else {
      console.error('获取帖子失败:', res.data);
      groupPosts.value = [];
    }
  } catch (e) {
    console.error('加载帖子失败:', e);
    groupPosts.value = [];
  }
};

const getGroupIcon = (name) => {
  if (!name) return '📚';
  const n = name.toLowerCase();
  if (n.includes('python') || n.includes('算法')) return '💻';
  if (n.includes('数学') || n.includes('高数')) return '📐';
  return '📚';
};

const handleJoinGroup = async () => {
  if (!group.value || isMember.value) return;

  try {
    await groupApi.joinGroup(groupId.value);
    
    const myGroups = JSON.parse(localStorage.getItem('myGroups') || '[]');
    myGroups.push({
      id: group.value.id,
      name: group.value.name,
      icon: group.value.icon,
      category: group.value.category,
      joinedAt: new Date().toISOString()
    });
    localStorage.setItem('myGroups', JSON.stringify(myGroups));
    
    isMember.value = true;
    group.value.currentMembers = (group.value.currentMembers || 0) + 1;
    
    groupMembers.value.push({
      id: currentUser.value?.id || Date.now(),
      name: currentUser.value?.nickname || '用户',
      avatar: currentUser.value?.avatar || '👤',
      avatarUrl: currentUser.value?.avatarUrl,
      role: 'member',
      joinedAt: new Date().toISOString().split('T')[0],
      posts: 0,
      passCount: 0,
      resourceCount: 0,
      streak: 0,
      level: currentUser.value?.level || 1
    });
  } catch (e) {
    console.error('加入小组失败:', e);
    alert('加入小组失败');
  }
};

const handleLeaveGroup = async () => {
  if (!group.value) return;

  try {
    await groupApi.leaveGroup(groupId.value);
    
    const myGroups = JSON.parse(localStorage.getItem('myGroups') || '[]');
    const updated = myGroups.filter(g => g.id !== group.value.id);
    localStorage.setItem('myGroups', JSON.stringify(updated));
    isMember.value = false;
    group.value.currentMembers = Math.max(0, (group.value.currentMembers || 0) - 1);
    
    const userId = String(currentUser.value?.id);
    groupMembers.value = groupMembers.value.filter(m => String(m.id) !== userId && String(m.userId) !== userId);
  } catch (e) {
    console.error('退出小组失败:', e);
    alert('退出小组失败');
  }
};

const savePosts = () => {
  localStorage.setItem(`groupPosts_${groupId.value}`, JSON.stringify(groupPosts.value));
};

const handleCreatePost = async () => {
  if (!newPostContent.value.trim()) return;

  const content = newPostContent.value.trim();

  try {
    const res = await postApi.createPost({
      groupId: groupId.value,
      title: content,
      content: content
    });
    
    if (res.data && res.data.code === 200) {
      newPostContent.value = '';
      
      const newPost = {
        id: res.data.data?.id || Date.now(),
        content: content,
        author: { name: currentUser.value?.nickname || '我', avatar: currentUser.value?.avatar || '😊', avatarUrl: currentUser.value?.avatarUrl },
        createdAt: '刚刚',
        likes: 0,
        liked: false,
        comments: [],
        userId: currentUser.value?.id
      };
      groupPosts.value.unshift(newPost);
      
      await loadPosts();
    } else {
      alert('发布失败');
    }
  } catch (e) {
    console.error('发布帖子失败:', e);
    alert('发布帖子失败: ' + (e.message || '未知错误'));
  }
};

const formatTime = (dateStr) => {
  if (!dateStr) return '刚刚';
  const date = new Date(dateStr);
  const now = new Date();
  const diff = now - date;
  
  if (diff < 60000) return '刚刚';
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`;
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`;
  
  const month = date.getMonth() + 1;
  const day = date.getDate();
  const hour = date.getHours().toString().padStart(2, '0');
  const minute = date.getMinutes().toString().padStart(2, '0');
  return `${month}月${day}日 ${hour}:${minute}`;
};

const handleToggleLike = (post) => {
  post.liked = !post.liked;
  post.likes += post.liked ? 1 : -1;
  savePosts();
};

const toggleReplyInput = (postId) => {
  showReplyInput.value[postId] = !showReplyInput.value[postId];
};

const handleAddComment = async (postId) => {
  const content = replyContent.value[postId];
  if (!content || !content.trim()) return;

  const post = groupPosts.value.find(p => p.id === postId);
  if (!post) return;

  try {
    const res = await commentApi.addComment({
      postId: postId,
      content: content.trim()
    });
    
    if (res.data && res.data.code === 200) {
      replyContent.value[postId] = '';
      showReplyInput.value[postId] = false;
      await loadPosts();
    }
  } catch (e) {
    console.error('添加评论失败:', e);
    alert('添加评论失败');
  }
};

const handleDeletePost = async (postId) => {
  if (!confirm('确定要删除这条讨论吗？')) return;
  
  try {
    const res = await postApi.deletePost(postId);
    if (res.data && res.data.code === 200) {
      await loadPosts();
    } else {
      alert(res.data?.message || '删除失败');
    }
  } catch (e) {
    console.error('删除帖子失败:', e);
    alert('删除失败：帖子不存在或无权限');
  }
};

const loadResources = async () => {
  try {
    const res = await groupApi.getGroupResources(groupId.value);
    if (res.data && res.data.code === 200 && res.data.data) {
      groupResources.value = res.data.data;
    }
  } catch (e) {
    console.error('加载资源失败:', e);
  }
};

const triggerFileInput = () => {
  if (fileInputRef.value) {
    fileInputRef.value.click();
  }
};

const handleFileChange = (event) => {
  const file = event.target.files[0];
  if (!file) return;
  
  const maxSize = 20 * 1024 * 1024;
  if (file.size > maxSize) {
    alert('文件大小不能超过 20MB');
    event.target.value = '';
    return;
  }
  
  uploadFileName.value = file.name;
  uploadFileSize.value = file.size;
  newResource.value.fileName = file.name;
  newResource.value.fileSize = file.size;
  
  if (!newResource.value.title) {
    newResource.value.title = file.name.replace(/\.[^/.]+$/, '');
  }
  
  const fileExt = file.name.split('.').pop().toLowerCase();
  if (['mp4', 'avi', 'mov', 'mkv', 'webm'].includes(fileExt)) {
    newResource.value.resourceType = '视频';
  } else if (['pdf'].includes(fileExt)) {
    newResource.value.resourceType = 'PDF';
  } else if (['doc', 'docx', 'txt', 'md'].includes(fileExt)) {
    newResource.value.resourceType = '文章';
  } else {
    newResource.value.resourceType = '其他';
  }
  
  const reader = new FileReader();
  reader.onload = (e) => {
    newResource.value.fileBase64 = e.target.result;
  };
  reader.readAsDataURL(file);
};

const formatFileSize = (size) => {
  if (!size) return '';
  if (size < 1024) return size + ' B';
  if (size < 1024 * 1024) return (size / 1024).toFixed(1) + ' KB';
  return (size / 1024 / 1024).toFixed(2) + ' MB';
};

const handleUploadResource = async () => {
  if (!newResource.value.title.trim()) {
    alert('请输入资源标题');
    return;
  }
  if (!newResource.value.fileBase64) {
    alert('请选择要上传的文件');
    return;
  }

  try {
    const res = await groupApi.uploadResource(groupId.value, {
      title: newResource.value.title.trim(),
      description: newResource.value.description.trim(),
      resourceType: newResource.value.resourceType,
      fileUrl: newResource.value.fileBase64,
      fileName: newResource.value.fileName,
      fileSize: newResource.value.fileSize
    });
    
    if (res.data && res.data.code === 200) {
      showUploadModal.value = false;
      newResource.value = { title: '', description: '', resourceType: 'PDF', fileUrl: '', fileName: '', fileSize: 0, fileBase64: '' };
      uploadFileName.value = '';
      uploadFileSize.value = 0;
      if (fileInputRef.value) fileInputRef.value.value = '';
      await loadResources();
      alert('资源上传成功！');
    } else {
      alert(res.data?.message || '上传失败');
    }
  } catch (e) {
    console.error('上传资源失败:', e);
    if (e.response && e.response.data && e.response.data.message) {
      alert(e.response.data.message);
    } else {
      alert('上传失败：' + (e.message || '未知错误'));
    }
  }
};

const handleDeleteResource = async (resourceId) => {
  if (!confirm('确定要删除这个资源吗？')) return;
  
  try {
    const res = await groupApi.deleteResource(resourceId);
    if (res.data && res.data.code === 200) {
      await loadResources();
    } else {
      alert(res.data?.message || '删除失败');
    }
  } catch (e) {
    console.error('删除资源失败:', e);
    if (e.response && e.response.data && e.response.data.message) {
      alert(e.response.data.message);
    } else {
      alert('删除失败：' + (e.message || '未知错误'));
    }
  }
};

const getResourceIcon = (type) => {
  if (type === '视频') return '🎬';
  if (type === '文章') return '📝';
  if (type === 'PDF') return '📄';
  if (type === '链接') return '🔗';
  return '📦';
};

const formatResourceTime = (time) => {
  if (!time) return '刚刚';
  const date = new Date(time);
  const now = new Date();
  const diff = now - date;
  if (diff < 60000) return '刚刚';
  if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前';
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前';
  return Math.floor(diff / 86400000) + '天前';
};

const getMimeType = (fileName) => {
  if (!fileName) return 'application/octet-stream';
  const ext = fileName.split('.').pop().toLowerCase();
  const mimeTypes = {
    'pdf': 'application/pdf',
    'doc': 'application/msword',
    'docx': 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    'txt': 'text/plain',
    'md': 'text/markdown',
    'html': 'text/html',
    'jpg': 'image/jpeg',
    'jpeg': 'image/jpeg',
    'png': 'image/png',
    'gif': 'image/gif',
    'webp': 'image/webp',
    'mp4': 'video/mp4',
    'avi': 'video/x-msvideo',
    'mov': 'video/quicktime',
    'mkv': 'video/x-matroska',
    'webm': 'video/webm',
    'mp3': 'audio/mpeg',
    'wav': 'audio/wav',
    'zip': 'application/zip',
    'rar': 'application/x-rar-compressed',
    '7z': 'application/x-7z-compressed',
    'xlsx': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    'xls': 'application/vnd.ms-excel',
    'ppt': 'application/vnd.ms-powerpoint',
    'pptx': 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
  };
  return mimeTypes[ext] || 'application/octet-stream';
};

const downloadResource = (resource) => {
  if (!resource.fileUrl) {
    alert('该资源暂无文件');
    return;
  }

  try {
    const link = document.createElement('a');
    link.href = resource.fileUrl;
    link.download = resource.fileName || 'resource';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  } catch (e) {
    console.error('下载失败:', e);
    alert('下载失败，请重试');
  }
};

const openResource = (resource) => {
  if (!resource.fileUrl) {
    alert('该资源暂无文件');
    return;
  }

  try {
    const mimeType = getMimeType(resource.fileName);
    const isPreviewable = mimeType.startsWith('image/') || 
                          mimeType.startsWith('video/') || 
                          mimeType === 'application/pdf';

    if (isPreviewable) {
      const blob = dataUrlToBlob(resource.fileUrl);
      const url = URL.createObjectURL(blob);
      window.open(url, '_blank');
      setTimeout(() => URL.revokeObjectURL(url), 10000);
    } else {
      downloadResource(resource);
    }
  } catch (e) {
    console.error('打开失败:', e);
    alert('打开失败，尝试下载');
    downloadResource(resource);
  }
};

const dataUrlToBlob = (dataUrl) => {
  const arr = dataUrl.split(',');
  const mime = arr[0].match(/:(.*?);/)[1];
  const bstr = atob(arr[1]);
  let n = bstr.length;
  const u8arr = new Uint8Array(n);
  while (n--) {
    u8arr[n] = bstr.charCodeAt(n);
  }
  return new Blob([u8arr], { type: mime });
};

const isPostOwner = (post) => {
  if (!currentUser.value) return false;
  const currentUserId = String(currentUser.value.id);
  if (post.userId && String(post.userId) === currentUserId) return true;
  if (group.value?.creatorId && String(group.value.creatorId) === currentUserId) return true;
  return false;
};

const handleDeleteComment = async (postId, commentId) => {
  if (!confirm('确定要删除这条回复吗？')) return;
  
  try {
    const res = await commentApi.deleteComment(commentId);
    if (res.data && res.data.code === 200) {
      await loadPosts();
    } else {
      alert(res.data?.message || '删除失败');
    }
  } catch (e) {
    console.error('删除评论失败:', e);
    alert('删除失败：评论不存在或无权限');
  }
};

const isCommentOwner = (comment) => {
  if (!currentUser.value) return false;
  const currentUserId = String(currentUser.value.id);
  if (comment.userId && String(comment.userId) === currentUserId) return true;
  if (group.value?.creatorId && String(group.value.creatorId) === currentUserId) return true;
  return false;
};

const goBack = () => {
  router.push('/study-groups');
};

const animatePage = () => {
  gsap.fromTo('.group-header-card',
    { opacity: 0, y: -20 },
    { opacity: 1, y: 0, duration: 0.6, ease: 'power3.out' }
  );
  gsap.fromTo('.tab-btn',
    { opacity: 0, y: 10 },
    { opacity: 1, y: 0, duration: 0.3, stagger: 0.1, delay: 0.2 }
  );
  gsap.fromTo('.post-card',
    { opacity: 0, y: 20 },
    { opacity: 1, y: 0, duration: 0.4, stagger: 0.1, delay: 0.4 }
  );
};
</script>

<template>
  <div class="group-detail-page">
    <div class="page-container">
      <button class="back-btn" @click="goBack">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
          <path d="M15 18l-6-6 6-6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
        <span>返回小组列表</span>
      </button>

      <div v-if="!group" class="not-found">
        <span class="not-found-icon">🔍</span>
        <h2>小组未找到</h2>
        <p>该小组可能已被删除或不存在</p>
        <button class="back-home-btn" @click="goBack">返回小组列表</button>
      </div>

      <template v-else>
        <div class="group-header-card">
          <div class="header-top">
            <div class="group-identity">
              <div class="group-icon-wrapper">
                <span class="group-icon">{{ group.icon }}</span>
              </div>
              <div class="group-info">
                <h1 class="group-name">{{ group.name }}</h1>
                <p class="group-desc">{{ group.description }}</p>
                <div class="group-meta-row">
                  <span class="meta-badge">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
                      <circle cx="9" cy="8" r="3" stroke="currentColor" stroke-width="2"/>
                      <path d="M3 20v-1a4 4 0 014-4h4a4 4 0 014 4v1" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
                      <circle cx="18" cy="8" r="2" stroke="currentColor" stroke-width="2"/>
                      <path d="M15 16v-1a3 3 0 011.5-2.6M21 16v-1a3 3 0 00-1-2.2" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
                    </svg>
                    {{ actualMemberCount }}/{{ group.maxMembers }} 成员
                  </span>
                  <span class="meta-badge">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
                      <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2"/>
                      <path d="M12 6v6l4 2" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
                    </svg>
                    创建于 {{ group.createdAt }}
                  </span>
                  <span class="meta-badge owner-badge">
                    {{ group.owner.name }}
                  </span>
                </div>
              </div>
            </div>
            <div class="header-actions">
              <button
                v-if="!isMember"
                class="join-btn"
                @click="handleJoinGroup"
                :disabled="group.currentMembers >= group.maxMembers"
              >
                {{ group.currentMembers >= group.maxMembers ? '小组已满' : '加入小组' }}
              </button>
              <button v-else class="leave-btn" @click="handleLeaveGroup">
                退出小组
              </button>
            </div>
          </div>

          <div class="topics-row">
            <span v-for="topic in group.topics" :key="topic" class="topic-tag">{{ topic }}</span>
          </div>
        </div>

        <div class="tabs-wrapper">
          <button
            :class="['tab-btn', { active: activeTab === 'discussions' }]"
            @click="activeTab = 'discussions'"
          >
            <span>💬</span>
            <span>讨论</span>
            <span class="tab-count">{{ groupPosts.length }}</span>
          </button>
          <button
            :class="['tab-btn', { active: activeTab === 'members' }]"
            @click="activeTab = 'members'"
          >
            <span>👥</span>
            <span>成员</span>
            <span class="tab-count">{{ actualMemberCount }}</span>
          </button>
          <button
            :class="['tab-btn', { active: activeTab === 'resources' }]"
            @click="activeTab = 'resources'"
          >
            <span>📎</span>
            <span>资源</span>
            <span class="tab-count">{{ groupResources.length }}</span>
          </button>
          <button
            :class="['tab-btn', { active: activeTab === 'ranking' }]"
            @click="activeTab = 'ranking'"
          >
            <span>🏆</span>
            <span>成员排行榜</span>
            <span class="tab-count">{{ memberRanking.length }}</span>
          </button>
        </div>

        <div v-if="activeTab === 'discussions'" class="discussions-tab">
          <div v-if="isMember" class="post-composer">
            <div class="composer-header">
              <div class="composer-avatar">
                <img v-if="currentUser?.avatarUrl" :src="currentUser.avatarUrl" alt="头像" class="avatar-img" />
                <span v-else>{{ currentUser?.avatar || '😊' }}</span>
              </div>
              <span class="composer-label">发表讨论</span>
            </div>
            <textarea
              v-model="newPostContent"
              placeholder="分享你的想法、问题或学习心得..."
              class="composer-textarea"
              rows="3"
              maxlength="500"
            ></textarea>
            <div class="composer-footer">
              <span class="char-count">{{ newPostContent.length }}/500</span>
              <button
                class="post-submit-btn"
                @click="handleCreatePost"
                :disabled="!newPostContent.trim()"
              >
                发布
              </button>
            </div>
          </div>

          <div v-if="!isMember" class="join-prompt">
            <span class="prompt-icon">🔒</span>
            <p>加入小组后才能参与讨论</p>
            <button class="join-prompt-btn" @click="handleJoinGroup">立即加入</button>
          </div>

          <div v-if="groupPosts.length === 0" class="empty-posts">
            <span class="empty-icon">💬</span>
            <p>暂无讨论</p>
            <p class="empty-hint">成为第一个发起讨论的人吧！</p>
          </div>
          <div v-else class="posts-list">
            <div v-for="post in groupPosts" :key="post.id" class="post-card">
              <div class="post-header">
                <div class="post-author">
                  <div class="author-avatar">
                    <img v-if="post.author.avatarUrl" :src="post.author.avatarUrl" alt="头像" class="avatar-img" />
                    <span v-else>{{ post.author.avatar }}</span>
                  </div>
                  <div class="author-info">
                    <span class="author-name">{{ post.author.name }}</span>
                    <span class="post-time">{{ post.createdAt }}</span>
                  </div>
                </div>
              </div>

              <div class="post-body">
                <p class="post-content">{{ post.content }}</p>
              </div>

              <div class="post-actions">
                <button
                  :class="['action-btn', { liked: post.liked }]"
                  @click="handleToggleLike(post)"
                >
                  <svg width="16" height="16" viewBox="0 0 24 24" :fill="post.liked ? '#e53e3e' : 'none'">
                    <path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"
                      :stroke="post.liked ? '#e53e3e' : '#a0aec0'" stroke-width="2"/>
                  </svg>
                  <span>{{ post.likes }}</span>
                </button>

                <button class="action-btn" @click="toggleReplyInput(post.id)">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
                    <path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2v10z" stroke="#a0aec0" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                  </svg>
                  <span>{{ post.comments.length }}</span>
                </button>
                
                <button
                  v-if="isPostOwner(post)"
                  class="action-btn delete-btn"
                  @click="handleDeletePost(post.id)"
                >
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
                    <path d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" stroke="#e53e3e" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                  </svg>
                  <span>删除</span>
                </button>
              </div>

              <div v-if="post.comments.length > 0" class="comments-section">
                <div v-for="comment in post.comments" :key="comment.id" class="comment-item">
                  <div class="comment-avatar">
                    <img v-if="comment.author.avatarUrl" :src="comment.author.avatarUrl" alt="头像" class="avatar-img" />
                    <span v-else>{{ comment.author.avatar }}</span>
                  </div>
                  <div class="comment-body">
                    <div class="comment-header">
                      <div class="comment-header-left">
                        <span class="comment-author">{{ comment.author.name }}</span>
                        <span class="comment-time">{{ comment.createdAt }}</span>
                      </div>
                      <button
                        v-if="isCommentOwner(comment)"
                        class="comment-delete-btn"
                        @click="handleDeleteComment(post.id, comment.id)"
                      >
                        删除
                      </button>
                    </div>
                    <p class="comment-content">{{ comment.content }}</p>
                  </div>
                </div>
              </div>

              <div v-if="showReplyInput[post.id] && isMember" class="reply-composer">
                <input
                  v-model="replyContent[post.id]"
                  type="text"
                  placeholder="写下你的回复..."
                  class="reply-input"
                  @keyup.enter="handleAddComment(post.id)"
                />
                <button
                  class="reply-submit-btn"
                  @click="handleAddComment(post.id)"
                  :disabled="!replyContent[post.id]?.trim()"
                >
                  回复
                </button>
              </div>
            </div>
          </div>
        </div>

        <div v-if="activeTab === 'members'" class="members-tab">
          <div v-if="groupMembers.length === 0" class="empty-members">
            <span class="empty-icon">👥</span>
            <p>暂无成员</p>
          </div>
          <div v-else class="members-grid">
            <div v-for="member in groupMembers" :key="member.id" class="member-card">
              <div class="member-avatar-wrapper">
                <div class="member-avatar">
                  <img v-if="member.avatarUrl" :src="member.avatarUrl" alt="头像" class="avatar-img" />
                  <span v-else>{{ member.avatar }}</span>
                </div>
                <span v-if="member.role === 'owner'" class="owner-crown">👑</span>
              </div>
              <div class="member-info">
                <span class="member-name">{{ (member.nickname && !/^\d+$/.test(member.nickname) ? member.nickname : member.username) || ('用户'+member.userId) }}</span>
                <span :class="['member-role', member.role]">
                  {{ member.role === 'owner' ? '组长' : '成员' }}
                </span>
              </div>
              <div class="member-stats">
                <div class="member-stat">
                  <span class="stat-label">发言</span>
                  <span class="stat-value">{{ member.posts || 0 }}</span>
                </div>
                <div class="member-stat">
                  <span class="stat-label">加入</span>
                  <span class="stat-value">{{ member.joinedAt || '刚刚' }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div v-if="activeTab === 'resources'" class="resources-tab">
          <div v-if="isGroupOwner" class="resource-upload-section">
            <button class="upload-btn" @click="showUploadModal = true">
              📤 上传学习资料
            </button>
            <p class="upload-hint">作为小组创建者，您可以上传学习资料给组员</p>
          </div>
          <div v-if="groupResources.length === 0" class="empty-resources">
            <span class="empty-icon">📎</span>
            <p>暂无资源</p>
            <p class="empty-hint">分享学习资源，帮助小组成员共同进步！</p>
          </div>
          <div v-else class="resources-list">
            <div v-for="resource in groupResources" :key="resource.id" class="resource-card">
              <div class="resource-icon-wrapper">
                <span class="resource-icon">{{ getResourceIcon(resource.resourceType) }}</span>
              </div>
              <div class="resource-info">
                <span class="resource-name">{{ resource.title }}</span>
                <span class="resource-meta">
                  {{ resource.uploaderId }} · {{ formatResourceTime(resource.createdAt) }}
                  <span v-if="resource.description"> · {{ resource.description }}</span>
                </span>
              </div>
              <div class="resource-actions">
                <button v-if="resource.fileUrl" class="open-btn" @click="openResource(resource)">打开</button>
                <button v-if="resource.fileUrl" class="download-btn" @click="downloadResource(resource)">下载</button>
                <button v-if="isGroupOwner" class="delete-resource-btn" @click="handleDeleteResource(resource.id)">删除</button>
              </div>
            </div>
          </div>
        </div>

        <div v-if="showUploadModal" class="upload-modal-mask" @click.self="showUploadModal = false">
          <div class="upload-modal">
            <div class="upload-modal-header">
              <h3>上传学习资料</h3>
              <button class="modal-close-btn" @click="showUploadModal = false">×</button>
            </div>
            <div class="upload-modal-body">
              <div class="form-group">
                <label>资源标题 <span class="required">*</span></label>
                <input v-model="newResource.title" type="text" class="form-input" placeholder="请输入资源标题" />
              </div>
              <div class="form-group">
                <label>资源描述</label>
                <textarea v-model="newResource.description" class="form-textarea" placeholder="请输入资源描述（可选）" rows="3"></textarea>
              </div>
              <div class="form-group">
                <label>资源类型</label>
                <select v-model="newResource.resourceType" class="form-select">
                  <option value="PDF">📄 PDF文档</option>
                  <option value="视频">🎬 视频</option>
                  <option value="文章">📝 文章</option>
                  <option value="链接">🔗 链接</option>
                  <option value="其他">📦 其他</option>
                </select>
              </div>
              <div class="form-group">
                <label>上传文件 <span class="required">*</span></label>
                <div class="file-upload-area">
                  <input
                    ref="fileInputRef"
                    type="file"
                    class="file-input-hidden"
                    @change="handleFileChange"
                  />
                  <button type="button" class="file-select-btn" @click="triggerFileInput">
                    📁 选择本地文件
                  </button>
                  <span v-if="uploadFileName" class="file-name-display">
                    已选择：{{ uploadFileName }} <span v-if="uploadFileSize">({{ formatFileSize(uploadFileSize) }})</span>
                  </span>
                  <span v-else class="file-name-hint">支持 PDF、视频、文章等文件</span>
                </div>
              </div>
            </div>
            <div class="upload-modal-footer">
              <button class="cancel-btn" @click="showUploadModal = false">取消</button>
              <button class="confirm-btn" @click="handleUploadResource">上传</button>
            </div>
          </div>
        </div>

        <div v-if="activeTab === 'ranking'" class="ranking-tab">
          <div class="ranking-podium">
            <div class="podium-container">
              <div v-if="memberRanking[1]" class="podium-item second">
                <div class="medal">{{ getMedal(2) }}</div>
                <div class="podium-avatar silver">
                  <img v-if="memberRanking[1].avatarUrl" :src="memberRanking[1].avatarUrl" alt="头像" class="avatar-img" />
                  <span v-else>{{ memberRanking[1].avatar }}</span>
                </div>
                <span class="podium-name">{{ memberRanking[1].name }}</span>
                <span class="podium-score">{{ getMemberScore(memberRanking[1]) }} 分</span>
                <div class="podium-base silver">2</div>
              </div>
              <div v-if="memberRanking[0]" class="podium-item first">
                <div class="crown">👑</div>
                <div class="medal">{{ getMedal(1) }}</div>
                <div class="podium-avatar gold">
                  <img v-if="memberRanking[0].avatarUrl" :src="memberRanking[0].avatarUrl" alt="头像" class="avatar-img" />
                  <span v-else>{{ memberRanking[0].avatar }}</span>
                </div>
                <span class="podium-name">{{ memberRanking[0].name }}</span>
                <span class="podium-score">{{ getMemberScore(memberRanking[0]) }} 分</span>
                <div class="podium-base gold">1</div>
              </div>
              <div v-if="memberRanking[2]" class="podium-item third">
                <div class="medal">{{ getMedal(3) }}</div>
                <div class="podium-avatar bronze">
                  <img v-if="memberRanking[2].avatarUrl" :src="memberRanking[2].avatarUrl" alt="头像" class="avatar-img" />
                  <span v-else>{{ memberRanking[2].avatar }}</span>
                </div>
                <span class="podium-name">{{ memberRanking[2].name }}</span>
                <span class="podium-score">{{ getMemberScore(memberRanking[2]) }} 分</span>
                <div class="podium-base bronze">3</div>
              </div>
            </div>
          </div>

          <div class="ranking-table-wrapper">
            <div class="ranking-table-header">
              <span class="r-col rank-col">排名</span>
              <span class="r-col member-col">成员</span>
              <span class="r-col">等级</span>
              <span class="r-col">通关数</span>
              <span class="r-col">资源数</span>
              <span class="r-col">连续天数</span>
              <span class="r-col">发言数</span>
              <span class="r-col score-col">综合评分</span>
            </div>
            <div
              v-for="(member, index) in memberRanking"
              :key="member.id"
              class="ranking-row"
            >
              <div class="r-col rank-col">
                <span class="rank-num">{{ getMedal(index + 1) || (index + 1) }}</span>
              </div>
              <div class="r-col member-col">
                <div class="r-avatar">
                  <img v-if="member.avatarUrl" :src="member.avatarUrl" alt="头像" class="avatar-img" />
                  <span v-else>{{ member.avatar }}</span>
                </div>
                <div class="r-member-info">
                  <span class="r-member-name">{{ (member.nickname && !/^\d+$/.test(member.nickname) ? member.nickname : member.username) || ('用户'+member.userId) }}</span>
                  <span :class="['r-role-tag', member.role]">
                    {{ member.role === 'owner' ? '组长' : '成员' }}
                  </span>
                </div>
              </div>
              <div class="r-col">
                <span class="r-level">Lv.{{ member.level }}</span>
              </div>
              <div class="r-col">
                <span class="r-stat">✅ {{ member.passCount }}</span>
              </div>
              <div class="r-col">
                <span class="r-stat">📚 {{ member.resourceCount }}</span>
              </div>
              <div class="r-col">
                <span class="r-stat">🔥 {{ member.streak }}</span>
              </div>
              <div class="r-col">
                <span class="r-stat">💬 {{ member.posts }}</span>
              </div>
              <div class="r-col score-col">
                <span class="r-score">{{ getMemberScore(member) }}</span>
              </div>
            </div>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.group-detail-page {
  min-height: calc(100vh - 70px - 60px);
}

.page-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 40px 20px;
}

.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 10px 18px;
  background: white;
  border: 2px solid #e2e8f0;
  border-radius: 12px;
  color: #4a5568;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
  margin-bottom: 24px;
}

.back-btn:hover {
  background: #f7fafc;
  border-color: #667eea;
  color: #667eea;
}

.not-found {
  text-align: center;
  padding: 80px 20px;
}

.not-found-icon {
  font-size: 64px;
  display: block;
  margin-bottom: 20px;
}

.not-found h2 {
  font-size: 24px;
  font-weight: 700;
  color: #2d3748;
  margin-bottom: 8px;
}

.not-found p {
  color: #718096;
  margin-bottom: 24px;
}

.back-home-btn {
  padding: 12px 28px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s;
}

.back-home-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4);
}

/* Header Card */
.group-header-card {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 20px;
  padding: 32px;
  color: white;
  margin-bottom: 24px;
}

.header-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 24px;
  flex-wrap: wrap;
}

.group-identity {
  display: flex;
  gap: 20px;
  flex: 1;
}

.group-icon-wrapper {
  width: 64px;
  height: 64px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.2);
  backdrop-filter: blur(10px);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.group-icon {
  font-size: 32px;
}

.group-info {
  flex: 1;
}

.group-name {
  font-size: 26px;
  font-weight: 700;
  margin-bottom: 8px;
}

.group-desc {
  font-size: 15px;
  opacity: 0.9;
  line-height: 1.5;
  margin-bottom: 16px;
}

.group-meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.meta-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  background: rgba(255, 255, 255, 0.15);
  border-radius: 20px;
  font-size: 13px;
  font-weight: 500;
  backdrop-filter: blur(10px);
}

.owner-badge {
  background: rgba(255, 215, 0, 0.25);
}

.header-actions {
  flex-shrink: 0;
}

.join-btn, .leave-btn {
  padding: 12px 28px;
  border-radius: 14px;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
}

.join-btn {
  background: white;
  color: #667eea;
  border: none;
}

.join-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.2);
}

.join-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.leave-btn {
  background: rgba(255, 255, 255, 0.15);
  color: white;
  border: 2px solid rgba(255, 255, 255, 0.3);
}

.leave-btn:hover {
  background: rgba(229, 62, 62, 0.3);
  border-color: #fc8181;
}

.topics-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid rgba(255, 255, 255, 0.2);
}

.topic-tag {
  padding: 6px 14px;
  background: rgba(255, 255, 255, 0.15);
  border-radius: 20px;
  font-size: 13px;
  font-weight: 500;
}

/* Tabs */
.tabs-wrapper {
  display: flex;
  gap: 8px;
  margin-bottom: 24px;
  background: white;
  padding: 6px;
  border-radius: 14px;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.05);
}

.tab-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 14px 20px;
  border: none;
  border-radius: 10px;
  background: none;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  color: #718096;
  transition: all 0.3s ease;
}

.tab-btn:hover {
  background: #f7fafc;
  color: #667eea;
}

.tab-btn.active {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.tab-count {
  font-size: 12px;
  padding: 2px 8px;
  background: rgba(0, 0, 0, 0.1);
  border-radius: 10px;
}

.tab-btn.active .tab-count {
  background: rgba(255, 255, 255, 0.3);
}

/* Post Composer */
.post-composer {
  background: white;
  border-radius: 16px;
  padding: 20px;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.06);
  margin-bottom: 20px;
}

.composer-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.composer-avatar {
  font-size: 24px;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.1) 0%, rgba(118, 75, 162, 0.1) 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.composer-avatar .avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.composer-label {
  font-size: 14px;
  font-weight: 600;
  color: #4a5568;
}

.composer-textarea {
  width: 100%;
  padding: 14px 16px;
  border: 2px solid #e2e8f0;
  border-radius: 12px;
  font-size: 14px;
  color: #2d3748;
  background: #f7fafc;
  outline: none;
  resize: vertical;
  min-height: 90px;
  font-family: inherit;
  transition: all 0.3s ease;
  box-sizing: border-box;
}

.composer-textarea:focus {
  border-color: #667eea;
  background: white;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
}

.composer-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 12px;
}

.char-count {
  font-size: 12px;
  color: #a0aec0;
}

.post-submit-btn {
  padding: 10px 28px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s;
}

.post-submit-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4);
}

.post-submit-btn:disabled {
  background: #cbd5e0;
  cursor: not-allowed;
}

/* Join Prompt */
.join-prompt {
  text-align: center;
  padding: 40px 20px;
  background: white;
  border-radius: 16px;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.06);
  margin-bottom: 20px;
}

.prompt-icon {
  font-size: 40px;
  display: block;
  margin-bottom: 12px;
}

.join-prompt p {
  color: #718096;
  margin-bottom: 16px;
}

.join-prompt-btn {
  padding: 10px 28px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s;
}

.join-prompt-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4);
}

.empty-posts, .empty-resources {
  text-align: center;
  padding: 60px 20px;
  background: white;
  border-radius: 16px;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.06);
}

.empty-posts .empty-icon, .empty-resources .empty-icon {
  font-size: 48px;
  display: block;
  margin-bottom: 12px;
}

.empty-posts p, .empty-resources p {
  color: #718096;
  font-size: 14px;
  margin: 0;
}

.empty-posts .empty-hint, .empty-resources .empty-hint {
  color: #a0aec0;
  font-size: 12px;
  margin-top: 8px;
}

/* Posts List */
.posts-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.post-card {
  background: white;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.06);
  transition: all 0.3s ease;
}

.post-card:hover {
  box-shadow: 0 8px 25px rgba(102, 126, 234, 0.1);
}

.post-header {
  margin-bottom: 14px;
}

.post-author {
  display: flex;
  align-items: center;
  gap: 12px;
}

.author-avatar {
  font-size: 28px;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.1) 0%, rgba(118, 75, 162, 0.1) 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.author-avatar .avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.author-info {
  display: flex;
  flex-direction: column;
}

.author-name {
  font-size: 15px;
  font-weight: 600;
  color: #2d3748;
}

.post-time {
  font-size: 12px;
  color: #a0aec0;
}

.post-body {
  margin-bottom: 16px;
}

.post-content {
  font-size: 15px;
  color: #4a5568;
  line-height: 1.7;
}

.post-actions {
  display: flex;
  gap: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid #edf2f7;
}

.action-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: none;
  border: none;
  cursor: pointer;
  font-size: 13px;
  color: #a0aec0;
  border-radius: 8px;
  transition: all 0.2s;
}

.action-btn:hover {
  background: #f7fafc;
  color: #4a5568;
}

.action-btn.liked {
  color: #e53e3e;
}

.action-btn.delete-btn:hover {
  background: #fef2f2;
  color: #e53e3e;
}

/* Comments */
.comments-section {
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.comment-item {
  display: flex;
  gap: 10px;
  padding: 12px;
  background: #f7fafc;
  border-radius: 12px;
}

.comment-avatar {
  font-size: 18px;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.1) 0%, rgba(118, 75, 162, 0.1) 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  overflow: hidden;
}

.comment-avatar .avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.comment-body {
  flex: 1;
}

.comment-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 4px;
}

.comment-header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.comment-delete-btn {
  padding: 2px 8px;
  background: none;
  border: none;
  color: #a0aec0;
  font-size: 11px;
  cursor: pointer;
  border-radius: 4px;
  transition: all 0.2s;
}

.comment-delete-btn:hover {
  background: #fef2f2;
  color: #e53e3e;
}

.comment-author {
  font-size: 13px;
  font-weight: 600;
  color: #2d3748;
}

.comment-time {
  font-size: 11px;
  color: #a0aec0;
}

.comment-content {
  font-size: 14px;
  color: #4a5568;
  line-height: 1.5;
}

/* Reply Composer */
.reply-composer {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.reply-input {
  flex: 1;
  padding: 10px 14px;
  border: 2px solid #e2e8f0;
  border-radius: 10px;
  font-size: 13px;
  color: #2d3748;
  background: #f7fafc;
  outline: none;
  transition: all 0.3s;
}

.reply-input:focus {
  border-color: #667eea;
  background: white;
}

.reply-submit-btn {
  padding: 10px 18px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.reply-submit-btn:hover:not(:disabled) {
  transform: scale(1.05);
}

.reply-submit-btn:disabled {
  background: #cbd5e0;
  cursor: not-allowed;
}

/* Members Tab */
.members-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 16px;
}

.empty-members {
  text-align: center;
  padding: 60px 20px;
  background: white;
  border-radius: 16px;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.06);
}

.empty-icon {
  font-size: 48px;
  display: block;
  margin-bottom: 12px;
}

.empty-members p {
  color: #718096;
  font-size: 14px;
}

.member-card {
  background: white;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.06);
  text-align: center;
  transition: all 0.3s ease;
}

.member-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(102, 126, 234, 0.15);
}

.member-avatar-wrapper {
  position: relative;
  display: inline-block;
  margin-bottom: 12px;
}

.member-avatar {
  font-size: 40px;
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.1) 0%, rgba(118, 75, 162, 0.1) 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.member-avatar .avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.owner-crown {
  position: absolute;
  top: -8px;
  right: -4px;
  font-size: 18px;
}

.member-info {
  margin-bottom: 16px;
}

.member-name {
  display: block;
  font-size: 16px;
  font-weight: 600;
  color: #2d3748;
  margin-bottom: 4px;
}

.member-role {
  font-size: 12px;
  padding: 2px 12px;
  border-radius: 10px;
  font-weight: 500;
}

.member-role.owner {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.member-role.member {
  background: #edf2f7;
  color: #4a5568;
}

.member-stats {
  display: flex;
  justify-content: center;
  gap: 24px;
  padding-top: 16px;
  border-top: 1px solid #edf2f7;
}

.member-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.stat-label {
  font-size: 11px;
  color: #a0aec0;
}

.stat-value {
  font-size: 14px;
  font-weight: 600;
  color: #667eea;
}

/* Resources Tab */
.resource-upload-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 24px;
  background: white;
  border-radius: 16px;
  border: 2px dashed #e2e8f0;
  margin-bottom: 20px;
  font-size: 14px;
  color: #718096;
}

.upload-btn {
  padding: 8px 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 20px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s;
}

.upload-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4);
}

.resources-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.resource-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 18px 20px;
  background: white;
  border-radius: 14px;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.05);
  transition: all 0.3s ease;
}

.resource-card:hover {
  box-shadow: 0 8px 25px rgba(102, 126, 234, 0.1);
}

.resource-icon-wrapper {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.1) 0%, rgba(118, 75, 162, 0.1) 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.resource-icon {
  font-size: 20px;
}

.resource-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.resource-name {
  font-size: 15px;
  font-weight: 600;
  color: #2d3748;
}

.resource-meta {
  font-size: 12px;
  color: #a0aec0;
}

.download-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s;
  flex-shrink: 0;
}

.download-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4);
}

.open-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: linear-gradient(135deg, #48bb78 0%, #38a169 100%);
  color: white;
  border: none;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  margin-right: 8px;
}

.open-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 15px rgba(72, 187, 120, 0.4);
}

/* Ranking Tab */
.ranking-tab {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.ranking-podium {
  margin-bottom: 8px;
}

.podium-container {
  display: flex;
  justify-content: center;
  align-items: flex-end;
  gap: 30px;
  padding: 40px;
  background: linear-gradient(135deg, #f7fafc 0%, #edf2f7 100%);
  border-radius: 20px;
  min-height: 280px;
}

.podium-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  position: relative;
}

.podium-item.first { order: 2; }
.podium-item.second { order: 1; }
.podium-item.third { order: 3; }

.crown {
  font-size: 36px;
  position: absolute;
  top: -50px;
  animation: bounce 2s ease-in-out infinite;
}

.medal {
  font-size: 28px;
  margin-bottom: 8px;
}

.podium-avatar {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  margin-bottom: 10px;
  border: 4px solid white;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.15);
  overflow: hidden;
}

.podium-avatar .avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.podium-avatar.gold {
  background: linear-gradient(135deg, #fbbf24 0%, #f59e0b 100%);
}

.podium-avatar.silver {
  background: linear-gradient(135deg, #e2e8f0 0%, #cbd5e0 100%);
}

.podium-avatar.bronze {
  background: linear-gradient(135deg, #d97706 0%, #b45309 100%);
}

.podium-name {
  font-size: 15px;
  font-weight: 600;
  color: #2d3748;
  margin-bottom: 4px;
}

.podium-score {
  font-size: 13px;
  color: #667eea;
  font-weight: 600;
  margin-bottom: 12px;
}

.podium-base {
  width: 90px;
  padding: 10px;
  text-align: center;
  font-weight: 700;
  color: white;
  border-radius: 8px 8px 0 0;
  font-size: 18px;
}

.podium-base.gold {
  height: 90px;
  background: linear-gradient(135deg, #fbbf24 0%, #f59e0b 100%);
}

.podium-base.silver {
  height: 70px;
  background: linear-gradient(135deg, #e2e8f0 0%, #cbd5e0 100%);
  color: #4a5568;
}

.podium-base.bronze {
  height: 50px;
  background: linear-gradient(135deg, #d97706 0%, #b45309 100%);
}

@keyframes bounce {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-10px); }
}

.ranking-table-wrapper {
  background: white;
  border-radius: 16px;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.05);
  overflow: hidden;
}

.ranking-table-header {
  display: grid;
  grid-template-columns: 60px 1.5fr 1fr 1fr 1fr 1fr 1fr 1fr;
  gap: 4px;
  align-items: center;
  padding: 14px 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  font-weight: 600;
  font-size: 13px;
}

.ranking-table-header .r-col {
  text-align: center;
}

.ranking-row {
  display: grid;
  grid-template-columns: 60px 1.5fr 1fr 1fr 1fr 1fr 1fr 1fr;
  gap: 4px;
  align-items: center;
  padding: 12px 20px;
  border-bottom: 1px solid #e2e8f0;
  transition: all 0.3s ease;
}

.ranking-row:hover {
  background: #f7fafc;
}

.r-col {
  text-align: center;
  font-size: 13px;
  color: #4a5568;
}

.r-col.rank-col {
  display: flex;
  align-items: center;
  justify-content: center;
}

.rank-num {
  font-size: 18px;
  font-weight: 700;
  color: #667eea;
}

.r-col.member-col {
  display: flex;
  align-items: center;
  gap: 10px;
  text-align: left;
}

.r-avatar {
  font-size: 28px;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.1) 0%, rgba(118, 75, 162, 0.1) 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  overflow: hidden;
}

.r-avatar .avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.r-member-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.r-member-name {
  font-size: 14px;
  font-weight: 600;
  color: #2d3748;
}

.r-role-tag {
  font-size: 11px;
  padding: 1px 8px;
  border-radius: 8px;
  font-weight: 500;
  width: fit-content;
}

.r-role-tag.owner {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.r-role-tag.member {
  background: #edf2f7;
  color: #4a5568;
}

.r-level {
  color: #667eea;
  font-weight: 600;
  font-size: 13px;
}

.r-stat {
  font-size: 13px;
  color: #4a5568;
}

.r-col.score-col .r-score {
  font-weight: 700;
  color: #667eea;
  font-size: 14px;
}

@media (max-width: 768px) {
  .header-top {
    flex-direction: column;
  }

  .group-identity {
    flex-direction: column;
    align-items: flex-start;
  }

  .header-actions {
    width: 100%;
  }

  .join-btn, .leave-btn {
    width: 100%;
  }

  .group-meta-row {
    flex-direction: column;
  }

  .members-grid {
    grid-template-columns: 1fr;
  }

  .resource-card {
    flex-wrap: wrap;
  }

  .podium-container {
    flex-direction: column;
    align-items: center;
    gap: 16px;
    padding: 20px;
  }

  .podium-item.first { order: 1; }
  .podium-item.second { order: 2; }
  .podium-item.third { order: 3; }

  .ranking-table-header {
    display: none;
  }

  .ranking-row {
    flex-wrap: wrap;
    gap: 8px;
    padding: 16px;
  }

  .ranking-row .r-col {
    width: auto;
  }
}

.resource-upload-section {
  background: linear-gradient(135deg, #f7fafc 0%, #edf2f7 100%);
  border-radius: 16px;
  padding: 24px;
  margin-bottom: 24px;
  text-align: center;
}

.upload-btn {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 12px;
  padding: 14px 28px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  margin-bottom: 12px;
}

.upload-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(102, 126, 234, 0.4);
}

.upload-hint {
  color: #718096;
  font-size: 14px;
  margin: 0;
}

.resource-actions {
  display: flex;
  gap: 8px;
}

.delete-resource-btn {
  background: #fff5f5;
  color: #e53e3e;
  border: 1px solid #feb2b2;
  border-radius: 8px;
  padding: 8px 16px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.delete-resource-btn:hover {
  background: #e53e3e;
  color: white;
  border-color: #e53e3e;
}

.upload-modal-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
}

.upload-modal {
  background: white;
  border-radius: 20px;
  width: 90%;
  max-width: 500px;
  max-height: 90vh;
  overflow-y: auto;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.2);
}

.upload-modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  border-bottom: 1px solid #edf2f7;
}

.upload-modal-header h3 {
  margin: 0;
  font-size: 20px;
  color: #2d3748;
}

.modal-close-btn {
  background: none;
  border: none;
  font-size: 28px;
  color: #a0aec0;
  cursor: pointer;
  line-height: 1;
  padding: 0;
}

.modal-close-btn:hover {
  color: #2d3748;
}

.upload-modal-body {
  padding: 24px;
}

.upload-modal-body .form-group {
  margin-bottom: 18px;
}

.upload-modal-body label {
  display: block;
  margin-bottom: 6px;
  font-size: 14px;
  font-weight: 600;
  color: #4a5568;
}

.upload-modal-body .required {
  color: #e53e3e;
}

.upload-modal-body .form-input,
.upload-modal-body .form-textarea,
.upload-modal-body .form-select {
  width: 100%;
  padding: 10px 14px;
  border: 2px solid #e2e8f0;
  border-radius: 8px;
  font-size: 14px;
  font-family: inherit;
  outline: none;
  transition: all 0.3s ease;
  box-sizing: border-box;
}

.upload-modal-body .form-textarea {
  resize: vertical;
  min-height: 60px;
}

.upload-modal-body .form-input:focus,
.upload-modal-body .form-textarea:focus,
.upload-modal-body .form-select:focus {
  border-color: #667eea;
}

.upload-modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 24px;
  border-top: 1px solid #edf2f7;
}

.cancel-btn {
  background: #f7fafc;
  color: #4a5568;
  border: none;
  border-radius: 8px;
  padding: 10px 20px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.cancel-btn:hover {
  background: #edf2f7;
}

.confirm-btn {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 8px;
  padding: 10px 20px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
}

.confirm-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.file-upload-area {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  padding: 16px;
  background: #f7fafc;
  border: 2px dashed #cbd5e0;
  border-radius: 8px;
  transition: all 0.3s ease;
}

.file-upload-area:hover {
  border-color: #667eea;
  background: #edf2f7;
}

.file-input-hidden {
  display: none;
}

.file-select-btn {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 8px;
  padding: 8px 16px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
}

.file-select-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.file-name-display {
  color: #2d3748;
  font-size: 14px;
  font-weight: 500;
  word-break: break-all;
}

.file-name-hint {
  color: #a0aec0;
  font-size: 13px;
}
</style>