<script setup>
import { computed, onMounted, ref } from 'vue';
import { getReadiness } from '../api';

const readiness = ref(null);
const loading = ref(false);
const errorText = ref('');

const summary = computed(() => {
  if (!readiness.value) {
    return {
      title: '等待检查',
      description: '点击刷新后，nod 会检查上线前的关键配置。',
      className: 'warning',
    };
  }
  if (readiness.value.status === 'READY') {
    return {
      title: '已满足上线基础条件',
      description: '关键配置已通过检查，可以进入最后的人工验收。',
      className: 'success',
    };
  }
  if (readiness.value.status === 'NEEDS_ATTENTION') {
    return {
      title: '仍有配置需要关注',
      description: '当前没有阻塞项，但建议处理警告后再公开发布。',
      className: 'warning',
    };
  }
  return {
    title: '存在阻塞上线的问题',
    description: '请先处理失败项，再进行生产发布。',
    className: 'danger',
  };
});

function statusText(status) {
  if (status === 'PASS') return '通过';
  if (status === 'WARN') return '注意';
  if (status === 'FAIL') return '阻塞';
  return status;
}

function statusClass(status) {
  if (status === 'PASS') return 'success';
  if (status === 'WARN') return 'warning';
  if (status === 'FAIL') return 'danger';
  return 'warning';
}

async function loadReadiness() {
  loading.value = true;
  errorText.value = '';
  try {
    readiness.value = await getReadiness();
  } catch (error) {
    errorText.value = error.message;
  } finally {
    loading.value = false;
  }
}

onMounted(loadReadiness);
</script>

<template>
  <section class="page-section">
    <div class="page-heading">
      <div>
        <p class="eyebrow">Launch Check</p>
        <h2>上线检查</h2>
        <p>检查 nod 点头上线前的关键配置，避免带着默认密钥、未配置支付或未关闭测试能力发布。</p>
      </div>
      <button class="secondary-btn" :disabled="loading" @click="loadReadiness">
        {{ loading ? '检查中...' : '刷新检查' }}
      </button>
    </div>

    <p v-if="errorText" class="error-text">{{ errorText }}</p>

    <article class="card readiness-summary-card" :class="summary.className">
      <div>
        <span>当前状态</span>
        <strong>{{ summary.title }}</strong>
        <p>{{ summary.description }}</p>
      </div>
      <div v-if="readiness" class="readiness-counts">
        <div><strong>{{ readiness.passed }}</strong><span>通过</span></div>
        <div><strong>{{ readiness.warning }}</strong><span>注意</span></div>
        <div><strong>{{ readiness.failed }}</strong><span>阻塞</span></div>
      </div>
    </article>

    <article class="card table-card readiness-table-card">
      <div v-if="loading" class="empty-state compact">正在检查上线配置...</div>
      <div v-else-if="!readiness" class="empty-state compact">暂无检查结果。</div>
      <div v-else class="table-wrapper">
        <table class="data-table readiness-table">
          <thead>
            <tr>
              <th>检查项</th>
              <th>状态</th>
              <th>说明</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in readiness.checks" :key="item.key">
              <td>{{ item.label }}</td>
              <td><span :class="['status-badge', statusClass(item.status)]">{{ statusText(item.status) }}</span></td>
              <td>{{ item.message }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </article>

    <article class="card billing-rule-card">
      <h3>发布前建议</h3>
      <ol class="ordered-list">
        <li>所有阻塞项必须处理完成后再发布生产环境。</li>
        <li>警告项需要人工确认，例如只上线一种支付方式时，另一种支付未启用可以接受。</li>
        <li>支付回调域名必须使用公网可访问的 HTTPS 地址。</li>
        <li>上线后先使用小额订单完整验证注册、购买、支付、到账和使用扣费。</li>
      </ol>
    </article>
  </section>
</template>
