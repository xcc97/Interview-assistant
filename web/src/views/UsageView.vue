<script setup>
import { onMounted, ref } from 'vue';
import { finishUsageSession, getUsageSessions, startUsageSession } from '../api';

const usageRecords = ref([]);
const loading = ref(false);
const operating = ref(false);
const errorText = ref('');
const successText = ref('');

const settlementRules = [
  '每次实时辅助或模拟练习都会生成一条使用记录。',
  '系统会按实际使用时长结算，并同步更新你的剩余分钟数。',
  '你可以通过使用记录了解自己的练习频率和时间投入。',
];

function formatDuration(seconds) {
  if (!seconds && seconds !== 0) return '-';
  const minutes = Math.floor(seconds / 60);
  const restSeconds = seconds % 60;
  return `${minutes} 分 ${restSeconds} 秒`;
}

function sceneText(scene) {
  if (scene === 'INTERVIEW_ASSIST') return '实时面试辅助';
  if (scene === 'MOCK_INTERVIEW') return '模拟面试练习';
  return scene;
}

function statusText(status) {
  if (status === 'ACTIVE') return '进行中';
  if (status === 'SETTLED') return '已结算';
  return status;
}

async function loadUsage() {
  loading.value = true;
  errorText.value = '';
  try {
    usageRecords.value = await getUsageSessions();
  } catch (error) {
    errorText.value = error.message;
  } finally {
    loading.value = false;
  }
}

async function handleStartSession() {
  operating.value = true;
  errorText.value = '';
  successText.value = '';
  try {
    const session = await startUsageSession({ scenario: 'INTERVIEW_ASSIST' });
    successText.value = `会话 ${session.sessionId || session.id} 已开始。`;
    await loadUsage();
  } catch (error) {
    errorText.value = error.message;
  } finally {
    operating.value = false;
  }
}

async function handleFinishSession(sessionId) {
  operating.value = true;
  errorText.value = '';
  successText.value = '';
  try {
    const session = await finishUsageSession({ sessionId });
    successText.value = `会话 ${session.sessionId || session.id} 已完成结算。`;
    await loadUsage();
  } catch (error) {
    errorText.value = error.message;
  } finally {
    operating.value = false;
  }
}

onMounted(loadUsage);
</script>

<template>
  <section class="page-section">
    <div class="page-heading">
      <div>
        <p class="eyebrow">Usage</p>
        <h2>使用记录</h2>
        <p>查看每次使用 nod 的开始时间、持续时长和结算状态。</p>
      </div>
      <button class="primary-btn" :disabled="operating" @click="handleStartSession">
        {{ operating ? '处理中...' : '开始一段新会话' }}
      </button>
    </div>

    <p v-if="errorText" class="error-text">{{ errorText }}</p>
    <p v-if="successText" class="success-text">{{ successText }}</p>

    <article class="card table-card">
      <div v-if="loading" class="empty-state compact">使用记录加载中...</div>
      <div v-else class="table-wrapper">
        <table class="data-table">
          <thead>
            <tr>
              <th>记录号</th>
              <th>场景</th>
              <th>开始时间</th>
              <th>结束时间</th>
              <th>实际时长</th>
              <th>扣费分钟</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="record in usageRecords" :key="record.id || record.sessionId">
              <td>{{ record.id || record.sessionId }}</td>
              <td>{{ sceneText(record.scenario) }}</td>
              <td>{{ record.startedAt }}</td>
              <td>{{ record.endedAt || '-' }}</td>
              <td>{{ formatDuration(record.durationSeconds) }}</td>
              <td>{{ record.chargedMinutes }} 分钟</td>
              <td><span :class="['status-badge', record.status === 'SETTLED' ? 'success' : 'warning']">{{ statusText(record.status) }}</span></td>
              <td>
                <button
                  v-if="record.status === 'ACTIVE'"
                  class="secondary-btn small-btn"
                  :disabled="operating"
                  @click="handleFinishSession(record.id || record.sessionId)"
                >
                  结束并结算
                </button>
                <span v-else>--</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </article>

    <article class="card billing-rule-card">
      <h3>用量说明</h3>
      <ol class="ordered-list">
        <li v-for="rule in settlementRules" :key="rule">{{ rule }}</li>
      </ol>
    </article>
  </section>
</template>
