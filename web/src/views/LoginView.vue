<script setup>
import { ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { register } from '../api';
import { useSessionStore } from '../stores/session';

const router = useRouter();
const route = useRoute();
const session = useSessionStore();
const mode = ref(route.query.mode === 'register' ? 'register' : 'login');
const nickname = ref('');
const phone = ref('');
const password = ref('');
const submitting = ref(false);
const errorText = ref('');

watch(
  () => route.query.mode,
  (value) => {
    mode.value = value === 'register' ? 'register' : 'login';
    errorText.value = '';
  }
);

function switchMode() {
  const nextMode = mode.value === 'login' ? 'register' : 'login';
  router.replace({
    name: 'login',
    query: nextMode === 'register' ? { ...route.query, mode: 'register' } : { ...route.query, mode: undefined },
  });
}

async function handleSubmit() {
  submitting.value = true;
  errorText.value = '';
  try {
    if (mode.value === 'register') {
      await register({
        nickname: nickname.value,
        phone: phone.value,
        password: password.value,
      });
    }
    await session.login({
      phone: phone.value,
      password: password.value,
    });
    router.push(route.query.redirect || '/dashboard');
  } catch (error) {
    errorText.value = error.message;
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <section class="auth-section">
    <div class="auth-card">
      <p class="eyebrow">Welcome to nod</p>
      <h2>{{ mode === 'login' ? '登录 nod 点头' : '创建 nod 账号' }}</h2>
      <p class="auth-desc">登录后可购买套餐、查看余额，并在面试练习或实时辅助中使用 nod。</p>
      <label v-if="mode === 'register'">
        昵称
        <input v-model="nickname" type="text" placeholder="请输入昵称" />
      </label>
      <label>
        手机号
        <input v-model="phone" type="tel" placeholder="请输入手机号" />
      </label>
      <label>
        密码
        <input v-model="password" type="password" placeholder="请输入密码" />
      </label>
      <p v-if="errorText" class="error-text">{{ errorText }}</p>
      <button class="primary-btn full" :disabled="submitting" @click="handleSubmit">
        {{ submitting ? '处理中...' : mode === 'login' ? '登录' : '注册并登录' }}
      </button>
      <button class="secondary-btn full auth-switch" @click="switchMode">
        {{ mode === 'login' ? '还没有账号？去注册' : '已有账号？去登录' }}
      </button>
    </div>
  </section>
</template>
