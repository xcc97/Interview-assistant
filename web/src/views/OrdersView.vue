<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { cancelOrder, createPayment, getOrders, getRuntimeConfig, mockPaymentPaid, syncPaidOrder } from '../api';

const router = useRouter();
const orders = ref([]);
const loading = ref(false);
const payingOrderId = ref('');
const cancelingOrderId = ref('');
const syncingOrderId = ref('');
const creatingPaymentOrderId = ref('');
const paymentResult = ref(null);
const paymentPollingText = ref('');
let paymentPollingTimer = null;
const errorText = ref('');
const successText = ref('');
const runtimeConfig = getRuntimeConfig();

const orderRules = [
  '待支付订单不会扣减余额，完成支付后时长会自动到账。',
  '如遇支付后未到账，请联系客服处理。',
  '已到账套餐可在用户中心查看剩余时长和有效期。',
];

function statusText(status) {
  if (status === 'PAID') return '已支付';
  if (status === 'PENDING') return '待支付';
  if (status === 'CLOSED') return '已取消';
  return status;
}

function paymentChannelText(channel) {
  if (channel === 'WECHAT') return '微信支付';
  if (channel === 'ALIPAY') return '支付宝';
  return channel || '-';
}

function orderIdText(order) {
  const orderId = String(order.id || order.orderId || '');
  if (!orderId) return '订单号：-';
  return `订单尾号：${orderId.slice(-8)}`;
}

function formatMinutesAsDuration(minutes) {
  const totalSeconds = Math.max(0, (Number(minutes) || 0) * 60);
  const hours = Math.floor(totalSeconds / 3600);
  const restMinutes = Math.floor((totalSeconds % 3600) / 60);
  if (hours > 0) {
    return `${hours}小时${restMinutes}分`;
  }
  return `${restMinutes}分钟`;
}

function formatDateTime(value) {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return String(value).replace('T', ' ').replace(/\.\d+Z?$/, '').replace(/Z$/, '');
  }
  const pad = (number) => String(number).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

function qrImageUrl(codeUrl) {
  return `https://api.qrserver.com/v1/create-qr-code/?size=220x220&data=${encodeURIComponent(codeUrl)}`;
}

function clearPaymentPolling() {
  if (paymentPollingTimer) {
    clearInterval(paymentPollingTimer);
    paymentPollingTimer = null;
  }
  paymentPollingText.value = '';
}

async function loadOrders(showSuccess = false) {
  loading.value = true;
  errorText.value = '';
  try {
    if (showSuccess && paymentResult.value?.orderId) {
      await trySyncPaidOrder(paymentResult.value.orderId, false);
    }
    orders.value = await getOrders();
    if (showSuccess) {
      successText.value = '订单状态已刷新。';
      if (paymentResult.value && orders.value.some((order) => (order.id || order.orderId) === paymentResult.value.orderId && order.status === 'PAID')) {
        clearPaymentPolling();
        paymentResult.value = null;
        successText.value = '支付已确认，套餐时长已到账。';
      }
    }
  } catch (error) {
    errorText.value = error.message;
  } finally {
    loading.value = false;
  }
}

async function trySyncPaidOrder(orderId, showMessage = true) {
  if (!orderId) {
    return null;
  }
  syncingOrderId.value = orderId;
  if (showMessage) {
    errorText.value = '';
    successText.value = '';
  }
  try {
    const syncedOrder = await syncPaidOrder(orderId);
    if (syncedOrder?.status === 'PAID') {
      clearPaymentPolling();
      if (paymentResult.value?.orderId === orderId) {
        paymentResult.value = null;
      }
      successText.value = '支付已确认，套餐时长已到账。';
    } else if (showMessage) {
      successText.value = '已同步支付状态，请查看订单最新状态。';
    }
    return syncedOrder;
  } catch (error) {
    if (showMessage) {
      errorText.value = error.message;
    }
    return null;
  } finally {
    syncingOrderId.value = '';
  }
}

async function handleSyncPaidOrder(order) {
  const orderId = order.id || order.orderId;
  await trySyncPaidOrder(orderId, true);
  await loadOrders(false);
}

