# 基础链路收敛验收报告

## 结论

基础链路验收通过，基础预约压测通过。

验收对象：`base-chain-convergence`  
测试服务：`http://localhost:8081`，`perf` profile  
测试资源：专用 `slot=900001`，容量 100

## 构建与自动化测试

- `mvn -q -DskipTests compile`：PASS
- `mvn -q -Dtest=BookingEventHandlerTest -DforkCount=0 test`：PASS
- `mvn -q -DskipTests package`：PASS
- 完整 `mvn -q test`：未完成；Spring 上下文测试长时间不退出，已停止并单独记录为测试生命周期环境问题。

## 基础并发压测

测试模型：500 个不同用户，以并发数 100 预约 100 个库存。

| 指标 | 结果 |
|---|---:|
| 请求数 | 500 |
| 并发数 | 100 |
| SUCCESS | 100 |
| SOLD_OUT | 400 |
| 系统错误 | 0 |
| TPS | 278.38 |
| P50 | 272.19 ms |
| P95 | 548.72 ms |
| P99 | 725.32 ms |
| MySQL 最终库存 | 0 |
| Redis 最终库存 | 0 |
| RESERVED 预约数 | 100 |
| Redis 用户数 | 100 |

结果：PASS。无超卖、无系统错误，MySQL 与 Redis 库存及用户集合最终一致，Outbox/Kafka 消费最终追平 100 条。

## 边界验收

### 同用户重复预约

20 个并发请求使用同一个用户：

- SUCCESS：1
- DUPLICATE_BOOKING：19
- 库存只减少 1

结果：PASS。

### 并发取消

20 个并发请求取消同一条有效预约：

- SUCCESS：1
- BOOKING_CANCEL_SKIPPED：19
- MySQL 库存恢复到 100
- Redis 库存恢复到 100
- Redis 用户集合恢复到 0

结果：PASS。

## 本轮实际完成的变更

- MySQL 库存扣减不再修改 `resource_slot.status`。
- 库存恢复使用 `LEAST(available_count + 1, total_count)`。
- Redis 补偿统一使用 Lua，避免多命令非原子问题。
- 预约/取消数据库事务拆到 `BookingTransactionService`，Redis 编排位于事务外。
- 新增 `CurrentUserProvider` 和 perf profile 的 `X-Test-User-Id`。
- warmup 启动时同步恢复 stock 和 RESERVED 用户集合。
- 新增基础链路压测脚本、边界测试脚本和报告图表生成脚本。

## 已知问题与边界

- 启动日志发现已有历史数据存在重复 `(user_id, slot_id)`：`86053001-3`，因此唯一索引初始化跳过。本轮没有删除或修改该既有业务数据。
- 本报告只覆盖基础预约链路和基础 Outbox 消费收敛，不代表 Kafka DLT、Replay、故障注入和正式生产性能验收已经完成。
- TPS 是当前本机 Docker/MySQL/Redis/Kafka 环境下、500 请求样本的观测值，不是容量上限或生产承诺。

原始压测数据：`test-results/base-chain/20260831-130553/metrics.json`、`requests.csv`。  
延迟图：`test-results/base-chain/20260831-130553/latency.png`。  
汇总图：`test-results/base-chain/20260831-130553/acceptance-summary.png`。
