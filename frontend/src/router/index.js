import { createRouter, createWebHistory } from 'vue-router';
import Landing from '../views/Landing.vue';
import Login from '../views/Login.vue';
import Home from '../views/Home.vue';
import ResourceCenter from '../views/ResourceCenter.vue';
import PersonalLearning from '../views/PersonalLearning.vue';
import AIAssistant from '../views/AIAssistant.vue';
import Profile from '../views/Profile.vue';
import UserProfileQuiz from '../views/UserProfileQuiz.vue';
import LearningGenerator from '../views/LearningGenerator.vue';
import LearningContent from '../views/LearningContent.vue';
import KnowledgeResource from '../views/KnowledgeResource.vue';
import PlanDetail from '../views/PlanDetail.vue';
import StageDetail from '../views/StageDetail.vue';
import SearchResults from '../views/SearchResults.vue';
import VideoPlayer from '../views/VideoPlayer.vue';
import StudyGroup from '../views/StudyGroups.vue';
import GroupDetail from '../views/GroupDetail.vue';

const routes = [
  {
    path: '/',
    name: 'Landing',
    component: Landing,
    meta: { requiresAuth: false }
  },
  {
    path: '/login',
    name: 'Login',
    component: Login,
    meta: { requiresAuth: false }
  },
  {
    path: '/quiz',
    name: 'UserProfileQuiz',
    component: UserProfileQuiz,
    meta: { requiresAuth: true }
  },
  {
    path: '/home',
    name: 'Home',
    component: Home,
    meta: { requiresAuth: true }
  },
  {
    path: '/resources',
    name: 'ResourceCenter',
    component: ResourceCenter,
    meta: { requiresAuth: true }
  },
  {
    path: '/learning',
    name: 'PersonalLearning',
    component: PersonalLearning,
    meta: { requiresAuth: true }
  },
  {
    path: '/learning/generate',
    name: 'LearningGenerator',
    component: LearningGenerator,
    meta: { requiresAuth: true }
  },
  {
    path: '/learning/content/:goalId',
    name: 'LearningContent',
    component: LearningContent,
    meta: { requiresAuth: true }
  },
  {
    path: '/knowledge/resource',
    name: 'KnowledgeResource',
    component: KnowledgeResource,
    meta: { requiresAuth: true }
  },
  {
    path: '/plan/detail',
    name: 'PlanDetail',
    component: PlanDetail,
    meta: { requiresAuth: true }
  },
  {
    path: '/learning/stage/:goalId/:stageIndex',
    name: 'StageDetail',
    component: StageDetail,
    meta: { requiresAuth: true }
  },
  {
    path: '/search',
    name: 'SearchResults',
    component: SearchResults,
    meta: { requiresAuth: true }
  },
  {
    path: '/video/player',
    name: 'VideoPlayer',
    component: VideoPlayer,
    meta: { requiresAuth: true }
  },
  {
    path: '/community',
    redirect: '/study-groups'
  },
  {
    path: '/assistant',
    name: 'AIAssistant',
    component: AIAssistant,
    meta: { requiresAuth: true }
  },
  {
    path: '/profile',
    name: 'Profile',
    component: Profile,
    meta: { requiresAuth: true }
  },
  {
    path: '/study-groups',
    name: 'StudyGroup',
    component: StudyGroup,
    meta: { requiresAuth: true }
  },
  {
    path: '/group/:id',
    name: 'GroupDetail',
    component: GroupDetail,
    meta: { requiresAuth: true }
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

router.beforeEach((to, from, next) => {
  const isLoggedIn = localStorage.getItem('isLoggedIn') === 'true';
  const profileCompleted = localStorage.getItem('profileCompleted') === 'true';
  
  if (to.meta.requiresAuth && !isLoggedIn) {
    next('/login');
  } else if (to.path === '/' && isLoggedIn) {
    if (!profileCompleted) {
      next('/quiz');
    } else {
      next('/home');
    }
  } else if (to.path === '/home' && isLoggedIn && !profileCompleted) {
    next('/quiz');
  } else {
    next();
  }
});

export default router;