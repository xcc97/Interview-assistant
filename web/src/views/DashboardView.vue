<script setup>
import { computed, onMounted, ref } from 'vue';
import { getBalanceTransactions, getProfile } from '../api';
import { useSessionStore } from '../stores/session';

const session = useSessionStore();
const profile = ref(null);
const transactions = ref([]);
const loading = ref(false);
const errorText = ref('');
const showOpenClientHint = ref(false);

const displayProfile = computed(() => profile.value || session.user || {});
const quickCards = computed(() => [
  { title: '剩余可用时长', value: formatDuration(resolveSeconds(displayProfile.value, 'remaining')), tip: '可用于实时辅助和模拟练习' },
  { title: '已使用时长', value: formatDuration(resolveSeconds(displayProfile.value, 'used')), tip: '帮助你了解近期练习投入' },
  { title: '当前套餐', value: displayProfile.value.currentPlanName || displayProfile.value.currentPlan || '暂无套餐', tip: '购买套餐后会显示在这里' },
  { title: '有效期至', value: formatDate(displayProfile.value.expiryTime || displayProfile.value.expiryDate), tip: '请在有效期内使用已购买时长' },
]);

function resolveSeconds(data, type) {
  if (!data) {
    return 0;
  }
  const secondsKey = type === 'remaining' ? 'remainingSeconds' : 'usedSeconds';
  const minutesKey = type === 'remaining' ? 'remainingMinutes' : 'usedMinutes';
  if (Number.isFinite(data[secondsKey])) {
    return data[secondsKey];
  }
  return (data[minutesKey] || 0) * 60;
}

function formatDuration(totalSeconds) {
  const safeSeconds = Math.max(0, Number(totalSeconds) || 0);
  const minutes = Math.floor(safeSeconds / 60);
  const seconds = safeSeconds % 60;
  return `${minutes}:${String(seconds).padStart(2, '0')}`;
}

function signedDuration(seconds) {
  const value = Number(seconds) || 0;
  const sign = value > 0 ? '+' : value < 0 ? '-' : '';
  return `${sign}${formatDuration(Math.abs(value))}`;
}

function openClient() {
  showOpenClientHint.value = false;
  const startedAt = Date.now();
  window.location.href = 'nod://open';
  window.setTimeout(() => {
    if (Date.now() - startedAt < 2200) {
      showOpenClientHint.value = true;
    }
  }, 1200);
}

function formatDate(value) {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return String(value).replace('T', ' ').slice(0, 16);
  }
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(date);
}

async function loadDashboard() {
  loading.value = true;
  errorText.value = '';
  try {
    const [profileResult, transactionResult] = await Promise.all([
      getProfile(),
      getBalanceTransactions(),
    ]);
    profile.value = profileResult;
    transactions.value = transactionResult.slice(0, 5);
  } catch (error) {
    errorText.value = error.message;
  } finally {
    loading.value = false;
  }
}

onMounted(loadDashboard);
</script>

<template>
  <section class="page-section">
    <div class="page-heading">
      <div>
        <p class="eyebrow">My nod</p>
        <h2>{{ displayProfile.nickname || '你好' }}，欢迎回来</h2>
        <p>查看你的套餐余额、使用情况和最近的时长变化。</p>
      </div>
    </div>

    <p v-if="errorText" class="error-text">{{ errorText }}</p>

    <div class="stats-grid">
      <article v-for="card in quickCards" :key="card.title" class="card stat-card">
        <span>{{ card.title }}</span>
        <strong>{{ card.value }}</strong>
        <p>{{ card.tip }}</p>
      </article>
    </div>

    <article class="card start-client-card">
      <div>
        <span class="status-badge success">开始使用</span>
        <h3>远程面试时，请打开桌面客户端</h3>
        <p>你当前剩余 {{ formatDuration(resolveSeconds(displayProfile, 'remaining')) }}。下载客户端并登录同一账号后，即可在面试前启动实时辅助。</p>
      </div>
      <div class="client-open-actions">
        <button class="primary-btn" @click="openClient">打开 nod 客户端</button>
        <a v-if="showOpenClientHint" class="secondary-btn link-btn" href="/downloads/nod.dmg" download>没有打开？下载客户端</a>
      </div>
    </article>

    <div class="content-grid two-columns">
      <article class="card">
        <h3>最近余额流水</h3>
        <div v-if="transactions.length" class="mini-list">
          <div v-for="item in transactions" :key="item.id" class="mini-list-item">
            <div>
              <strong>{{ item.type }}</strong>
              <p>{{ item.sourceType }} · {{ item.sourceId }}</p>
            </div>
            <span :class="['amount-text', item.seconds > 0 ? 'plus' : 'minus']">
              {{ signedDuration(item.seconds ?? ((item.minutes || 0) * 60)) }}
            </span>
          </div>
        </div>
        <div v-else class="empty-state compact">暂无余额流水，购买套餐后会显示到账记录。</div>
      </article>

      <article class="card">
        <h3>让下一场面试更稳</h3>
        <ol class="ordered-list">
          <li>提前把简历和项目经历整理成关键词。</li>
          <li>用 nod 练习高频问题，熟悉自己的表达节奏。</li>
          <li>面试前确认账户有足够可用时长。</li>
          <li>面试后复盘卡顿问题，持续优化回答结构。</li>
        </ol>
      </article>
    </div>
  </section>
</template>
