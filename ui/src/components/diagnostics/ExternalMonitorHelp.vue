<template>
  <section class="insight-section diagnostic-section">
    <header>
      <div>
        <h2>站外存活监测</h2>
        <p>独立探针在 Halo 宕机时仍能通知</p>
      </div>
    </header>
    <p>
      项目随附 external-monitor Docker 部署包。将它部署到另一台服务器，配置站点地址和独立 Webhook 或
      SMTP 渠道即可运行。
    </p>
    <div class="diagnostic-metrics">
      <article>
        <span>检查间隔</span><strong>30 秒</strong><small>默认连续失败 3 次告警</small>
      </article>
      <article>
        <span>恢复判定</span><strong>连续成功 2 次</strong><small>记录宕机时长并发送恢复通知</small>
      </article>
      <article>
        <span>通知存储</span><strong>独立 SQLite</strong><small>重启后继续重试待发通知</small>
      </article>
    </div>
    <p>
      部署步骤见项目 docs/external-monitor.md。探针独立于插件工作，无需 Halo 管理员密码；支持 HTTPS
      验证、可选页面内容匹配、通知失败重试和本地事件记录。
    </p>
    <p>
      此页不代表探针已经部署。不要将唯一探针放在 Halo 同一台主机上，否则主机故障时仍会失去告警能力。
    </p>
  </section>
</template>
