# 站外存活监测

external-monitor 是独立 Python 探针，不依赖 Halo 或 Insight 进程。
请部署到另一台服务器；同一主机上的第二个容器无法覆盖主机宕机。
监测公开页面即可，不需要 Halo 管理员账号或密码。

## Docker 部署

将 external-monitor 目录复制到独立服务器，在该目录执行：

```sh
cp .env.example .env
# 编辑 .env，填写 MONITOR_URL 和 WEBHOOK_URL，或完整 SMTP 配置
docker compose up -d --build
docker compose logs -f --tail=50
```

MONITOR_URL 使用最终 HTTPS 地址（不跟随重定向）。默认检查 2xx；
可以填写 EXPECTED_TEXT，在前 64 KiB 响应中检查特征文字，避免代理错误页返回 200。
站点首页可能受 CDN 缓存影响，建议使用能反映源站状态的专用地址。
ALLOW_HTTP=true 仅用于明确需要 HTTP 的环境；HTTPS 始终验证证书。
SMTP 使用 STARTTLS，默认端口 587；探针的通知渠道必须独立于 Halo。
.env 包含渠道凭据，请不要提交或分享，限制文件读取权限。

默认每 30 秒检查一次，超时 5 秒，连续失败 3 次通知宕机，连续成功 2 次通知恢复。
相同宕机期间不重复生成告警。通知写入独立 SQLite，失败后指数退避，最多 5 次；
探针重启后继续重试。Webhook 带 X-Insight-Event-Id，可用于接收端去重；
设置 WEBHOOK_SECRET 后带 X-Insight-Signature（sha256 HMAC）。
网络中断时可能重复投递，采用至少一次语义，不能保证收件人已经阅读。
发送通知使用独立工作线程，慢渠道不会推迟存活检查。新的宕机/恢复状态会替代旧的待重试消息，
已经发送中的消息无法撤回。投递工作线程异常退出时主进程退出，由容器重启策略恢复。

## 查看状态与停止

```sh
docker compose logs --tail=50
docker compose stop
```

每 5 分钟输出一次状态，状态变化和发送失败立即记录。
最近 2000 个状态事件、4000 条已结束投递记录保存在 probe-data 卷。
停止或更新容器保留数据卷；不要删除数据卷，除非希望清空探针历史。
建议由服务器自身的容器监控检查探针是否仍运行。
本站插件页面只提供部署说明，不会把“代码已准备好”显示成“探针已在线”。

## 无 Docker

Python 3.10+，仅使用标准库。设置上述环境变量和可写 DATABASE_PATH 后执行：

```sh
python3 main.py
```

生产环境请由 systemd 等进程管理器运行。测试无需访问网络：

```sh
python3 -m unittest -v
```
