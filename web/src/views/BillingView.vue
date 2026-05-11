<script setup>
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { createOrder, getPlans } from '../api';
import { useSessionStore } from '../stores/session';

const router = useRouter();
const route = useRoute();
const session = useSessionStore();
const plans = ref([]);
const loading = ref(false);
const orderingPlan = ref('');
const errorText = ref('');
const successText = ref('');

const billingRules = [
  '购买成功后，套餐分钟数会自动加入你的 nod 账户余额。',
  '使用实时辅助、模拟练习等能力时，将按实际使用时长扣减。',
  '你可以在用户中心查看剩余时长、订单状态和用量记录。',
  '余额不足时，可随时续购套餐继续使用。',
];

async function loadPlans() {
  loading.value = true;
  errorText.value = '';
  try {
    plans.value = await getPlans();
  } catch (error) {
    errorText.value = error.message;
  } finally {
    loading.value = false;
  }
}

async function handleCreateOrder(plan) {
  const planId = plan.planId || plan.id;
  if (!session.isAuthenticated) {
    router.push({ name: 'login', query: { redirect: route.fullPath, planId } });
    return;
  }
  orderingPlan.value = planId;
  errorText.value = '';
  successText.value = '';
  try {
    const order = await createOrder({
      planId,
      paymentChannel: 'WECHAT',
    });
    successText.value = '订单已创建，请继续完成支付。';
    router.push('/orders');
  } catch (error) {
    errorText.value = error.message;
  } finally {
    orderingPlan.value = '';
  }
}

onMounted(loadPlans);
</script>

<template>
  <section class="page-section">
    <div class="page-heading center-heading">
      <p class="eyebrow">Pricing</p>
      <h2>选择适合你的 nod 套餐</h2>
      <p>按分钟购买，按实际使用扣减。适合临时冲刺、密集面试和长期练习。</p>
    </div>

    <p v-if="errorText" class="error-text">{{ errorText }}</p>
    <p v-if="successText" class="success-text">{{ successText }}</p>

    <div v-if="loading" class="card empty-state compact">套餐加载中...</div>
    <div v-else class="pricing-grid">
      <article v-for="plan in plans" :key="plan.planId || plan.id" :class="['card', 'pricing-card', { featured: plan.recommended || plan.featured }]">
        <span v-if="plan.recommended || plan.featured" class="plan-badge">推荐</span>
        <h3>{{ plan.name }}</h3>
        <div class="plan-price">¥{{ plan.price }}</div>
        <p class="plan-minutes">{{ plan.totalMinutes }} 分钟 · {{ plan.validDays }} 天有效</p>
        <ul class="feature-list">
          <li>{{ plan.description }}</li>
          <li>支付成功后自动增加 {{ plan.totalMinutes }} 分钟余额</li>
          <li>支持订单、余额流水和使用记录追踪</li>
        </ul>
        <button class="primary-btn full" :disabled="Boolean(orderingPlan)" @click="handleCreateOrder(plan)">
          {{ orderingPlan === (plan.planId || plan.id) ? '处理中...' : '购买' }}
        </button>
      </article>
    </div>

    <article class="card billing-rule-card">
      <h3>购买与使用说明</h3>
      <ol class="ordered-list">
        <li v-for="rule in billingRules" :key="rule">{{ rule }}</li>
      </ol>
    </article>
  </section>
</template>
