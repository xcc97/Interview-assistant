<script setup>
import { computed, onMounted, ref } from 'vue';
import { getBalanceTransactions, getInterviewSessions, getOrders, getProfile, getUsageSessions } from '../api';
import { useSessionStore } from '../stores/session';

const session = useSessionStore();
const profile = ref(null);
const transactions = ref([]);
const orders = ref([]);
const usageRecords = ref([]);
const interviewSessions = ref([]);
const loading = ref(false);
const errorText = ref('');
const showOpenClientHint = ref(false);

const clientDownloadUrl = computed(() => {
  const platform = navigator.userAgentData?.platform || navigator.platform || navigator.userAgent || '';
  const normalizedPlatform = platform.toLowerCase();
  if (normalizedPlatform.includes('win')) {
    return '/downloads/nod.msi';
  }
  return '/downloads/nod.dmg';
});

const clientInstallerName = computed(() => (clientDownloadUrl.value.endsWith('.msi') ? 'nod.msi' : 'nod.dmg'));

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

function transactionTypeText(type, seconds) {
  if (type === 'GRANT' || type === 'RECHARGE' || Number(seconds) > 0) return '时长增加';
  if (type === 'CONSUME' || type === 'DEDUCT' || Number(seconds) < 0) return '时长扣减';
  if (type === 'REFUND') return '退款返还';
  if (type === 'ADJUST') return '人工调整';
  return type || '余额变动';
}

function transactionSourceText(item) {
  if (item.sourceName) return item.sourceName;
  if (item.sourceType === 'ORDER') return '套餐订单';
  if (item.sourceType === 'SESSION') return '面试使用';
  if (item.sourceType === 'USAGE_SESSION') return '使用记录';
  if (item.sourceType === 'ADMIN') return '人工处理';
  if (item.sourceType === 'REFUND') return '退款';
  return item.sourceType || '系统记录';
}

function statusText(status) {
  if (status === 'PAID') return '已支付';
  if (status === 'PENDING') return '待支付';
  if (status === 'ACTIVE') return '进行中';
  if (status === 'SETTLED') return '已结算';
  return status || '-';
}

function sceneText(scene) {
  if (scene === 'INTERVIEW_ASSIST' || scene === 'DESKTOP_INTERVIEW_ASSIST') return '实时面试辅助';
  if (scene === 'MOCK_INTERVIEW') return '模拟面试练习';
  return scene || '-';
}

function formatDateTime(value) {
  if (!value) {
    return '-';
  }
  return String(value).replace('T', ' ').slice(0, 16);
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
    const [profileResult, transactionResult, orderResult, usageResult, interviewSessionResult] = await Promise.all([
      getProfile(),
      getBalanceTransactions(),
      getOrders(),
      getUsageSessions(),
      getInterviewSessions(),
    ]);
    profile.value = profileResult;
    transactions.value = transactionResult.slice(0, 5);
    orders.value = orderResult.slice(0, 5);
    usageRecords.value = usageResult.slice(0, 5);
    interviewSessions.value = interviewSessionResult.slice(0, 3);
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
        <a v-if="showOpenClientHint" class="secondary-btn link-btn" :href="clientDownloadUrl" :download="clientInstallerName">没有打开？下载客户端</a>
      </div>
    </article>

    <article class="card interview-history-card">
      <div class="card-title-row">
        <div>
          <h3>面试复习记录</h3>
          <p class="compact-note">按每一场面试归档，个人中心只展示最近几场，完整记录请进入详情页查看。</p>
        </div>
        <RouterLink class="secondary-btn small-btn link-btn" to="/interview-sessions">查看全部</RouterLink>
      </div>
      <div v-if="interviewSessions.length" class="mini-list">
        <div v-for="sessionItem in interviewSessions" :key="sessionItem.sessionId" class="mini-list-item">
          <div>
            <strong>{{ formatDateTime(sessionItem.startedAt) }}</strong>
            <p>{{ sessionItem.recordCount }} 条问答 · {{ sessionItem.previewQuestion || '暂无问题摘要' }}</p>
          </div>
          <RouterLink class="secondary-btn small-btn link-btn" :to="{ name: 'interview-session-detail', params: { sessionId: sessionItem.sessionId } }">详情</RouterLink>
        </div>
      </div>
      <div v-else class="empty-state compact">暂无面试复习记录。使用桌面客户端生成回答后，会按面试场次自动归档。</div>
    </article>

    <div class="content-grid two-columns">
      <article class="card">
        <div class="card-title-row">
          <h3>最近订单</h3>
          <RouterLink class="secondary-btn small-btn link-btn" to="/orders">查看全部</RouterLink>
        </div>
        <div v-if="orders.length" class="mini-list">
          <div v-for="order in orders" :key="order.id || order.orderId" class="mini-list-item">
            <div>
              <strong>{{ order.planName || '套餐订单' }}</strong>
              <p>¥{{ order.amount }} · {{ order.grantedMinutes || order.minutes }} 分钟</p>
            </div>
            <span :class="['status-badge', order.status === 'PAID' ? 'success' : 'warning']">{{ statusText(order.status) }}</span>
          </div>
        </div>
        <div v-else class="empty-state compact">暂无订单，购买套餐后会显示在这里。</div>
      </article>

      <article class="card">
        <div class="card-title-row">
          <h3>最近使用记录</h3>
          <RouterLink class="secondary-btn small-btn link-btn" to="/usage">查看全部</RouterLink>
        </div>
        <div v-if="usageRecords.length" class="mini-list">
          <div v-for="record in usageRecords" :key="record.id || record.sessionId" class="mini-list-item">
            <div>
              <strong>{{ sceneText(record.scenario) }}</strong>
              <p>{{ formatDateTime(record.startedAt) }} · {{ formatDuration(record.durationSeconds || 0) }}</p>
            </div>
            <span :class="['status-badge', record.status === 'SETTLED' ? 'success' : 'warning']">{{ statusText(record.status) }}</span>
          </div>
        </div>
        <div v-else class="empty-state compact">暂无使用记录，开始面试后会显示每次使用情况。</div>
      </article>

      <article class="card">
        <div class="card-title-row">
          <h3>最近余额流水</h3>
          <RouterLink class="secondary-btn small-btn link-btn" to="/transactions">查看全部</RouterLink>
        </div>
        <div v-if="transactions.length" class="mini-list">
          <div v-for="item in transactions" :key="item.id" class="mini-list-item">
            <div>
              <strong>{{ transactionTypeText(item.type, item.seconds ?? ((item.minutes || 0) * 60)) }}</strong>
              <p>{{ transactionSourceText(item) }}</p>
            </div>
            <span :class="['amount-text', (item.seconds ?? ((item.minutes || 0) * 60)) > 0 ? 'plus' : 'minus']">
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
