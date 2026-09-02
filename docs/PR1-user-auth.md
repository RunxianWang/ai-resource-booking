# PR1：用户认证与权限基础

## 目标

将 Demo 用户替换为真实用户身份，提供最小可用的 JWT 登录和基于角色的接口保护。

## 最小方案

- 使用 JWT，存放在 HttpOnly Cookie 中。
- JWT 有效期为 2 小时；退出时清除 Cookie，不实现 Token 黑名单。
- 使用 JDBC 管理 `users`、`roles`、`user_roles` 三张表。
- 角色只有 `USER` 和 `ADMIN`。
- 本地启动时根据 `APP_BOOTSTRAP_ADMIN_USERNAME` 和 `APP_BOOTSTRAP_ADMIN_PASSWORD` 幂等初始化管理员。
- 密码使用 BCrypt 哈希后保存。
- 暂不实现注册、改密、找回密码、refresh token 和用户管理页面。

## 接口

- `POST /api/auth/login`
- `POST /api/auth/logout`
- `GET /api/auth/me`
- 保留 `GET /api/users/me`，但身份来自 JWT。

## 保护规则

- 未登录访问受保护接口返回 `401`。
- 已登录但没有权限返回 `403`。
- `/api/dev/**`、`/api/dead-letters/**`、`/api/admin/**` 仅管理员可访问。
- 预约和用户当前信息接口需要登录。

## 验收标准

1. 本地启动后自动存在 `admin` 管理员。
2. 使用环境变量配置的管理员账号可以登录并获得 JWT Cookie。
3. 当前用户接口返回用户 ID、用户名和角色。
4. 未登录访问受保护接口返回 `401`。
5. 普通用户访问管理员接口返回 `403`。
6. 管理员可以访问开发、死信和管理员接口。
7. 退出后浏览器 Cookie 被清除。
8. 密码不以明文保存，生产代码不再使用 `DemoUserContext`。
