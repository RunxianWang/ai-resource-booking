# Kafka 消费可靠性、死信与恢复方案

## 1. 文档目的

本文定义 `codex-kafka-dlt-replay` 分支的改造边界、实施顺序、数据模型和验收标准。

本次改造的目标不是单独增加一个 DLT Topic，而是形成预约事件从产生、发送、消费、失败、死信保存到恢复重放的完整闭环。

## 2. 当前实现与问题

当前预约事件通过 Outbox 发送到 `booking-success-topic`，消费者为 `BookingSuccessConsumer`。

当前消费者主要执行：

```text
收到 Kafka 消息
    ↓
写入 consume_log
    ↓
重复消息直接跳过
    ↓
记录消费日志
```

当前问题：

1. `consume_log` 和消费者业务处理没有明确处于同一个事务边界。
2. 消费者缺少可观察的下游业务处理结果。
3. 消费异常没有统一的有限重试和 DLT 策略。
4. DLT 消息没有结构化落库，也没有恢复重放能力。
5. 现有测试不足以验证事务回滚、重试、DLT 和 Replay 的完整链路。

## 3. 本次 PR 目标

本次 PR 完成：

1. 将消费者改造成薄适配层，把处理逻辑放入事务型 Service。
2. 通过 `consume_log` 唯一键和事务保证消费幂等。
3. 增加 `booking_event_projection`，保存消费者处理后的预约事件状态。
4. 增加 `booking_event_audit`，记录事件处理历史。
5. 配置有限重试和 Dead Letter Topic。
6. 增加 DLT 消费者，将失败消息保存到 `dead_letter_log`。
7. 提供死信查询、Replay 和 Ignore 管理能力。
8. 增加业务失败、SQL 失败、Kafka 发送失败等测试场景。
9. 补充单元测试、Kafka 集成测试和本地全链路验证说明。

## 4. 本次 PR 不包含的内容

以下内容不放入本次 PR：

- 多级 Retry Topic。
- Prometheus、Grafana 和复杂监控面板。
- 自动告警平台接入。
- 自动补偿和复杂状态机。
- 事件版本兼容和 Schema Registry。
- 多租户设计。
- 完整的管理员用户体系和权限中心。
- Flyway/Liquibase 数据库迁移框架改造。

这些内容可以在后续 PR 中继续建设。

## 5. 目标消息流程

```text
预约请求
    ↓
MySQL 预约事务
    ├─ booking_record
    └─ message_log（Outbox）
    ↓
OutboxSender
    ↓
booking-success-topic
    ↓
BookingSuccessConsumer
    ↓
BookingEventHandler（数据库事务）
    ├─ 检查 consume_log
    ├─ 更新 booking_event_projection
    ├─ 写入 booking_event_audit
    └─ 写入 consume_log = SUCCESS
    ↓
成功提交
```

失败时：

```text
消费者处理失败
    ↓
DefaultErrorHandler
    ↓
有限重试
    ↓
DeadLetterPublishingRecoverer
    ↓
booking-success-topic.DLT
    ↓
BookingDeadLetterConsumer
    ↓
dead_letter_log
    ↓
查询 / Replay / Ignore
```

## 6. 消费事务和幂等设计

消费者 Service 使用数据库事务包住：

```text
检查或插入 consume_log
    ↓
处理 BOOKING_RESERVED / BOOKING_CANCELLED
    ↓
更新 booking_event_projection
    ↓
写入 booking_event_audit
    ↓
consume_log 标记 SUCCESS
```

如果任意一步失败，所有数据库变更一起回滚，Kafka 消费方法抛出异常，由 Kafka Error Handler 负责重试。

重复消息通过以下唯一键保证幂等：

```text
consume_log(message_key, consumer_group)
```

重复消息不应再次执行投影更新或审计写入。

## 7. 事件投影和审计

### 7.1 booking_event_projection

用于保存消费者处理后的预约事件状态：

```text
BOOKING_RESERVED  → RESERVED
BOOKING_CANCELLED → CANCELLED
```

建议字段：

```text
id
booking_id
user_id
slot_id
event_type
booking_status
last_message_key
event_time
created_at
updated_at
```

投影更新必须具备幂等性，并保留相同 `message_key` 的重复处理保护。

### 7.2 booking_event_audit

用于记录每次事件的处理历史，至少包括：

```text
id
booking_id
message_key
consumer_group
event_type
processing_status
error_message
created_at
```

审计记录和投影更新处于同一个事务中。

