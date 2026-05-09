<script setup>
import { ref } from 'vue';
import { analyzeInterview } from '../api';

const isAnalyzing = ref(false);
const analyzeError = ref('');
const answer = ref('');
const form = ref({
  question: '请介绍一下你在项目里如何做性能优化？',
  resumeText: '',
});

const examples = [
  '请介绍一个你主导过的项目，并说明你的贡献。',
  '你遇到过最复杂的线上问题是什么，最后怎么解决的？',
  '如果接口响应突然变慢，你会如何排查？',
];

function useExample(question) {
  form.value.question = question;
  answer.value = '';
  analyzeError.value = '';
}

async function handleAnalyze() {
  if (!form.value.question.trim()) {
    analyzeError.value = '请先输入面试问题';
    return;
  }

  isAnalyzing.value = true;
  analyzeError.value = '';
  answer.value = '';
  try {
    const result = await analyzeInterview({
      question: form.value.question.trim(),
      resumeText: form.value.resumeText.trim(),
    });
    answer.value = result.answer || 'nod 已收到问题，但暂时没有生成有效建议，请稍后重试。';
  } catch (error) {
    analyzeError.value = error.message;
  } finally {
    isAnalyzing.value = false;
  }
}
</script>

<template>
  <section class="page-section">
    <div class="page-heading center-heading">
      <p class="eyebrow">Try nod</p>
      <h2>先体验一次 AI 回答建议</h2>
      <p>输入真实面试问题，nod 会帮你拆解考察点、组织回答结构，并给出更自然的表达参考。</p>
    </div>

    <div class="content-grid two-columns demo-grid">
      <article class="card analyzer-card">
        <h3>输入面试问题</h3>
        <label>
          面试官的问题
          <textarea v-model="form.question" rows="4" placeholder="例如：请介绍一下你在项目里如何做性能优化？"></textarea>
        </label>
        <label>
          你的经历背景（可选）
          <textarea v-model="form.resumeText" rows="5" placeholder="可以补充岗位方向、项目经历、技术栈或你希望突出的优势"></textarea>
        </label>
        <button class="primary-btn full" :disabled="isAnalyzing" @click="handleAnalyze">
          {{ isAnalyzing ? '生成中...' : '生成回答建议' }}
        </button>
        <p v-if="analyzeError" class="error-text">{{ analyzeError }}</p>
      </article>

      <article class="card answer-card">
        <h3>nod 建议</h3>
        <div v-if="answer" class="answer-content">{{ answer }}</div>
        <div v-else class="empty-state">生成后，这里会展示回答框架、关键要点和表达建议。</div>
      </article>
    </div>

    <article class="card demo-examples-card">
      <h3>不知道问什么？试试这些高频问题</h3>
      <div class="example-chip-list">
        <button v-for="question in examples" :key="question" class="secondary-btn small-btn" @click="useExample(question)">
          {{ question }}
        </button>
      </div>
    </article>
  </section>
</template>
