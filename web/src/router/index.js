import { createRouter, createWebHistory } from 'vue-router';
import HomeView from '../views/HomeView.vue';
import LoginView from '../views/LoginView.vue';
import DashboardView from '../views/DashboardView.vue';
import BillingView from '../views/BillingView.vue';
import OrdersView from '../views/OrdersView.vue';
import UsageView from '../views/UsageView.vue';
import ConsoleView from '../views/ConsoleView.vue';
import DownloadView from '../views/DownloadView.vue';
import TermsView from '../views/TermsView.vue';
import PrivacyView from '../views/PrivacyView.vue';
import RefundView from '../views/RefundView.vue';
import ContactView from '../views/ContactView.vue';
import ReadinessView from '../views/ReadinessView.vue';
import AdminOperationsView from '../views/AdminOperationsView.vue';
import { useSessionStore } from '../stores/session';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: HomeView },
    { path: '/login', name: 'login', component: LoginView },
    { path: '/dashboard', name: 'dashboard', component: DashboardView, meta: { requiresAuth: true } },
    { path: '/billing', name: 'billing', component: BillingView, meta: { requiresAuth: true } },
    { path: '/orders', name: 'orders', component: OrdersView, meta: { requiresAuth: true } },
    { path: '/usage', name: 'usage', component: UsageView, meta: { requiresAuth: true } },
    { path: '/console', name: 'console', component: ConsoleView, meta: { requiresAuth: true } },
    { path: '/download', name: 'download', component: DownloadView },
    { path: '/readiness', name: 'readiness', component: ReadinessView, meta: { requiresAuth: true, requiresAdmin: true } },
    { path: '/admin/ops', name: 'admin-ops', component: AdminOperationsView, meta: { requiresAuth: true, requiresAdmin: true } },
    { path: '/terms', name: 'terms', component: TermsView },
    { path: '/privacy', name: 'privacy', component: PrivacyView },
    { path: '/refund', name: 'refund', component: RefundView },
    { path: '/contact', name: 'contact', component: ContactView },
  ],
  scrollBehavior() {
    return { top: 0 };
  },
});

router.beforeEach((to) => {
  const session = useSessionStore();
  if (to.meta.requiresAuth && !session.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } };
  }
  if (to.meta.requiresAdmin && !session.isAdmin) {
    return { name: 'dashboard' };
  }
  if (to.name === 'login' && session.isAuthenticated) {
    return { name: 'dashboard' };
  }
  return true;
});

export default router;
