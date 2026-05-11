<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { RouterLink } from 'vue-router';

const demoMoments = [
  {
    time: '00:03',
    speaker: '面试官 01',
    question: '请介绍一下你在项目里如何做性能优化？',
    answer: `可以。我之前做性能优化时，一般不会一上来就直接改代码，而是先把问题量化清楚。

比如我在一个业务系统里遇到过接口响应变慢的问题，当时用户侧感知比较明显，核心接口高峰期会从几百毫秒涨到 2 秒以上。我的处理思路是先用日志、链路追踪和慢查询把耗时拆开，看时间到底花在数据库、外部接口，还是应用内部逻辑上。

定位后我主要做了三件事：第一，优化 SQL 和索引，把高频查询的扫描行数降下来；第二，对接口返回做裁剪和分页，避免一次拉太多非必要字段；第三，把读多写少的数据放到缓存里，并设置合理的过期和更新策略。

最后核心接口平均响应时间降到 300 毫秒以内。我的复盘是，性能优化最重要的不是堆技术，而是先定位瓶颈，再用数据证明优化有效。`
  },
  {
    time: '00:08',
    speaker: '面试官 02',
    question: '你遇到过最复杂的线上问题是什么？',
    answer: `我遇到过一个比较复杂的线上问题，是活动期间订单状态偶发不一致。用户反馈是支付成功了，但页面还显示未支付，而且不是每次都复现。

我当时先做了两件事：第一是止血，先让客服和运营有人工核对方案，避免影响用户权益；第二是把问题订单拉出来，把支付回调、订单更新、消息队列消费和前端查询这几条链路的日志串起来看。

最后发现根因不是支付失败，而是高峰期消息消费有延迟，加上前端短时间内读到了旧状态。解决时，我先给前端加了处理中提示和短轮询，后端优化了消费并发和失败重试，同时对订单状态变更做了幂等保护。

这个问题让我印象比较深，因为它不是单点 bug，而是链路时序问题。我的经验是，复杂线上问题要先串完整链路，再判断是数据问题、时序问题还是展示问题。`
  },
  {
    time: '00:14',
    speaker: '面试官 03',
    question: '如果接口突然变慢，你会怎么排查？',
    answer: `如果接口突然变慢，我不会马上猜原因，而是先确认影响范围。

第一，我会看监控指标，确认是所有接口都慢，还是某几个接口慢；是所有用户都慢，还是某个地区、某类请求慢。同时看 QPS、错误率、CPU、内存、数据库连接数这些基础指标有没有异常。

第二，我会拆链路耗时，把一次请求分成网关、应用服务、数据库、缓存、第三方接口几段。如果有链路追踪工具，就直接看 trace；没有的话，我会临时加关键日志，把每一段耗时打出来。

第三，再根据瓶颈处理。如果是数据库，就看慢 SQL、执行计划和索引；如果是应用层，就看线程池、锁竞争和 GC；如果是外部依赖，就考虑超时、降级和缓存兜底。处理上我会先止血，再做根因修复。`
  },
  {
    time: '00:20',
    speaker: '面试官 04',
    question: '你在团队协作里通常承担什么角色？',
    answer: `我在团队里通常更偏推进和兜底型角色，不只是完成自己手上的任务，也会关注上下游协作有没有卡点。

比如接到一个需求后，我会先和产品确认目标、边界和优先级，避免大家理解不一致。然后我会把技术方案拆成几个阶段，提前和前端、测试、后端同学对齐接口、数据结构和风险点。

落地过程中，我会比较关注两个事情。第一是节奏，如果某个依赖有延期风险，我会尽早同步出来，而不是等到最后一天才暴露。第二是质量，核心链路我会补好日志、异常处理和回滚方案，确保上线后可观测、可恢复。

所以我的定位不是单纯写代码，而是把事情稳定推进到上线，并且让团队协作成本尽量低。`
  }
];

const activeDemoIndex = ref(0);
const demoSlideMs = 4800;
let demoTimer;

const activeDemo = computed(() => demoMoments[activeDemoIndex.value]);

onMounted(() => {
  demoTimer = window.setInterval(() => {
    activeDemoIndex.value = (activeDemoIndex.value + 1) % demoMoments.length;
  }, demoSlideMs);
});

onUnmounted(() => {
  if (demoTimer) window.clearInterval(demoTimer);
});

const steps = [
  { title: '导入简历', desc: '让回答贴合你的项目经历和岗位方向。' },
  { title: '开始面试', desc: '客户端实时识别面试官问题，自动生成答题思路。' },
  { title: '照着结构表达', desc: '按背景、行动、结果、复盘组织语言，不再临场卡壳。' },
];