function startPaymentPolling(orderId) {
  clearPaymentPolling();
  let remainingSeconds = 120;
  paymentPollingText.value = '正在等待支付结果确认...';
  paymentPollingTimer = setInterval(async () => {
    remainingSeconds -= 5;
    await trySyncPaidOrder(orderId, false);
    await loadOrders(false);
    const paid = orders.value.some((order) => (order.id || order.orderId) === orderId && order.status === 'PAID');
    if (paid) {
      clearPaymentPolling();
      paymentResult.value = null;
      successText.value = '支付已确认，套餐时长已到账。';
      return;
    }
    if (remainingSeconds <= 0) {
      clearPaymentPolling();
      paymentPollingText.value = '暂未确认到账，你可以稍后刷新订单状态或联系客服。';
      return;
    }
    paymentPollingText.value = `正在等待支付结果确认，约 ${remainingSeconds} 秒后停止自动刷新。`;
  }, 5000);
}

async function handleMockPay(orderId) {
  payingOrderId.value = orderId;
  errorText.value = '';
  successText.value = '';
  try {
    const result = await mockPaymentPaid({ orderId });
    successText.value = `订单 ${result.id || result.orderId} 已完成支付，时长已到账。`;
    await loadOrders();
  } catch (error) {
    errorText.value = error.message;
  } finally {
    payingOrderId.value = '';
  }
}

async function handleCancelOrder(order) {
  const orderId = order.id || order.orderId;
  if (!window.confirm(`确认取消${orderIdText(order)}吗？取消后这笔待支付订单将关闭。`)) {
    return;
  }
  cancelingOrderId.value = orderId;
  errorText.value = '';
  successText.value = '';
  try {
    await cancelOrder(orderId);
    successText.value = `${orderIdText(order)} 已取消。`;
    await loadOrders();
  } catch (error) {
    errorText.value = error.message;
  } finally {
    cancelingOrderId.value = '';
  }
}

async function handleCreatePayment(order, paymentChannel) {
  const orderId = order.id || order.orderId;
  creatingPaymentOrderId.value = `${orderId}-${paymentChannel}`;
  errorText.value = '';
  successText.value = '';
  paymentResult.value = null;
  try {
    const result = await createPayment({ orderId, paymentChannel });
    paymentResult.value = result;
    startPaymentPolling(orderId);
    if (result.paymentForm) {
      const paymentWindow = window.open('', '_blank');
      if (paymentWindow) {
        paymentWindow.document.write(result.paymentForm);
        paymentWindow.document.close();
      } else {
        errorText.value = '浏览器阻止了支付窗口，请允许弹窗后重试。';
      }
    } else {
      successText.value = result.message || '支付订单已创建，请按页面提示完成支付。';
    }
  } catch (error) {
    errorText.value = error.message;
  } finally {
    creatingPaymentOrderId.value = '';
  }
}

function contactSupport(orderId) {
  router.push({ name: 'contact', query: { orderId } });
}

onMounted(loadOrders);
onBeforeUnmount(clearPaymentPolling);
</script>

