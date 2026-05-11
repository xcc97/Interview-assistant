<script setup>
import { ref } from 'vue';
import { RouterLink } from 'vue-router';

const isAnalyzing = ref(false);
const answer = ref('');
const form = ref({
  question: '请介绍一下你在项目里如何做性能优化？',
});

const examples = [
  '请介绍一个你主导过的项目，并说明你的贡献。',
  '你遇到过最复杂的线上问题是什么，最后怎么解决的？',
  '如果接口响应突然变慢，你会如何排查？',
];

const previewAnswers = {
  '请介绍一下你在项目里如何做性能优化？': `可以。我之前做性能优化时，一般不会一上来就直接改代码，而是先把问题量化清楚。

比如我在一个业务系统里遇到过接口响应变慢的问题，当时用户侧感知比较明显，核心接口高峰期会从几百毫秒涨到 2 秒以上。我的处理思路是先用日志、链路追踪和慢查询把耗时拆开，看时间到底花在数据库、外部接口，还是应用内部逻辑上。

定位后发现主要有三类问题：第一是部分查询没有命中合适索引；第二是列表页一次性加载了过多非必要字段；第三是有些结果其实短时间内变化不大，但每次请求都重新计算。

所以我做了几件事：先优化 SQL 和索引，把高频查询的扫描行数降下来；然后对接口返回做裁剪和分页，避免一次拉太多数据；最后把一些读多写少的数据放到缓存里，并且设置合理的过期时间和更新策略。对于峰值流量，我也补了限流和降级方案，避免个别慢接口拖垮整体服务。

最后效果是核心接口的平均响应时间从 1 秒多降到 300 毫秒以内，高峰期也比较稳定。我的复盘是，性能优化最重要的不是“用了什么技术”，而是先定位瓶颈，再针对性处理，并且用数据证明优化确实有效。`,
  '请介绍一个你主导过的项目，并说明你的贡献。': `可以。我之前主导过一个内部业务管理系统的重构项目，背景是老系统功能堆得比较多，页面响应慢，代码也比较难维护，新需求上线经常会影响到旧功能。

我在这个项目里的主要贡献有三块。第一是前期梳理，我和产品、运营一起把核心流程重新过了一遍，把真正高频的功能和历史遗留功能拆开，避免重构变成简单地“照搬一遍”。

第二是技术方案设计。我把原来比较耦合的模块拆成了更清晰的业务域，比如订单、用户、权限和数据看板，每个模块边界更明确。同时在前端也做了组件拆分，让表单、列表、筛选这些通用能力可以复用。

第三是落地推进。我没有一次性大爆炸上线，而是按模块灰度迁移，先迁移低风险功能，再逐步切核心流程。过程中我也补了关键接口监控和回滚方案，确保出现问题能快速定位和恢复。

最后这个项目上线后，新需求开发效率明显提升，之前一个中等需求可能要三四天，现在基本一两天就能完成；页面加载速度也有比较明显改善。对我来说，这个项目最大的价值不是单纯完成重构，而是让系统后续更容易迭代，团队维护成本也降了下来。`,
  '你遇到过最复杂的线上问题是什么，最后怎么解决的？': `我遇到过一个比较复杂的线上问题，是某次活动期间订单状态偶发不一致。麻烦的地方在于它不是必现问题，用户反馈是“有时支付成功了，但页面还显示未支付”，而且重试后又可能恢复正常。

我当时先做了两件事：第一是止血，先让客服和运营有一个人工核对方案，避免影响用户权益；第二是拉取问题订单，把支付回调、订单更新、消息队列消费和前端查询这几条链路的日志串起来看。

排查后发现，根因不是支付本身失败，而是高峰期消息消费有延迟，加上前端查询读到了短暂的旧状态，导致用户看到状态不一致。这个问题跨了支付回调、异步消息、缓存和前端展示，所以一开始看起来比较乱。

解决上，我先调整了订单状态查询逻辑：对于刚支付完成的订单，前端增加短时间轮询和更明确的处理中提示，避免直接展示误导状态。后端则优化了消息消费的并发和失败重试，同时对关键状态变更做了幂等保护，避免重复回调造成脏数据。

最终问题稳定解决后，我又补了订单状态延迟的监控，比如支付成功到订单完成的耗时分布，一旦超过阈值就告警。我的经验是，复杂线上问题不能只盯一个点，要先把完整链路串起来，再判断是数据问题、时序问题还是用户展示问题。`,
  '如果接口响应突然变慢，你会如何排查？': `如果接口突然变慢，我会先确认影响范围，而不是马上猜原因。

第一步，我会看监控指标，比如是所有接口都慢，还是某几个接口慢；是所有用户都慢，还是某个地区、某类请求慢。同时看 QPS、错误率、CPU、内存、数据库连接数这些基础指标有没有异常。

第二步，我会看链路耗时，把一次请求拆成几个阶段：网关、应用服务、数据库、缓存、第三方接口。这样能快速判断时间主要耗在哪一段。如果有链路追踪工具，就直接看 trace；没有的话，就临时加关键日志，把耗时打出来。

第三步，如果定位到数据库，我会看慢 SQL、执行计划、索引命中情况，以及是不是数据量突然变大。如果定位到应用层，我会看是否有锁竞争、线程池打满、GC 频繁，或者最近是否上线了新代码。如果是外部依赖慢，就要看是否需要超时控制、降级或缓存兜底。

处理上我会分两步：先止血，比如限流、降级、扩容、回滚或者临时缓存；再做根因修复，比如优化 SQL、拆分逻辑、调整线程池或完善异步处理。

我觉得排查慢接口最关键的是有顺序：先看范围，再看链路，再看具体瓶颈。这样不会陷入凭感觉排查，也能更快恢复线上稳定性。`,
};

