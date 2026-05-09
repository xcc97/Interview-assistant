<script setup>
import { onMounted, ref } from 'vue';
import { getBalanceTransactions } from '../api';

const transactions = ref([]);
const loading = ref(false);
const errorText = ref('');

function formatDuration(seconds) {
  if (!seconds && seconds !== 0) return '-';
  const minutes = Math.floor(seconds / 60);
  const restSeconds = seconds % 60;
  return `${minutes} 分 ${restSeconds} 秒`;
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

function formatDateTime(value) {
  if (!value) return '-';
  return String(value).replace('T', ' ').slice(0, 16);
}

async function loadTransactions() {
  loading.value = true;
  errorText.value = '';
  try {
    transactions.value = await getBalanceTransactions();
  } catch (error) {
    errorText.value = error.message;
  } finally {
    loading.value = false;
  }
}

onMounted(loadTransactions);
</script>

<template>
  <section class="page-section">
    <div class="page-heading">
      <div>
        <p class="eyebrow">Balance</p>
        <h2>余额流水</h2>
        <p>查看套餐到账、面试扣费和余额调整记录。</p>
      </div>
    </div>

    <p v-if="errorText" class="error-text">{{ errorText }}</p>

    <article class="card table-card">
      <div v-if="loading" class="empty-state compact">余额流水加载中...</div>
      <div v-else-if="transactions.length" class="table-wrapper">
        <table class="data-table">
          <thead>
            <tr>
              <th>类型</th>
              <th>来源</th>
              <th>变动时长</th>
              <th>时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in transactions" :key="item.id">
              <td>{{ transactionTypeText(item.type, item.seconds ?? ((item.minutes || 0) * 60)) }}</td>
              <td>{{ transactionSourceText(item) }}</td>
              <td>
                <span :class="['amount-text', (item.seconds ?? ((item.minutes || 0) * 60)) > 0 ? 'plus' : 'minus']">
                  {{ signedDuration(item.seconds ?? ((item.minutes || 0) * 60)) }}
                </span>
              </td>
              <td>{{ formatDateTime(item.createdAt || item.createdTime || item.updatedAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="empty-state compact">暂无余额流水。</div>
    </article>
  </section>
</template>
