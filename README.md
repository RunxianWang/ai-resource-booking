# AI 算力资源预约平台

这是一个基于 Spring Boot 的高并发资源预约项目，主要模拟实验室 GPU / AI 训练资源的预约场景。
项目中假设某个资源时段的名额是有限的，多个用户可能会同时预约同一个时段，所以重点处理的是：
- 高并发下库存不能超卖
- 同一个用户不能重复预约
- 预约成功后的消息不能丢
- Kafka 重复消费时不能重复处理

## 技术栈

- Spring Boot / Java 17
- MySQL
- Redis
- Redis Lua Script
- Kafka
- Docker Compose
- JMeter
- 前端页面：TODO：按你的实际前端技术栈填写

## 项目主要流程

整体预约流程大概如下：

```text
用户提交预约
 -> Redis Lua 判断库存和重复预约
 -> Redis 预扣减库存
 -> MySQL 事务写入预约记录
 -> 写入 message_log
 -> Kafka 发送预约成功事件
 -> 消费端记录消费日志
```

## 启动方式

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