const steps = [
  { title: '导入简历', desc: '让回答贴合你的项目经历和岗位方向。' },
  { title: '开始面试', desc: '客户端实时识别面试官问题，自动生成答题思路。' },
  { title: '照着结构表达', desc: '按背景、行动、结果、复盘组织语言，不再临场卡壳。' },
];

function useExample(question) {
  form.value.question = question;
  answer.value = '';
}

function handleAnalyze() {
  isAnalyzing.value = true;
  window.setTimeout(() => {
    answer.value = previewAnswers[form.value.question] || previewAnswers['请介绍一下你在项目里如何做性能优化？'];
    isAnalyzing.value = false;
  }, 300);
}
</script>

<template>
  <section id="try" class="page-section try-section">
    <div class="page-heading center-heading">
      <p class="eyebrow">Try nod</p>
      <h2>先用一个真实问题体验效果</h2>
      <p>客户端会实时监听并识别面试官问题，这里先用几个典型问题展示 nod 的回答质量和表达风格。</p>
    </div>

    <div class="content-grid two-columns demo-grid embedded-demo-grid">
      <article class="card analyzer-card">
        <div class="card-title-row">
          <h3>面试官问题</h3>
          <span>客户端实时识别</span>
        </div>
        <label>
          客户端会在面试中自动听取并识别面试官所有问题
          <textarea v-model="form.question" rows="5" readonly></textarea>
        </label>
        <button class="primary-btn full" :disabled="isAnalyzing" @click="handleAnalyze">
          {{ isAnalyzing ? '生成中...' : '查看回答示例' }}
        </button>
      </article>

      <article class="card answer-card">
        <div class="card-title-row">
          <h3>回答示例</h3>
          <span>口语化表达</span>
        </div>
        <div v-if="answer" class="answer-content">{{ answer }}</div>
        <div v-else class="empty-state demo-empty-state">
          <strong>点击左侧按钮查看示例答案</strong>
          <p>你会看到一段更接近真实面试表达的口语化回答。</p>
        </div>
      </article>
    </div>

    <article class="card demo-examples-card compact-example-card">
      <h3>不知道问什么？试试这些高频问题</h3>
      <div class="example-chip-list">
        <button v-for="question in examples" :key="question" class="secondary-btn small-btn" @click="useExample(question)">
          {{ question }}
        </button>
      </div>
    </article>
  </section>

  <section class="home-hero-section balanced-hero">
    <div class="hero-card intro-card">
      <p class="eyebrow">nod 点头 · 面试表达助手</p>
      <h1>让面试回答更有结构</h1>
      <p class="hero-desc">
        客户端实时识别面试官问题，结合你的简历经历，整理成可直接表达的回答框架。
      </p>
      <div class="hero-actions compact-hero-actions">
        <RouterLink class="primary-btn link-btn" to="/download">下载客户端</RouterLink>
        <a class="secondary-btn link-btn" href="#try">立即体验</a>
        <RouterLink class="secondary-btn link-btn" to="/billing">查看套餐</RouterLink>
      </div>
    </div>

    <div class="hero-card preview-card">
      <div class="card-title-row">
        <h3>面试中，nod 这样帮你</h3>
        <span>实时提示</span>
      </div>
      <div class="interview-flow-card question-card">
        <small>面试官问题</small>
        <p>请介绍一下你在项目里如何处理高并发和接口性能问题？</p>
      </div>
      <div class="interview-flow-arrow">↓</div>
      <div class="interview-flow-card answer-tip-card">
        <small>回答结构</small>
        <ul>
          <li>先说业务场景和性能目标</li>
          <li>再说瓶颈定位、缓存、异步、限流</li>
          <li>最后补充数据指标和复盘</li>
        </ul>
      </div>
    </div>

    <div class="hero-card steps-card">
      <div class="card-title-row">
        <h3>三步开始</h3>
        <span>简单上手</span>
      </div>
      <div class="quick-steps balanced-steps">
        <article v-for="(step, index) in steps" :key="step.title" class="quick-step-card">
          <span>{{ index + 1 }}</span>
          <strong>{{ step.title }}</strong>
          <p>{{ step.desc }}</p>
        </article>
      </div>
    </div>

    <div class="hero-card metrics-card">
      <div class="card-title-row">
        <h3>适合这些场景</h3>
        <span>高频面试</span>
      </div>
      <div class="metric-grid compact-metrics scenario-grid">
        <div><strong>技术面</strong><span>项目与架构</span></div>
        <div><strong>产品面</strong><span>需求与规划</span></div>
        <div><strong>运营面</strong><span>增长与复盘</span></div>
        <div><strong>销售面</strong><span>客户与转化</span></div>
        <div><strong>管理面</strong><span>协作与带队</span></div>
        <div><strong>通用面</strong><span>经历与动机</span></div>
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
