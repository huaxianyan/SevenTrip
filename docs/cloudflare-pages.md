# Cloudflare Pages 部署

「出行」公开站点位于仓库的 `site/`，目标域名为：

```text
https://chuxing.neko7ina.com
```

站点是纯 HTML/CSS/SVG，不需要 Node.js、构建脚本、环境变量、Cloudflare Worker 或服务端函数。

## 创建 Pages 项目

1. 登录 Cloudflare Dashboard
2. 进入 `Workers & Pages`，选择 `Create application → Pages → Connect to Git`
3. 授权并选择 GitHub 仓库 `huaxianyan/SevenTrip`
4. 使用以下构建设置：

   ```text
   Production branch: main
   Framework preset: None
   Build command: 留空
   Build output directory: site
   Root directory: 留空（仓库根目录）
   ```

5. 部署后先通过 Cloudflare 提供的 `*.pages.dev` 地址检查主页、隐私政策、支持页面和使用条款

## 绑定域名

在 Pages 项目的 `Custom domains` 中添加：

```text
chuxing.neko7ina.com
```

如果 `neko7ina.com` 已由同一 Cloudflare 账号管理，Cloudflare 会自动创建所需 DNS 记录和 TLS 证书。等待状态变成 `Active` 后检查：

```text
https://chuxing.neko7ina.com/
https://chuxing.neko7ina.com/privacy/
https://chuxing.neko7ina.com/support/
https://chuxing.neko7ina.com/terms/
https://chuxing.neko7ina.com/sitemap.xml
```

不要把 `*.pages.dev` 临时地址提交给 Google Wallet，审核材料统一使用自定义域名。

## Search Console 域名验证

1. 使用 Google Cloud 项目 `walletassistantqlbb10jcvuvmbat` 的 Owner 或 Editor 账号登录 Google Search Console
2. 添加 Domain property：

   ```text
   neko7ina.com
   ```

3. 将 Google 提供的 TXT 记录添加到 Cloudflare DNS

## 发布检查

- 页面不包含统计脚本、广告、表单、Cookie 或远程字体
- `_headers` 配置了 CSP、禁止嵌入及其他安全响应头
- 所有正式 URL 都使用 HTTPS 和自定义域名
- 页面中的开发者名称为 `NeKo7inA`，联系邮箱为 `7@neko7ina.com`
- 功能、权限或数据处理方式变化时，同步更新主页和隐私政策
