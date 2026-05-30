<script setup>
import { ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { register, sendRegisterSmsCode, verifyRegisterSmsCode } from '../api';
import { useSessionStore } from '../stores/session';

const router = useRouter();
const route = useRoute();
const session = useSessionStore();
const mode = ref(route.query.mode === 'register' ? 'register' : 'login');
const nickname = ref('');
const phone = ref('');
const smsCode = ref('');
const password = ref('');
const submitting = ref(false);
const sendingCode = ref(false);
const codeSent = ref(false);
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

function normalizePhone(value) {
  return value.trim();
}

function validatePhone(value) {
  return /^1[3-9]\d{9}$/.test(value);
}

async function handleSendCode() {
  errorText.value = '';
  const normalizedPhone = normalizePhone(phone.value);
  if (!validatePhone(normalizedPhone)) {
    errorText.value = '请输入有效的手机号';
    return;
  }
  sendingCode.value = true;
  try {
    await sendRegisterSmsCode({ phone: normalizedPhone });
    codeSent.value = true;
    errorText.value = '验证码已发送，当前环境会直接返回验证码用于测试';
  } catch (error) {
    errorText.value = error.message;
  } finally {
    sendingCode.value = false;
  }
}

async function handleSubmit() {
  submitting.value = true;
  errorText.value = '';
  try {
    const normalizedPhone = normalizePhone(phone.value);
    if (!validatePhone(normalizedPhone)) {
      throw new Error('请输入有效的手机号');
    }
    if (!password.value.trim()) {
      throw new Error('请输入密码');
    }
    if (mode.value === 'register') {
      if (!nickname.value.trim()) {
        throw new Error('请输入昵称');
      }
      if (!smsCode.value.trim()) {
        throw new Error('请输入验证码');
      }
      await verifyRegisterSmsCode({ phone: normalizedPhone, code: smsCode.value.trim() });
      await register({
        nickname: nickname.value.trim(),
        phone: normalizedPhone,
        password: password.value,
        smsCode: smsCode.value.trim(),
      });
    }
    await session.login({
      phone: normalizedPhone,
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
      <h2>{{ mode === 'login' ? '登录 nod 点头' : '手机号注册 nod 账号' }}</h2>
      <p class="auth-desc">账号必须绑定手机号，注册前需要先完成验证码校验。</p>
      <label v-if="mode === 'register'">
        昵称
        <input v-model="nickname" type="text" placeholder="请输入昵称" maxlength="32" />
      </label>
      <label>
        手机号
        <input v-model="phone" type="tel" inputmode="numeric" autocomplete="tel" placeholder="请输入手机号" />
      </label>
      <label v-if="mode === 'register'">
        验证码
        <div class="code-row">
          <input v-model="smsCode" type="text" inputmode="numeric" maxlength="6" placeholder="请输入6位验证码" />
          <button class="secondary-btn" :disabled="sendingCode" @click="handleSendCode">
            {{ sendingCode ? '发送中...' : codeSent ? '重新发送' : '发送验证码' }}
          </button>
        </div>
      </label>
      <label>
        密码
        <input v-model="password" type="password" autocomplete="current-password" placeholder="请输入密码" />
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
