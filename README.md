# AI 算力资源预约平台

这是一个基于 Spring Boot 的高并发资源预约项目，主要模拟实验室 GPU / AI 训练资源的预约场景。
项目中假设某个资源时段的名额是有限的，多个用户可能会同时预约同一个时段，所以重点处理的是：
- 高并发下库存不能超卖
- 同一个用户不能重复预约
- 预约成功后的消息可靠投递
- Kafka 重复消费时不能重复处理

## 技术栈

- Spring Boot / Java 17
- MySQL
- Redis
- Redis Lua Script
- Kafka
- Docker Compose
- JMeter
- 前端页面：Vite / React

## 项目主要流程

整体预约流程大概如下：

```text
用户提交预约
 -> Redis Lua 判断库存和重复预约
 -> Redis 预扣减库存
 -> MySQL 事务写入预约记录
 -> 同事务写入 message_log（Outbox）
 -> OutboxSender 异步发送 Kafka 事件
 -> 消费端记录消费日志
```

## 主要接口

- `POST /api/bookings`：提交预约
- `POST /api/bookings/{bookingId}/cancel`：取消预约
- `GET /api/bookings/my`：查询当前用户预约
- `GET /api/bookings/slots/{slotId}`：查询时段预约记录
- `GET /api/slots`：查询可预约时段
- `GET /api/slots/{slotId}`：查询库存
- `POST /api/slots/{slotId}/warmup`：Redis 库存预热

预约和取消事件发送到 `booking-success-topic`，消费者通过 `consume_log` 的唯一键避免重复消费。

## Kafka 消费失败恢复

消费者将业务处理、事件投影、审计和 `consume_log` 放在同一个数据库事务中。处理失败时，Kafka 会按固定退避策略重试 3 次，仍失败的消息发送到 `booking-success-topic.DLT`，再由 DLT 消费者保存到 `dead_letter_log`。

死信管理接口：

- `GET /api/dead-letters`：查询死信
- `GET /api/dead-letters/{id}`：查看死信详情
- `POST /api/dead-letters/{id}/replay`：重新投递到原始 Topic
- `POST /api/dead-letters/{id}/ignore`：忽略死信

Replay 保留原始 `messageKey`，消费端通过幂等键避免重复处理。

## 故障注入验证

测试时可以显式设置环境变量：

```text
APP_FAULT_INJECTION_ENABLED=true
APP_FAULT_INJECTION_POINT=projection-update
```

支持的故障点包括 `consumer-before-process`、`projection-update`、`consume-log-write`。默认关闭。故障注入用于验证事务回滚、有限重试和 DLT 链路。

## 启动方式

### 配置本地环境变量

先复制 `.env.example` 为 `.env`，填写本机数据库账号、密码、JWT 密钥和初始化管理员信息。
`.env` 已被 Git 忽略，不要将真实值提交到仓库。项目启动脚本和一键测试脚本会自动读取该文件；直接使用 Maven 启动时，需要先将这些变量导入当前终端环境。

### 启动中间件
在项目根目录终端执行
docker compose up -d
### 启动后端
在项目根目录终端执行
.mvnw.cmd spring-boot:run
### 启动前端
在.../frontend路径终端执行
npm run dev
### 访问项目
前端页面：http://localhost:5173
后端接口：http://localhost:8080

## 关闭项目
关闭终端
关闭中间件：docker compose down
