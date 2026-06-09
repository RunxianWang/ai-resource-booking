import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { useReservation } from '@/hooks/useReservation';
import { ResultCard } from '@/components/ResultCard';
import {
  Activity,
  CalendarCheck,
  CheckCircle2,
  Cpu,
  Database,
  Flame,
  Loader2,
  RotateCcw,
  Server,
  ShieldCheck,
} from 'lucide-react';

const resourceNames = {
  1001: 'A100 GPU 训练资源池',
  1002: 'H100 GPU 推理资源池',
  1003: '云端大模型微调集群',
};

const getResourceName = (resourceId) => resourceNames[resourceId] || `AI 算力资源 ${resourceId ?? '-'}`;

const formatDateTime = (value) => {
  if (!value) return '-';
  return String(value).replace('T', ' ').slice(0, 19);
};

const statusLabel = (data) => {
  if (!data) return '加载中';
  const available = data.redisAvailableCount ?? data.mysqlAvailableCount ?? 0;
  return available > 0 ? '可预约' : '已约满';
};

const statusVariant = (data) => {
  if (!data) return 'secondary';
  const available = data.redisAvailableCount ?? data.mysqlAvailableCount ?? 0;
  return available > 0 ? 'default' : 'destructive';
};

const Metric = ({ label, value, icon: Icon }) => (
  <div className="rounded-md border bg-white p-4">
    <div className="flex items-center gap-2 text-sm text-muted-foreground">
      <Icon className="h-4 w-4" />
      <span>{label}</span>
    </div>
    <div className="mt-2 text-2xl font-semibold">{value ?? '-'}</div>
  </div>
);

const BooleanMetric = ({ label, value }) => (
  <div className="rounded-md border bg-white p-4">
    <div className="text-sm text-muted-foreground">{label}</div>
    <div className={value ? 'mt-2 flex items-center gap-2 font-semibold text-emerald-700' : 'mt-2 flex items-center gap-2 font-semibold text-red-600'}>
      <CheckCircle2 className="h-4 w-4" />
      {value == null ? '-' : value ? '一致' : '异常'}
    </div>
  </div>
);

const ResourceCard = ({ data, userId, setUserId, loading, onBook }) => {
  const available = data?.redisAvailableCount ?? data?.mysqlAvailableCount;
  const resourceId = data?.resourceId ?? 1001;
  const slotId = data?.id ?? 1;

  return (
    <Card className="overflow-hidden border-slate-200 shadow-sm">
      <CardHeader className="border-b bg-white">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
          <div className="space-y-2">
            <div className="flex items-center gap-2">
              <div className="flex h-10 w-10 items-center justify-center rounded-md bg-slate-900 text-white">
                <Cpu className="h-5 w-5" />
              </div>
              <div>
                <CardTitle className="text-xl">{getResourceName(resourceId)}</CardTitle>
                <p className="text-sm text-muted-foreground">resourceId: {resourceId} / slotId: {slotId}</p>
              </div>
            </div>
          </div>
          <Badge variant={statusVariant(data)} className="w-fit">{statusLabel(data)}</Badge>
        </div>
      </CardHeader>
      <CardContent className="space-y-5 p-5">
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <div className="rounded-md bg-slate-50 p-3">
            <div className="text-xs text-muted-foreground">开始时间</div>
            <div className="mt-1 font-medium">{formatDateTime(data?.startTime)}</div>
          </div>
          <div className="rounded-md bg-slate-50 p-3">
            <div className="text-xs text-muted-foreground">结束时间</div>
            <div className="mt-1 font-medium">{formatDateTime(data?.endTime)}</div>
          </div>
          <div className="rounded-md bg-slate-50 p-3">
            <div className="text-xs text-muted-foreground">总名额</div>
            <div className="mt-1 font-medium">{data?.totalCount ?? '-'}</div>
          </div>
          <div className="rounded-md bg-emerald-50 p-3">
            <div className="text-xs text-emerald-700">当前剩余名额</div>
            <div className="mt-1 text-2xl font-semibold text-emerald-800">{available ?? '-'}</div>
          </div>
        </div>

        <div className="grid gap-4 border-t pt-5 md:grid-cols-[1fr_auto] md:items-end">
          <div className="space-y-2">
            <Label htmlFor={`user-${slotId}`}>用户 ID</Label>
            <Input
              id={`user-${slotId}`}
              type="number"
              value={userId}
              onChange={(e) => setUserId(e.target.value)}
              placeholder="请输入预约用户 ID"
            />
          </div>
          <Button
            onClick={() => onBook(userId, slotId)}
            disabled={loading || !userId}
            className="h-10 min-w-36"
          >
            {loading ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <CalendarCheck className="mr-2 h-4 w-4" />}
            立即预约
          </Button>
        </div>

        <div className="rounded-md border bg-slate-50 p-3 text-sm">
          <div className="font-medium">技术详情</div>
          <div className="mt-2 grid gap-2 sm:grid-cols-2">
            <span>MySQL 剩余：{data?.mysqlAvailableCount ?? '-'}</span>
            <span>Redis 剩余：{data?.redisAvailableCount ?? '-'}</span>
          </div>
        </div>
      </CardContent>
    </Card>
  );
};

