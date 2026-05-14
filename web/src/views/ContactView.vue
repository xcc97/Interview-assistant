<script setup>
const contactItems = [
  { title: 'QQ 联系', value: '1141261080', tip: '使用问题、订单售后、产品反馈都可以通过 QQ 联系。' },
  { title: 'QQ 邮箱', value: '1141261080@qq.com', tip: '如需发送截图、订单信息或详细问题说明，请通过邮箱联系。' },
];
</script>

<template>
  <section class="page-section contact-page">
    <div class="page-heading center-heading">
      <p class="eyebrow">Contact</p>
      <h2>联系我们</h2>
      <p>如果你在使用 nod 点头时遇到问题，可以通过以下方式联系。</p>
    </div>

    <div class="contact-method-grid">
      <article v-for="item in contactItems" :key="item.title" class="card contact-method-card">
        <span>{{ item.title }}</span>
        <strong>{{ item.value }}</strong>
        <p>{{ item.tip }}</p>
      </article>
    </div>

    <article class="card legal-card contact-note">
      <h3>联系客服时建议提供</h3>
      <ol class="ordered-list">
        <li>注册手机号或账号昵称。</li>
        <li>订单号、支付时间和支付截图。</li>
        <li>遇到问题的页面、操作步骤和错误提示。</li>
        <li>你希望协助处理的具体事项。</li>
      </ol>
    </article>
  </section>
</template>
