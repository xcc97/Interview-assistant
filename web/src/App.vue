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
        { to: '/interview-sessions', label: '面试记录' },
      ]
    : [
        { to: '/', label: '首页' },
        { to: '/billing', label: '价格' },
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
          <img class="brand-logo" src="/nod.svg" alt="" />
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
