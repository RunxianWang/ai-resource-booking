# 基础链路收敛实施计划

## 1. 目标

本轮定义为基础链路收敛，不进行架构重构。保留现有主流程：

```text
Redis Lua 并发闸门
    -> MySQL 业务事务
    -> message_log Outbox
    -> Kafka
    -> consume_log 幂等消费
```

最终边界固定为：

- Redis 负责高并发预扣库存和用户快速判重。
- MySQL 负责库存、预约状态和唯一约束的最终正确性。
- `message_log` 与预约/取消业务事务绑定。
- Kafka 不参与库存正确性判断。
- Redis 故障时允许暂时少报库存，但不能导致 MySQL 超卖。

## 2. 已确认的现状

- 当前 `booking_record` 已存在 `uk_user_slot(user_id, slot_id)`，本轮重点是验证其初始化和历史数据兼容性。
- `BookingService` 当前同时承担 Redis 编排和数据库事务。
- 预约失败补偿当前使用 Redis 多条命令，不是原子操作。
- 取消链路当前没有按数据库事务提交之后恢复 Redis 的清晰边界。
- warmup 当前恢复库存键，但启动 warmup 没有完整恢复 RESERVED 用户集合。
- `resource_slot.status` 仍与库存语义混用，需要与 `available_count` 解耦。

## 3. 实施范围

### 3.1 状态模型与数据库

- 预约成功只执行 `available_count - 1`。
- 取消成功只执行 `available_count + 1`。
- 不再使用 `AVAILABLE`/`RESERVED` 表示库存是否售罄。
- 保留 `booking_record` 的 `RESERVED -> CANCELLED` 单向生命周期。
- Phase 1 不支持取消后再次预约同一 slot。
- 核查历史数据后保留或补齐 `(user_id, slot_id)` 唯一约束。

### 3.2 预约链路

- 拆分应用编排层与数据库事务层。
- Redis reserve Lua 只负责用户判重、库存检查、SADD 和 DECR。
- MySQL 事务依次执行库存 CAS 扣减、预约插入、Outbox 插入。
- 任一步失败，数据库回滚；Redis 执行幂等补偿。
- 统一返回成功、重复、售罄、未初始化等结果码。

### 3.3 取消链路

- 使用 `WHERE status = 'RESERVED'` 的 CAS 更新预约状态。
- 只有 affected rows 为 1 时才恢复 MySQL 库存并写取消 Outbox。
- 数据库事务提交后再执行 Redis restore Lua。
- 重复取消不得重复增加库存或重复写有效取消事件。

### 3.4 Redis、身份和恢复

- 新增或统一 `compensate.lua` / `restore.lua`，使用 `SREM` 结果保证补偿幂等。
- warmup/rebuild 同时恢复 stock 和 RESERVED 用户集合。
- 正常预约流程不自动触发 warmup。
- 引入 `CurrentUserProvider`，区分 demo、perf 和未来正式身份来源。
- 仅 perf profile 允许通过 `X-Test-User-Id` 注入压测用户。

### 3.5 验证与测试

建立以下 invariant：

```text
I1: 0 <= available_count <= total_count
I2: available_count = total_count - RESERVED booking 数
I3: 同一 user + slot 最多一条 booking_record
I4: 稳定状态下 Redis stock = MySQL available_count
I5: 稳定状态下 Redis users = MySQL RESERVED 用户集合
```

验证顺序：

1. 单线程基础预约和取消。
2. 同用户重复预约。
3. 同一预约并发取消。
4. 500 个用户竞争 100 个库存。
5. 500 个请求使用同一个用户。
6. Redis、MySQL、Outbox 故障补偿。
7. Kafka 停止、重复投递和 Outbox 重启恢复。
8. 最后进行 TPS、P50、P95、P99 性能测试。

## 4. 预计修改文件

- `src/main/java/com/wrx/booking/service/BookingService.java`
- 新增 `BookingApplicationService.java`
- 新增或拆分 `BookingTxService.java`
- `src/main/java/com/wrx/booking/repository/ResourceSlotRepository.java`
- `src/main/java/com/wrx/booking/repository/BookingRecordRepository.java`
- `src/main/java/com/wrx/booking/service/SlotService.java`
- `src/main/java/com/wrx/booking/service/RedisStockWarmUpRunner.java`
- Redis Lua 脚本及相关配置类
- `CurrentUserProvider` 及各 profile 实现
- 相关 schema initializer、`src/main/resources/schema.sql`
- 基础链路测试、并发测试和 consistency checker

原则上不修改前端，不重构 Kafka consumer，不引入分布式锁、悲观锁、TCC、Saga、Seata 或 Kafka 扣库存。

## 5. 风险与处理

- 历史重复预约数据可能导致唯一约束校验失败：先检查并单独报告，不直接删除数据。
- 旧的 `status` 语义可能被查询或演示逻辑依赖：修改前逐处确认。
- Redis 与 MySQL 在故障窗口可能暂时不一致：以 MySQL 不超卖为最高优先级，通过 rebuild/reconcile 收敛。
- 取消的过期时间规则需要保留，不能因 CAS 改造而扩大取消权限。

## 6. 执行约束

- 本分支先完成基础链路代码和测试，再进入性能与 Kafka 实验。
- 每个阶段先通过 invariant，再进入下一阶段。
- 任何 invariant 失败时停止扩展测试范围，只修复当前层。
- 不删除重要数据，不修改生产配置，不设计 rebooking V2。
