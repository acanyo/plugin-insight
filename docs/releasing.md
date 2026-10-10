# 发布到 GitHub 和 Halo 应用市场

## 发布凭据

在 Halo 官网创建具有**版本管理**权限的个人令牌，并将其保存为 GitHub 仓库的
Actions Secret：`HALO_PAT`。不要将令牌写入代码、工作流或发布说明。

配置入口：<https://github.com/acanyo/plugin-insight/settings/secrets/actions>。
应用市场的应用 ID 为 `app-bm4ppebf`。

## 新版本

发布 GitHub Release 后，`CD` 工作流构建安装包、上传到 GitHub，并发布到 Halo 应用市场。
该流程通过 `halo-pat` 参数传递仓库的 `HALO_PAT`。

## 补发已有版本

如果 GitHub 已有安装包，但市场发布步骤被跳过，可以运行
[App Store Republish](https://github.com/acanyo/plugin-insight/actions/workflows/appstore-release.yaml)，
选择 `main` 分支并填写现有 Release 标签，例如 `v1.2.0`。

补发流程下载 GitHub 已发布的安装包、校验 SHA-256，然后使用官方发布 Action 同步版本说明和安装包。
GitHub Release 和标签保持原样，不需要重新构建。

发布后核对[应用市场页面](https://www.halo.run/store/apps/app-bm4ppebf)的版本与下载附件。
如果流程已创建市场版本后失败，先在 Halo 后台确认该版本状态，避免重复创建同一版本。