<template>
  <section class="page-section">
    <div class="page-heading">
      <div>
        <p class="eyebrow">Orders</p>
        <h2>我的订单</h2>
        <p>查看你的 nod 套餐订单、支付状态和到账时间。</p>
      </div>
    </div>

    <p v-if="errorText" class="error-text">{{ errorText }}</p>
    <p v-if="successText" class="success-text">{{ successText }}</p>

    <article v-if="paymentResult" class="card payment-card">
      <h3>支付已创建</h3>
      <p>{{ paymentResult.message }}</p>
      <div v-if="paymentResult.codeUrl" class="payment-code-box">
        <img class="payment-qr" :src="qrImageUrl(paymentResult.codeUrl)" alt="微信支付二维码" />
        <div>
          <strong>请使用微信扫码支付</strong>
          <p>请在支付完成后回到本页面刷新状态。</p>
          <small>支付完成后，点击下方按钮刷新订单状态。</small>
        </div>
      </div>
      <div v-else-if="paymentResult.paymentForm" class="payment-code-box">
        <div>
          <strong>支付宝支付页面已打开</strong>
          <p>如果浏览器拦截了新窗口，请允许弹窗后重新点击支付宝支付。</p>
          <small>支付完成后，回到本页面刷新订单状态。</small>
        </div>
      </div>
      <p v-if="paymentPollingText" class="payment-polling-text">{{ paymentPollingText }}</p>
      <div class="payment-card-actions">
        <button class="primary-btn" :disabled="loading" @click="loadOrders(true)">
          {{ loading ? '刷新中...' : '我已完成支付，刷新订单状态' }}
        </button>
        <button class="secondary-btn" @click="contactSupport(paymentResult.orderId)">遇到问题，联系客服</button>
      </div>
    </article>

    <article class="card table-card">
      <div v-if="loading" class="empty-state compact">订单加载中...</div>
      <div v-else class="table-wrapper">
        <table class="data-table">
          <thead>
            <tr>
              <th>订单</th>
              <th>金额</th>
              <th>时长</th>
              <th>状态</th>
              <th>支付方式</th>
              <th>支付时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="order in orders" :key="order.id || order.orderId">
              <td>
                <strong>{{ order.planName }}</strong>
                <p class="compact-note">{{ orderIdText(order) }}</p>
              </td>
              <td>¥{{ order.amount }}</td>
              <td>{{ formatMinutesAsDuration(order.grantedMinutes || order.minutes) }}</td>
              <td><span :class="['status-badge', order.status === 'PAID' ? 'success' : order.status === 'CLOSED' ? 'muted' : 'warning']">{{ statusText(order.status) }}</span></td>
              <td>{{ paymentChannelText(order.paymentChannel) }}</td>
              <td>{{ formatDateTime(order.paidAt) }}</td>
              <td>
                <div v-if="order.status === 'PENDING'" class="payment-actions">
                  <button
                    class="secondary-btn small-btn"
                    :disabled="creatingPaymentOrderId === `${order.id || order.orderId}-WECHAT`"
                    @click="handleCreatePayment(order, 'WECHAT')"
                  >
                    {{ creatingPaymentOrderId === `${order.id || order.orderId}-WECHAT` ? '创建中...' : '微信支付' }}
                  </button>
                  <button
                    class="secondary-btn small-btn"
                    :disabled="creatingPaymentOrderId === `${order.id || order.orderId}-ALIPAY`"
                    @click="handleCreatePayment(order, 'ALIPAY')"
                  >
                    {{ creatingPaymentOrderId === `${order.id || order.orderId}-ALIPAY` ? '创建中...' : '支付宝' }}
                  </button>
                  <button
                    v-if="runtimeConfig.enableMockPayment"
                    class="secondary-btn small-btn"
                    :disabled="payingOrderId === (order.id || order.orderId)"
                    @click="handleMockPay(order.id || order.orderId)"
                  >
                    {{ payingOrderId === (order.id || order.orderId) ? '处理中...' : '完成支付' }}
                  </button>
                  <button
                    v-if="order.paymentChannel === 'ALIPAY'"
                    class="secondary-btn small-btn"
                    :disabled="syncingOrderId === (order.id || order.orderId)"
                    @click="handleSyncPaidOrder(order)"
                  >
                    {{ syncingOrderId === (order.id || order.orderId) ? '同步中...' : '同步支付状态' }}
                  </button>
                  <button
                    class="secondary-btn small-btn"
                    :disabled="cancelingOrderId === (order.id || order.orderId)"
                    @click="handleCancelOrder(order)"
                  >
                    {{ cancelingOrderId === (order.id || order.orderId) ? '取消中...' : '取消订单' }}
                  </button>
                  <button class="secondary-btn small-btn" @click="contactSupport(order.id || order.orderId)">
                    联系客服
                  </button>
                </div>
                <span v-else>--</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </article>

    <article class="card billing-rule-card">
      <h3>订单说明</h3>
      <ol class="ordered-list">
        <li v-for="rule in orderRules" :key="rule">{{ rule }}</li>
      </ol>
    </article>
  </section>
</template>
