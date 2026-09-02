# 验收报告

## 1. 报告结论

预约核心链路验收通过。

重点验证了高并发库存控制、重复预约幂等、并发取消、MySQL/Redis 最终一致性、事务回滚、Outbox、Kafka 消费幂等、故障补偿、DLT 和 Replay。最终 Full 与 Resilience 回归均通过。

测试使用隔离的测试机器、测试槽位和测试用户；测试结束后会清理测试夹具，不依赖演示预约数据。

## 2. 核心业务流程

```text
预约 API
  -> 校验预约时长、当天范围和连续槽位
  -> Redis Lua 原子预扣多个槽位
  -> MySQL 事务更新库存
  -> 写入 booking_record 和 Outbox message_log
  -> OutboxSender 发送 Kafka
  -> Consumer 通过 consume_log 幂等处理
  -> 更新 booking_event_projection 和审计记录
```

Redis 预扣成功但 MySQL 事务失败时，会执行 Redis Lua 补偿。取消预约通过条件更新保证并发下只有一个请求成功，并同时恢复 MySQL 和 Redis 库存。

## 3. 关键验收指标

### 3.1 最终 Full 回归

| 指标 | 结果 |
|---|---:|
| 不同用户请求数 | 500 |
| 并发数 | 100 |
| 成功预约 | 100 |
| 正常售罄响应 | 400 |
| 系统错误 | 0 |
| MySQL 可用库存 | 0 |
| Redis 可用库存 | 0 |
| RESERVED 预约记录 | 100 |
| Redis 已预约用户数 | 100 |
| Outbox 消息数 | 100 |
| 成功消费数 | 100 |
| 消费日志数 | 100 |
| 库存一致性 | PASS |
| 消息最终一致性 | PASS |
| TPS | 约 361 |
| P50 延迟 | 约 203 ms |
| P95 延迟 | 约 525 ms |
| P99 延迟 | 约 614 ms |

`SUCCESS=100` 等于测试库存容量；其余请求均得到预期的 `SOLD_OUT`，没有发生超卖或系统异常。

### 3.2 历史 50,000 请求基准压测

该结果作为历史基准数据保留，用于展示更大请求量下的行为，不作为生产容量承诺。

| 指标 | 结果 |
|---|---:|
| 请求数 | 50,000 |
| 并发数 | 200 |
| 成功预约 | 100 |
| 正常售罄响应 | 49,900 |
| 系统错误 | 0 |
| TPS | 约 437 |
| P50 延迟 | 约 33 ms |
| P95 延迟 | 约 603 ms |
| P99 延迟 | 约 767 ms |
| MySQL/Redis 最终库存 | 一致 |
| 消息最终一致性 | PASS |

该压测同时暴露了本机短连接过多导致临时端口耗尽的问题，随后通过线程级 HTTP 连接复用解决。这说明压测结果不仅验证业务，也验证了测试工具和客户端连接模型。

## 4. 场景验收结果

| 编号 | 场景 | 关键结果 | 结论 |
|---|---|---|---|
| T01 | 不同用户并发抢库存 | 100 成功、400 售罄、无系统错误 | PASS |
| T02 | 同用户并发重复预约 | 1 成功、19 次重复预约 | PASS |
| T03 | 同预约并发取消 | 1 成功、19 次幂等跳过 | PASS |
| T04 | MySQL、Redis 和预约记录一致性 | 库存、用户集合、预约记录最终一致 | PASS |
| T05 | 唯一约束和事务回滚 | 唯一索引生效，失败请求无半条预约记录 | PASS |
| T06 | Kafka 重复消费幂等 | 消费记录 1 条、投影记录 1 条，重复指标增加 | PASS |
| T07 | Redis 故障补偿 | 预扣后故障，库存由 2 恢复为 2，无预约残留 | PASS |
| T08 | Kafka 暂停和恢复 | Kafka 恢复后 Outbox 消息继续发送并消费 | PASS |
| T09 | OutboxSender 重启恢复 | `INIT` 消息重启后恢复为 `SENT` | PASS |
| T10 | DLT 重试和死信入库 | 重试 3 次后进入 DLT 并写入死信表 | PASS |
| T11 | Replay 并发保护 | 20 次并发 Replay 只有 1 次成功，`replay_count=1` | PASS |
| T12 | 预约时长和当天边界 | 1/2/4 小时、下一整点、跨天限制均通过 | PASS |

## 5. 核心数据库设计

### 5.1 有效预约唯一索引

预约表使用生成列区分“有效预约”和“历史预约”：

```sql
active_user_id = CASE WHEN status = 'RESERVED' THEN user_id ELSE NULL END
active_slot_id = CASE WHEN status = 'RESERVED' THEN slot_id ELSE NULL END

UNIQUE KEY uk_active_user_slot(active_user_id, active_slot_id)
```

该设计解决了旧索引直接使用 `(user_id, slot_id)` 带来的问题：用户取消预约后，历史记录不会继续阻塞重新预约。

当前规则是：

- `RESERVED` 记录参与唯一性限制。
- `CANCELLED` 和 `FINISHED` 记录不参与唯一性限制。
- 历史记录保留，不需要为了重新预约而删除数据。
- 数据库唯一约束作为并发场景下的最终兜底。

### 5.2 其他重要索引和约束

- `resource_slot.uk_machine_time`：同一机器不能生成重复时间段。
- `booking_record.idx_slot_id`：按槽位查询预约。
- `booking_record.idx_machine_id`：按机器查询关联预约。
- `message_log.uk_message_key`：同一 Outbox 消息不能重复创建。
- `consume_log.uk_message_consumer_group`：同一消费者组不能重复处理同一消息。
- `booking_event_projection.uk_projection_booking`：同一预约只保留一份最新投影。

当前 ID 仍使用数据库自增主键。项目是单库写入模型，暂不引入雪花算法，避免增加没有必要的分布式 ID 复杂度。

## 6. 消息可靠性设计

- 预约记录和 Outbox 消息在同一个 MySQL 事务中写入。
- Kafka 发送失败时，消息保持 `INIT` 或 `FAILED`，由 OutboxSender 后续重试。
- Kafka 消费业务、投影、审计和消费日志放在同一个事务中。
- 消费失败后固定重试 3 次，仍失败则进入 DLT。
- Replay 通过 `PENDING -> REPLAYING -> REPLAYED` 状态抢占，避免并发重复 Replay。
- 重复消息保留原始 `messageKey`，由消费唯一键保证幂等。

## 7. 构建与回归验证

已验证：

- Maven 完整测试：PASS
- 前端 ESLint：PASS
- 前端生产构建：PASS
- Python 测试脚本编译：PASS
- PowerShell 测试脚本解析：PASS
- Git 差异格式检查：PASS
- 敏感值和本机路径扫描：PASS

## 8. 测试边界

以上结论基于本地 Docker MySQL、Redis 和 Kafka 环境，不能直接等同于生产容量或生产安全审计结论。当前报告不覆盖完整 OWASP 审计、多节点 Kafka、高可用数据库、长时间 soak test 和大规模前端组件测试。

## 9. 复现方式

准备本机 `.env` 后，可运行：

```powershell
.\scripts\verify-backend.ps1 -Suite Fast
.\scripts\verify-backend.ps1 -Suite Full
.\scripts\verify-backend.ps1 -Suite Resilience
.\scripts\verify-backend.ps1 -Suite Load -Requests 50000 -Concurrency 200
```

