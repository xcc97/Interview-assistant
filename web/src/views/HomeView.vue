<template>
  <section class="hero-section">
    <div class="hero-content">
      <p class="eyebrow">nod 点头 · 面试时的 AI 应答搭子</p>
      <h1>听懂问题，点出思路，让你更从容地开口。</h1>
      <p class="hero-desc">
        nod 点头为求职者提供实时面试辅助、模拟问答和表达优化。把面试官的问题转化为清晰答题框架，让你在关键时刻快速组织语言，稳定发挥。
      </p>
      <div class="hero-actions">
        <RouterLink class="primary-btn link-btn" to="/download">下载客户端</RouterLink>
        <RouterLink class="secondary-btn link-btn" to="/billing">查看套餐</RouterLink>
        <RouterLink class="secondary-btn link-btn" to="/login">登录/注册</RouterLink>
      </div>
      <div class="status-card inline-card">
        <div>
          <strong>为真实面试场景设计</strong>
          <p>从问题理解、要点拆解到回答润色，nod 帮你把“会做”说成“说得清”。</p>
        </div>
      </div>
    </div>

    <div class="hero-panel product-preview">
      <div class="panel-header">
        <span></span><span></span><span></span>
      </div>
      <div class="mock-card active">
        <small>面试官问题</small>
        <p>请介绍一下你在项目里如何处理高并发和接口性能问题？</p>
      </div>
      <div class="mock-card">
        <small>nod 答题提示</small>
        <p>先说明业务场景，再按“瓶颈定位 → 缓存/异步/限流 → 结果指标”组织回答。</p>
      </div>
      <div class="metric-grid">
        <div><strong>实时提示</strong><span>快速成型答案</span></div>
        <div><strong>模拟练习</strong><span>提前熟悉追问</span></div>
        <div><strong>按量使用</strong><span>分钟套餐灵活购买</span></div>
      </div>
    </div>
  </section>

  <section class="page-section landing-section">
    <div class="page-heading center-heading">
      <p class="eyebrow">Why nod</p>
      <h2>不是替你面试，而是帮你把能力表达出来</h2>
      <p>nod 更关注回答结构、表达顺序和关键信息提炼，适合技术面、业务面、项目复盘和行为面试。</p>
    </div>

    <div class="stats-grid feature-grid">
      <article class="card stat-card">
        <span>听题理解</span>
        <strong>抓住问题重点</strong>
        <p>识别面试官真正想考察的能力点，减少答非所问。</p>
      </article>
      <article class="card stat-card">
        <span>结构提示</span>
        <strong>给出回答骨架</strong>
        <p>按背景、行动、结果、复盘等结构提示你如何展开。</p>
      </article>
      <article class="card stat-card">
        <span>表达优化</span>
        <strong>让答案更专业</strong>
        <p>把零散经验整理成更有逻辑、更像候选人真实经历的表达。</p>
      </article>
      <article class="card stat-card">
        <span>用量透明</span>
        <strong>按分钟计费</strong>
        <p>套餐购买后自动到账，使用记录和余额变化都可查看。</p>
      </article>
    </div>
  </section>
</template>

<script setup>
import { RouterLink } from 'vue-router';
</script>
