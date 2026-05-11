<script setup>
import { onMounted, ref } from 'vue';
import { getInterviewSessions } from '../api';

const sessions = ref([]);
const loading = ref(false);
const errorText = ref('');

function formatDateTime(value) {
  if (!value) return '-';
  return String(value).replace('T', ' ').slice(0, 16);
}

async function loadSessions() {
  loading.value = true;
  errorText.value = '';
  try {
    sessions.value = await getInterviewSessions();
  } catch (error) {
    errorText.value = error.message;
  } finally {
    loading.value = false;
  }
}

onMounted(loadSessions);
</script>

<template>
  <section class="page-section">
    <div class="page-heading">
      <div>
        <p class="eyebrow">Interview Review</p>
        <h2>面试复习记录</h2>
        <p>按每一场面试整理问答记录，点进场次后可以查看完整问题和生成回答。</p>
      </div>
    </div>

    <p v-if="errorText" class="error-text">{{ errorText }}</p>

    <article class="card table-card">
      <div v-if="loading" class="empty-state compact">面试记录加载中...</div>
      <div v-else-if="sessions.length" class="table-wrapper">
        <table class="data-table">
          <thead>
            <tr>
              <th>面试时间</th>
              <th>问答数量</th>
              <th>最近问题</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="session in sessions" :key="session.sessionId">
              <td>{{ formatDateTime(session.startedAt) }}</td>
              <td>{{ session.recordCount }} 条</td>
              <td>{{ session.previewQuestion || '-' }}</td>
              <td>
                <RouterLink class="secondary-btn small-btn link-btn" :to="{ name: 'interview-session-detail', params: { sessionId: session.sessionId } }">
                  查看详情
                </RouterLink>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="empty-state compact">暂无面试记录。使用桌面客户端生成回答后，会按面试场次自动归档。</div>
    </article>
  </section>
</template>