## 8. 重试和 DLT 策略

第一版采用同 partition 的阻塞式重试：

```text
重试次数：3 次
退避间隔：2 秒
目标 DLT：booking-success-topic.DLT
DLT partition：保持原 partition
```

消息超过重试次数后进入 DLT，不再无限阻塞主 Topic。

## 9. dead_letter_log

死信落库至少保存：

```text
id
message_key
consumer_group
original_topic
original_partition
original_offset
payload
exception_class
exception_message
status
retry_count
replay_count
created_at
replayed_at
```

状态初步定义为：

```text
PENDING
REPLAYED
IGNORED
```

死信消费者只负责保存失败消息，不重新执行业务。

## 10. Replay 设计

Replay 流程：

```text
查询 PENDING 死信
    ↓
使用原始 message_key 和 payload 发送回原 Topic
    ↓
Kafka 发送成功
    ↓
更新死信状态为 REPLAYED
```

Replay 必须满足：

- 保留原始 `message_key`。
- Kafka 发送成功后才能更新状态。
- 已经 `REPLAYED` 的记录不能重复执行。
- 并发 Replay 必须具备状态保护。
- Replay 后仍由消费端幂等逻辑兜底。

初步接口：

```http
GET  /api/dead-letters
GET  /api/dead-letters/{id}
POST /api/dead-letters/{id}/replay
POST /api/dead-letters/{id}/ignore
```

接口属于消息运维管理能力，后续补充管理员认证、授权和操作审计。

## 11. 失败注入设计

失败注入用于自动化测试和本地全链路验证，不作为正常业务流程。

故障点包括：

```text
consumer-before-process
projection-update
consume-log-write
dlt-save
outbox-publish
```

默认配置必须关闭：

```yaml
app:
  fault-injection:
    enabled: false
    point: none
```

测试环境通过 `test` profile 或测试配置显式开启。单元测试优先使用 Mock 模拟异常，集成测试验证真实 Kafka 和数据库事务行为。

## 12. 测试计划

### 12.1 消费事务测试

- 正常消息可以成功处理。
- 重复消息不会重复更新投影。
- 重复消息不会重复写入审计。
- 投影更新失败时事务回滚。
- `consume_log` 写入成功但后续失败时，成功记录不会残留。
- 重试成功后最终只有一份有效消费结果。

### 12.2 Kafka DLT 集成测试

- 消费失败后按照配置重试。
- 超过重试次数后进入 DLT。
- DLT 消费者可以保存 `dead_letter_log`。
- 原始 Topic、partition、offset、payload 和异常信息完整保存。
- 单条毒消息不会无限重试。

### 12.3 Replay 测试

- PENDING 死信可以 Replay。
- Replay 保留原始 message key。
- Kafka 发送失败时状态不会错误变为 REPLAYED。
- 重复 Replay 不会重复执行下游业务。
- Replay 后消息可以重新进入主 Topic 并成功处理。

### 12.4 生产端 Outbox 测试

- Kafka 发送失败时 message_log 保持可重试状态。
- OutboxSender 可以继续重试待发送消息。
- 发送成功后 message_log 状态正确更新。

## 13. 实施顺序

1. 增加并确认数据库表结构。
2. 增加事务型 `BookingEventHandler`。
3. 将 `BookingSuccessConsumer` 改为薄适配层。
4. 增加 `booking_event_projection` 和 `booking_event_audit` 处理。
5. 配置 Retry 和 DLT。
6. 增加 DLT 消费者和 `dead_letter_log`。
7. 增加死信查询、Replay、Ignore 服务和接口。
8. 增加失败注入和自动化测试。
9. 更新 README 和本地验证说明。

## 14. 完成标准

本次 PR 完成时必须满足：

- 消费业务和 `consume_log` 处于同一个事务。
- 业务失败可以回滚并重试。
- 重复消费不会重复更新投影和审计。
- 消息超过重试次数后进入 DLT。
- DLT 消息可以落库查询。
- 死信可以安全 Replay 或 Ignore。
- Replay 不改变原始 message key。
- 单元测试和 Kafka 集成测试通过。
- README 记录消息完整生命周期。

## 15. 后续 PR 方向

- 管理员认证和 RBAC 权限。
- 死信处理操作审计和审批。
- Prometheus 指标和告警。
- DLT 堆积监控。
- 自动化恢复策略。
- Retry Topic 和更复杂的顺序保证方案。
- Flyway/Liquibase 数据库迁移。
- 事件版本和 Schema Registry。

