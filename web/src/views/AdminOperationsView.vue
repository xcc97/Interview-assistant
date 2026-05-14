<script setup>
import { computed, onMounted, ref } from 'vue';
import { adminCloseOrder, adminGrantPaidOrder, getAdminOrders, getPaymentCallbackLogs } from '../api';

const orderStatusFilter = ref('');
const callbackStatusFilter = ref('');
const callbackOrderIdFilter = ref('');
const orders = ref([]);
const callbackLogs = ref([]);
const loadingOrders = ref(false);
const loadingLogs = ref(false);
const operatingOrderId = ref('');
const errorText = ref('');
const successText = ref('');

const orderStats = computed(() => ({
  total: orders.value.length,
  pending: orders.value.filter((order) => order.status === 'PENDING').length,
  paid: orders.value.filter((order) => order.status === 'PAID').length,
  closed: orders.value.filter((order) => order.status === 'CLOSED').length,
}));

function statusText(status) {
  if (status === 'PAID') return '已支付';
  if (status === 'PENDING') return '待支付';
  if (status === 'CLOSED') return '已关闭';
  if (status === 'SUCCESS') return '成功';
  if (status === 'FAILED') return '失败';
  return status || '-';
}

function statusClass(status) {
  if (status === 'PAID' || status === 'SUCCESS') return 'success';
  if (status === 'FAILED' || status === 'CLOSED') return 'danger';
  return 'warning';
}

function paymentChannelText(channel) {
  if (channel === 'WECHAT') return '微信支付';
  if (channel === 'ALIPAY') return '支付宝';
  return channel || '-';
}

function compactText(value, maxLength = 80) {
  if (!value) return '-';
  return value.length > maxLength ? `${value.slice(0, maxLength)}...` : value;
}

function formatDateTime(value) {
  if (!value) return '-';
  return String(value).replace('T', ' ').replace(/\.\d+.*$/, '').replace(/Z$/, '').slice(0, 16);
}

async function loadOrders() {
  loadingOrders.value = true;
  errorText.value = '';
  try {
    orders.value = await getAdminOrders(orderStatusFilter.value);
  } catch (error) {
    errorText.value = error.message;
  } finally {
    loadingOrders.value = false;
  }
}

async function loadCallbackLogs() {
  loadingLogs.value = true;
  errorText.value = '';
  try {
    callbackLogs.value = await getPaymentCallbackLogs({
      orderId: callbackOrderIdFilter.value.trim(),
      status: callbackStatusFilter.value,
    });
  } catch (error) {
    errorText.value = error.message;
  } finally {
    loadingLogs.value = false;
  }
}

async function handleCloseOrder(order) {
  const orderId = order.orderId || order.id;
  const reason = window.prompt(`请输入关闭订单 ${orderId} 的原因`, '管理员手动关闭');
  if (reason === null) return;
  operatingOrderId.value = orderId;
  errorText.value = '';
  successText.value = '';
  try {
    await adminCloseOrder(orderId, { reason });
    successText.value = `订单 ${orderId} 已关闭。`;
    await loadOrders();
  } catch (error) {
    errorText.value = error.message;
  } finally {
    operatingOrderId.value = '';
  }
}

async function handleGrantPaid(order) {
  const orderId = order.orderId || order.id;
  const transactionId = window.prompt(`请输入订单 ${orderId} 的交易号或补单备注`, order.paymentTransactionId || 'MANUAL-GRANT');
  if (transactionId === null) return;
  operatingOrderId.value = orderId;
  errorText.value = '';
  successText.value = '';
  try {
    await adminGrantPaidOrder(orderId, { transactionId, note: '管理台人工补单' });
    successText.value = `订单 ${orderId} 已补单入账。`;
    await loadOrders();
  } catch (error) {
    errorText.value = error.message;
  } finally {
    operatingOrderId.value = '';
  }
}

function inspectCallbacks(order) {
  callbackOrderIdFilter.value = order.orderId || order.id;
  callbackStatusFilter.value = '';
  loadCallbackLogs();
}

onMounted(async () => {
  await Promise.all([loadOrders(), loadCallbackLogs()]);
});
</script>