const Index = () => {
  const {
    slotId,
    setSlotId,
    userId,
    setUserId,
    inventory,
    businessResult,
    debugResult,
    consistency,
    loading,
    handleQuery,
    handleWarmup,
    handleBook,
    handleReset,
    handleVerify,
    refreshDashboard,
  } = useReservation();

  const showSlot = () => refreshDashboard(slotId);

  return (
    <main className="min-h-screen bg-slate-50">
      <div className="mx-auto max-w-6xl space-y-6 px-4 py-6 sm:px-6 lg:px-8">
        <header className="flex flex-col gap-4 rounded-none border-b border-slate-200 pb-5 md:flex-row md:items-end md:justify-between">
          <div className="space-y-2">
            <div className="flex items-center gap-3 text-sm font-medium text-slate-600">
              <Server className="h-4 w-4" />
              高并发资源预约系统演示
            </div>
            <h1 className="text-3xl font-bold tracking-normal text-slate-950 sm:text-4xl">AI 算力资源预约平台</h1>
            <p className="max-w-3xl text-sm text-muted-foreground sm:text-base">
              基于 Spring Boot、Redis、MySQL、Kafka 的高并发资源预约系统演示。
            </p>
          </div>
          <Button onClick={showSlot} disabled={loading} variant="outline" className="w-full md:w-auto">
            {loading ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Activity className="mr-2 h-4 w-4" />}
            刷新数据
          </Button>
        </header>

        <section className="space-y-3">
          <div className="flex items-center justify-between gap-3">
            <h2 className="text-xl font-semibold">可预约资源</h2>
            <span className="text-sm text-muted-foreground">默认展示 slotId=1</span>
          </div>
          <ResourceCard
            data={inventory}
            userId={userId}
            setUserId={setUserId}
            loading={loading}
            onBook={handleBook}
          />
        </section>

        {businessResult && <ResultCard data={businessResult} mode="business" />}

        <section className="space-y-3">
          <h2 className="text-xl font-semibold">系统链路状态</h2>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <Metric label="MySQL 剩余" value={consistency?.mysqlAvailableCount ?? inventory?.mysqlAvailableCount} icon={Database} />
            <Metric label="Redis 剩余" value={inventory?.redisAvailableCount} icon={Server} />
            <Metric label="成功预约数" value={consistency?.successBookingCount} icon={CalendarCheck} />
            <Metric label="消息日志数" value={consistency?.messageLogCount} icon={Activity} />
            <Metric label="消费日志数" value={consistency?.consumeLogCount} icon={Activity} />
            <BooleanMetric label="库存一致性" value={consistency?.stockConsistent} />
            <BooleanMetric label="消息一致性" value={consistency?.messageConsistent} />
            <Metric label="已消费消息数" value={consistency?.consumedMessageCount} icon={ShieldCheck} />
          </div>
        </section>

        <section className="space-y-3 border-t pt-6">
          <div>
            <h2 className="text-xl font-semibold">开发者工具</h2>
            <p className="mt-1 text-sm text-muted-foreground">用于本地演示环境的数据预热、重置和一致性校验。</p>
          </div>
          <Card>
            <CardContent className="grid gap-4 p-5 md:grid-cols-[1fr_auto_auto_auto_auto] md:items-end">
              <div className="space-y-2">
                <Label htmlFor="slotId">Slot ID</Label>
                <Input
                  id="slotId"
                  type="number"
                  value={slotId}
                  onChange={(e) => setSlotId(e.target.value)}
                  placeholder="请输入 Slot ID"
                />
              </div>
              <Button onClick={() => handleQuery(slotId)} disabled={loading} variant="outline">
                查看库存
              </Button>
              <Button onClick={handleWarmup} disabled={loading} variant="secondary">
                <Flame className="mr-2 h-4 w-4" />
                预热 Redis
              </Button>
              <Button onClick={() => handleVerify(slotId)} disabled={loading} variant="outline">
                <ShieldCheck className="mr-2 h-4 w-4" />
                数据一致性校验
              </Button>
              <Button onClick={handleReset} disabled={loading} variant="destructive">
                <RotateCcw className="mr-2 h-4 w-4" />
                重置数据
              </Button>
            </CardContent>
          </Card>
        </section>

        <ResultCard data={debugResult} mode="debug" />
      </div>
    </main>
  );
};

export default Index;
