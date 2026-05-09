<script setup>
const contactItems = [
  { title: '商务与合作', value: 'business@nod.ai', tip: '品牌合作、企业试用、渠道合作' },
  { title: '订单与售后', value: 'support@nod.ai', tip: '支付到账、退款、套餐异常' },
  { title: '产品反馈', value: 'feedback@nod.ai', tip: '功能建议、体验问题、内容反馈' },
];
</script>

<template>
  <section class="page-section">
    <div class="page-heading center-heading">
      <p class="eyebrow">Contact</p>
      <h2>联系我们</h2>
      <p>如果你在使用 nod 点头时遇到问题，或希望了解合作与企业方案，可以通过以下方式联系。</p>
    </div>

    <div class="stats-grid contact-grid">
      <article v-for="item in contactItems" :key="item.title" class="card stat-card">
        <span>{{ item.title }}</span>
        <strong>{{ item.value }}</strong>
        <p>{{ item.tip }}</p>
      </article>
      <article class="card stat-card">
        <span>服务时间</span>
        <strong>工作日 10:00 - 19:00</strong>
        <p>紧急订单问题请在邮件标题中注明订单号。</p>
      </article>
    </div>

    <article class="card legal-card contact-note">
      <h3>联系客服时建议提供</h3>
      <ol class="ordered-list">
        <li>注册手机号或账号昵称。</li>
        <li>订单号、支付时间和支付截图。</li>
        <li>遇到问题的页面、操作步骤和错误提示。</li>
        <li>你希望我们协助处理的具体事项。</li>
      </ol>
    </article>
  </section>
</template>