<template>
  <section class="page-section">
    <div class="page-heading">
      <div>
        <p class="eyebrow">Admin Ops</p>
        <h2>运营管理</h2>
        <p>查看最近订单和支付回调，处理关闭订单、人工补单与失败回调排查。</p>
      </div>
    </div>

    <p v-if="errorText" class="error-text">{{ errorText }}</p>
    <p v-if="successText" class="success-text">{{ successText }}</p>

    <div class="admin-stat-grid">
      <article class="card stat-card"><span>最近订单</span><strong>{{ orderStats.total }}</strong></article>
      <article class="card stat-card"><span>待支付</span><strong>{{ orderStats.pending }}</strong></article>
      <article class="card stat-card"><span>已支付</span><strong>{{ orderStats.paid }}</strong></article>
      <article class="card stat-card"><span>已关闭</span><strong>{{ orderStats.closed }}</strong></article>
    </div>

    <article class="card table-card admin-panel-card">
      <div class="admin-panel-header">
        <div>
          <h3>最近订单</h3>
          <p>默认显示最近 100 条订单，可按状态筛选。</p>
        </div>
        <div class="admin-filter-row">
          <select v-model="orderStatusFilter" class="filter-select">
            <option value="">全部状态</option>
            <option value="PENDING">待支付</option>
            <option value="PAID">已支付</option>
            <option value="CLOSED">已关闭</option>
          </select>
          <button class="secondary-btn" :disabled="loadingOrders" @click="loadOrders">
            {{ loadingOrders ? '加载中...' : '刷新订单' }}
          </button>
        </div>
      </div>

      <div v-if="loadingOrders" class="empty-state compact">订单加载中...</div>
      <div v-else class="table-wrapper">
        <table class="data-table admin-data-table">
          <thead>
            <tr>
              <th>订单号</th>
              <th>套餐</th>
              <th>金额</th>
              <th>状态</th>
              <th>支付方式</th>
              <th>交易号</th>
              <th>创建/支付/关闭</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="order in orders" :key="order.orderId || order.id">
              <td>{{ order.orderId || order.id }}</td>
              <td>{{ order.planName }}</td>
              <td>¥{{ order.amount }}</td>
              <td><span :class="['status-badge', statusClass(order.status)]">{{ statusText(order.status) }}</span></td>
              <td>{{ paymentChannelText(order.paymentChannel) }}</td>
              <td>{{ order.paymentTransactionId || '-' }}</td>
              <td>
                <small>创建：{{ formatDateTime(order.createdAt) }}</small><br />
                <small>支付：{{ formatDateTime(order.paidAt) }}</small><br />
                <small>关闭：{{ formatDateTime(order.closedAt) }}</small>
                <p v-if="order.closeReason" class="compact-note">{{ order.closeReason }}</p>
              </td>
              <td>
                <div class="payment-actions">
                  <button class="secondary-btn small-btn" @click="inspectCallbacks(order)">回调</button>
                  <button
                    v-if="order.status === 'PENDING'"
                    class="secondary-btn small-btn"
                    :disabled="operatingOrderId === (order.orderId || order.id)"
                    @click="handleCloseOrder(order)"
                  >
                    关闭
                  </button>
                  <button
                    v-if="order.status === 'PENDING'"
                    class="secondary-btn small-btn"
                    :disabled="operatingOrderId === (order.orderId || order.id)"
                    @click="handleGrantPaid(order)"
                  >
                    补单
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </article>

    <article class="card table-card admin-panel-card">
      <div class="admin-panel-header">
        <div>
          <h3>支付回调日志</h3>
          <p>查看微信/支付宝回调原始请求、失败原因和处理状态。</p>
        </div>
        <div class="admin-filter-row wide">
          <input v-model="callbackOrderIdFilter" class="filter-input" placeholder="按订单号筛选" />
          <select v-model="callbackStatusFilter" class="filter-select">
            <option value="">全部状态</option>
            <option value="SUCCESS">成功</option>
            <option value="FAILED">失败</option>
          </select>
          <button class="secondary-btn" :disabled="loadingLogs" @click="loadCallbackLogs">
            {{ loadingLogs ? '加载中...' : '刷新回调' }}
          </button>
        </div>
      </div>

      <div v-if="loadingLogs" class="empty-state compact">回调日志加载中...</div>
      <div v-else class="table-wrapper">
        <table class="data-table admin-data-table">
          <thead>
            <tr>
              <th>时间</th>
              <th>渠道</th>
              <th>订单号</th>
              <th>交易号</th>
              <th>状态</th>
              <th>错误</th>
              <th>请求摘要</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="log in callbackLogs" :key="log.id">
              <td>{{ formatDateTime(log.createdAt) }}</td>
              <td>{{ paymentChannelText(log.paymentChannel) }}</td>
              <td>{{ log.orderId || '-' }}</td>
              <td>{{ log.transactionId || '-' }}</td>
              <td><span :class="['status-badge', statusClass(log.status)]">{{ statusText(log.status) }}</span></td>
              <td>{{ log.errorMessage || '-' }}</td>
              <td>
                <details>
                  <summary>{{ compactText(log.requestBody) }}</summary>
                  <pre class="callback-pre">{{ log.requestBody }}</pre>
                  <pre class="callback-pre">{{ log.requestHeaders }}</pre>
                </details>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </article>
  </section>
</template>
