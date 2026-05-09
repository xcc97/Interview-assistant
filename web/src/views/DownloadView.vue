<template>
  <section class="page-section">
    <div class="page-heading download-heading">
      <div>
        <p class="eyebrow">Download Client</p>
        <h2>下载 nod 点头客户端</h2>
        <p>Web 负责注册、购买和查看用量；真正远程面试时，请打开桌面客户端获得实时辅助。</p>
      </div>
      <RouterLink class="primary-btn link-btn" to="/billing">先购买套餐</RouterLink>
    </div>

    <div class="content-grid two-columns download-grid">
      <article class="card download-card featured">
        <span class="status-badge success">推荐</span>
        <h3>macOS 客户端</h3>
        <p>适合 MacBook 用户，在远程面试前启动客户端，登录账号后即可检查余额并开始辅助会话。</p>
        <button class="primary-btn full" @click="openClient">打开 nod 客户端</button>
        <a class="secondary-btn link-btn full" href="/downloads/nod.dmg" download>下载 macOS 安装包</a>
        <p class="compact-note">已安装时会尝试通过 nod://open 打开客户端；未安装时请下载 nod.dmg。</p>
      </article>

      <article class="card download-card">
        <span class="status-badge warning">即将上线</span>
        <h3>Windows 客户端</h3>
        <p>Windows 10/11 版本正在打包适配中，暂未开放下载。需要测试包的用户可以先联系我们获取内测版本。</p>
        <button class="secondary-btn full" disabled>Windows 版本即将上线</button>
        <p class="compact-note">Windows 安装包准备完成后，会在这里开放 .msi 下载。</p>
      </article>
    </div>

    <article class="card usage-guide-card">
      <h3>客户使用流程</h3>
      <ol class="ordered-list">
        <li>注册或登录 nod 点头账号。</li>
        <li>进入价格页购买套餐，本地测试可使用模拟支付。</li>
        <li>回到我的点头确认剩余可用时长。</li>
        <li>下载并打开桌面客户端，使用同一账号登录。</li>
        <li>远程面试开始前点击开始辅助，面试结束后停止会话，系统按分钟扣费。</li>
      </ol>
    </article>

    <article class="card usage-guide-card">
      <h3>安装包生成与发布</h3>
      <p>使用 JDK 21 自带的 jpackage 生成可安装包。macOS 需要在 Mac 上构建 .dmg，Windows 需要在 Windows 上构建 .msi。</p>
      <div class="config-list">
        <div><strong>macOS 构建脚本</strong><span>scripts/package-desktop.sh</span></div>
        <div><strong>Windows 构建脚本</strong><span>scripts/package-desktop.ps1</span></div>
        <div><strong>下载目录</strong><span>web/public/downloads/</span></div>
      </div>
    </article>
  </section>
</template>

<script setup>
import { RouterLink } from 'vue-router';

function openClient() {
  window.location.href = 'nod://open';
}
</script>
