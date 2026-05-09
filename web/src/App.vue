<script setup>
import { computed } from 'vue';
import { RouterLink, RouterView, useRouter } from 'vue-router';
import { useSessionStore } from './stores/session';

const session = useSessionStore();
const router = useRouter();

const navItems = computed(() => {
  const items = session.isAuthenticated
    ? [
        { to: '/', label: '首页' },
        { to: '/billing', label: '价格' },
        { to: '/download', label: '下载客户端' },
        { to: '/dashboard', label: '个人中心' },
      ]
    : [
        { to: '/', label: '首页' },
        { to: '/download', label: '下载客户端' },
      ];
  if (session.isAdmin) {
    items.push({ to: '/readiness', label: '上线检查' });
    items.push({ to: '/admin/ops', label: '运营管理' });
  }
  return items;
});

function handleLogout() {
  session.logout();
  router.push('/');
}
</script>

<template>
  <main class="app-shell">
    <nav class="nav-bar">
      <RouterLink class="brand" to="/" aria-label="nod 点头首页">
        <span class="brand-mark" aria-hidden="true">
          <svg class="brand-logo" viewBox="0 0 64 64" role="img">
            <path class="logo-bubble" d="M32 6C17.64 6 6 16.52 6 29.5S17.64 53 32 53c2.94 0 5.76-.44 8.39-1.25L52.5 58 49.2 46.96C54.61 42.66 58 36.47 58 29.5 58 16.52 46.36 6 32 6Z" />
            <path class="logo-n" d="M19 39V22h6.2l9.7 10.4V22H45v17h-6.2l-9.7-10.4V39H19Z" />
            <path class="logo-nod" d="M22 43c5.8 5.3 14.3 5.3 20 0" />
          </svg>
        </span>
        <span class="brand-text"><strong>nod</strong><small>点头</small></span>
      </RouterLink>
      <div class="nav-actions">
        <RouterLink v-for="item in navItems" :key="item.to" class="nav-link" :to="item.to">
          {{ item.label }}
        </RouterLink>
        <template v-if="!session.isAuthenticated">
          <RouterLink class="nav-link" to="/login?mode=register">注册</RouterLink>
          <RouterLink class="login-pill" to="/login">登录</RouterLink>
        </template>
        <button v-else class="login-pill" @click="handleLogout">退出</button>
      </div>
    </nav>

    <RouterView />

    <footer class="site-footer">
      <div>
        <strong>nod 点头</strong>
        <p>面试时的 AI 应答搭子，帮你把能力表达得更清楚。</p>
      </div>
      <div class="footer-links">
        <RouterLink to="/terms">用户协议</RouterLink>
        <RouterLink to="/privacy">隐私政策</RouterLink>
        <RouterLink to="/refund">退款说明</RouterLink>
        <RouterLink to="/contact">联系我们</RouterLink>
      </div>
    </footer>
  </main>
</template>
