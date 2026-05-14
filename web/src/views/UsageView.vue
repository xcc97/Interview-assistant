<script setup>
import { onMounted, ref } from 'vue';
import { getUsageSessions } from '../api';

const usageRecords = ref([]);
const loading = ref(false);
const errorText = ref('');

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
  if (scene === 'INTERVIEW_ASSIST' || scene === 'DESKTOP_INTERVIEW_ASSIST') return '实时面试辅助';
  if (scene === 'MOCK_INTERVIEW') return '模拟面试练习';
  return '面试辅助';
}

function formatDateTime(value) {
  if (!value) return '-';
  const normalized = String(value).replace('T', ' ').replace(/\.\d+.*$/, '').replace(/Z$/, '');
  return normalized.slice(0, 16);
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
    </div>

    <p v-if="errorText" class="error-text">{{ errorText }}</p>

    <article class="card table-card">
      <div v-if="loading" class="empty-state compact">使用记录加载中...</div>
      <div v-else class="table-wrapper">
        <table class="data-table">
          <thead>
            <tr>
              <th>场景</th>
              <th>开始时间</th>
              <th>结束时间</th>
              <th>实际时长</th>
              <th>扣费分钟</th>
              <th>状态</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="record in usageRecords" :key="record.id || record.sessionId">
              <td>{{ sceneText(record.scenario) }}</td>
              <td>{{ formatDateTime(record.startedAt) }}</td>
              <td>{{ formatDateTime(record.endedAt) }}</td>
              <td>{{ formatDuration(record.durationSeconds) }}</td>
              <td>{{ record.chargedMinutes }} 分钟</td>
              <td><span :class="['status-badge', record.status === 'SETTLED' ? 'success' : 'warning']">{{ statusText(record.status) }}</span></td>
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