const faqItems = [
  {
    q: 'nod 是做什么的？',
    a: 'nod 是面试时的表达助手：在你参加远程面试时，听清面试官的问题，结合你导入的简历与岗位方向，整理成有结构的回答要点，方便你用自己的话顺畅说出来。',
  },
  {
    q: '和「替考」或自动答题有什么区别？',
    a: 'nod 不会代替你作答，也不会替你出声。它提供的是听题理解、回答结构和关键信息提示，最终怎么说、说到什么深度，仍由你本人完成。',
  },
  {
    q: '怎么计费？余额怎么查？',
    a: '按使用时长（分钟）计费。购买套餐后额度会到账，可在个人中心查看剩余分钟数与使用记录，具体价格以套餐页说明为准。',
  },
  {
    q: '我的简历和面试内容安全吗？',
    a: '我们按隐私政策处理你上传的简历与使用数据。建议你阅读站内《隐私政策》了解收集范围、用途与保存方式；如有疑问可通过「联系我们」沟通。',
  },
  {
    q: '支持哪些设备或会议软件？',
    a: '请从下载页安装 nod 客户端，并在本机完成麦克风和系统权限配置。常见远程会议软件一般可与本机音频配合使用，具体以客户端内说明与实测为准。',
  },
  {
    q: '遇到问题怎么反馈？',
    a: '可通过页脚「联系我们」提交问题或建议。若涉及订单与退款，也可查阅《退款说明》或用户在个人中心的相关入口。',
  },
];

const openFaqIndex = ref(null);

function toggleFaq(index) {
  openFaqIndex.value = openFaqIndex.value === index ? null : index;
}

</script>

<template>
  <section id="try" class="page-section cinematic-demo-section">
    <div class="cinematic-demo-shell">
      <div class="cinematic-copy">
        <p class="eyebrow">效果演示</p>
        <h2>几秒钟看懂 nod 如何工作</h2>
        <p>在真实远程面试里，面试官连续提问时，nod 会实时听清问题，并在右侧整理成可直接照着说的口语化回答。</p>
        <div class="cinematic-actions">
          <RouterLink class="primary-btn link-btn" to="/download">下载客户端</RouterLink>
          <RouterLink class="secondary-btn link-btn" to="/billing">查看套餐</RouterLink>
        </div>
      </div>

      <div class="demo-video-frame" aria-label="nod 自动识别面试问题并生成回答的演示动画">
        <div class="video-topbar">
          <div class="video-dots"><span></span><span></span><span></span></div>
          <strong>nod 实时面试辅助</strong>
          <em>录制 00:24</em>
        </div>

        <div class="demo-video-stage">
          <div class="interviewer-panel">
            <div class="panel-label">面试官声音识别</div>
            <div class="waveform" aria-hidden="true">
              <span v-for="index in 20" :key="index"></span>
            </div>
            <div class="question-feed">
              <Transition name="demo-slide" mode="out-in">
                <div :key="activeDemo.time" class="question-cue">
                  <small>{{ activeDemo.time }} · {{ activeDemo.speaker }}</small>
                  <p>{{ activeDemo.question }}</p>
                </div>
              </Transition>
            </div>
          </div>

          <div class="answer-panel">
            <div class="panel-label">生成的口语化回答</div>
            <div class="answer-feed">
              <Transition name="demo-slide" mode="out-in">
                <div :key="activeDemo.question" class="answer-cue">
                  <small>已生成回答</small>
                  <p>{{ activeDemo.answer }}</p>
                </div>
              </Transition>
            </div>
          </div>
        </div>

        <div class="demo-slide-dots" aria-hidden="true">
          <span
            v-for="(_, i) in demoMoments"
            :key="i"
            :class="{ active: i === activeDemoIndex }"
          ></span>
        </div>

        <div class="video-progress" :key="activeDemoIndex">
          <span></span>
        </div>
      </div>
    </div>
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

  <section id="faq" class="page-section faq-section" aria-labelledby="faq-heading">
    <div class="faq-slab">
      <div class="faq-heading-block">
        <p class="eyebrow">常见问题</p>
        <h2 id="faq-heading" class="faq-section-title">FAQs</h2>
        <p class="faq-section-lead">计费、隐私、客户端与使用前你可能想先确认的几件事。</p>
      </div>
      <div class="faq-accordion">
        <div
          v-for="(item, index) in faqItems"
          :key="item.q"
          class="faq-item"
          :class="{ open: openFaqIndex === index }"
        >
          <button
            type="button"
            class="faq-trigger"
            :aria-expanded="openFaqIndex === index"
            :aria-controls="`faq-panel-${index}`"
            :id="`faq-trigger-${index}`"
            @click="toggleFaq(index)"
          >
            <span class="faq-question-text">{{ item.q }}</span>
            <span class="faq-chevron" aria-hidden="true"></span>
          </button>
          <div
            v-show="openFaqIndex === index"
            :id="`faq-panel-${index}`"
            class="faq-panel"
            role="region"
            :aria-labelledby="`faq-trigger-${index}`"
          >
            <p>{{ item.a }}</p>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>
