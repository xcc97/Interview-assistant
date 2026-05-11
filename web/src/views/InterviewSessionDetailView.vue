<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { getInterviewSessionRecords } from '../api';

const route = useRoute();
const records = ref([]);
const loading = ref(false);
const errorText = ref('');

const sessionId = computed(() => String(route.params.sessionId || ''));
const sessionTitle = computed(() => (records.value[0]?.createdAt ? formatDateTime(records.value[0].createdAt) : sessionId.value));

function formatDateTime(value) {
  if (!value) return '-';
  return String(value).replace('T', ' ').slice(0, 16);
}

async function loadRecords() {
  loading.value = true;
  errorText.value = '';
  try {
    records.value = await getInterviewSessionRecords(sessionId.value);
  } catch (error) {
    errorText.value = error.message;
  } finally {
    loading.value = false;
  }
}

onMounted(loadRecords);
</script>

<template>
  <section class="page-section">
    <div class="page-heading">
      <div>
        <p class="eyebrow">Interview Detail</p>
        <h2>面试详情：{{ sessionTitle }}</h2>
        <p>这一场面试中保存的全部面试官问题和生成回答。</p>
      </div>
      <RouterLink class="secondary-btn link-btn" to="/interview-sessions">返回面试记录</RouterLink>
    </div>

    <p v-if="errorText" class="error-text">{{ errorText }}</p>

    <article class="card interview-history-card">
      <div v-if="loading" class="empty-state compact">详情加载中...</div>
      <div v-else-if="records.length" class="interview-record-list">
        <div v-for="(record, index) in records" :key="record.id" class="interview-record-item">
          <div class="record-time">第 {{ index + 1 }} 条 · {{ formatDateTime(record.createdAt) }}</div>
          <div class="record-block question-block">
            <strong>面试官问题</strong>
            <p>{{ record.question }}</p>
          </div>
          <div class="record-block answer-block">
            <strong>生成回答</strong>
            <p>{{ record.answer }}</p>
          </div>
        </div>
      </div>
      <div v-else class="empty-state compact">这一场面试暂无问答记录。</div>
    </article>
  </section>
</template>
