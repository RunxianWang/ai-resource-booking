# 前端测试台

这个 Vite 前端用于直观测试后端预约接口。开发环境中，页面请求 `/api`，由 Vite 代理到 Spring Boot。

## 启动方式

```powershell
npm install
npm run dev
```

默认访问地址：

```text
http://localhost:5173
```

默认后端代理目标：

```text
http://localhost:8080
```

如果后端端口不同，可以复制 `.env.example` 为 `.env.local` 并修改：

```env
VITE_API_PROXY_TARGET=http://localhost:8081
```
