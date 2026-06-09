import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { CalendarCheck, Clock3, Cpu, Loader2, Search, Server, UserRound, Zap } from 'lucide-react';
import { toast } from 'sonner';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { createBooking, getCurrentUser, listSlots } from '@/services/reservationApi';
import {
  formatSlotRange,
  getErrorPayload,
  isBookableSlot,
  isWithinBookingWindow,
  statusLabel,
} from '@/lib/format';

const fallbackUser = { userId: 1, userName: '演示用户 1' };

const machineKeyOf = (slot) => {
  if (slot.machineId !== undefined && slot.machineId !== null) return `machine:${slot.machineId}`;
  return `name:${slot.resourceName || 'unknown-machine'}`;
};

const inferGpuModel = (machineName = '', resourceType = '') => {
  const text = `${machineName} ${resourceType}`.toUpperCase();
  const match = text.match(/H100|A100|L40S|A800|4090|3090/);
  return match?.[0] || resourceType || 'GPU';
};

const slotStatusLabel = (slot, now) => {
  if (!isWithinBookingWindow(slot, now)) return '已结束';
  if (isBookableSlot(slot, now)) return '可预约';
  if (slot.status === 'FINISHED') return '已结束';
  return '已被预约';
};

const buildMachineGroups = (slots, now) => {
  const groups = new Map();

  slots.forEach((slot) => {
    const key = machineKeyOf(slot);
    const current = groups.get(key) || {
      key,
      machineName: slot.resourceName || 'GPU 机器',
      resourceType: slot.resourceType || 'GPU',
      gpuModel: inferGpuModel(slot.resourceName, slot.resourceType),
      allSlots: [],
    };
    current.allSlots.push(slot);
    groups.set(key, current);
  });

  return Array.from(groups.values())
    .map((machine) => {
      const visibleSlots = machine.allSlots
        .filter((slot) => isWithinBookingWindow(slot, now))
        .sort((left, right) => new Date(left.startTime).getTime() - new Date(right.startTime).getTime());
      const bookableSlots = visibleSlots.filter((slot) => isBookableSlot(slot, now));
      return {
        ...machine,
        visibleSlots,
        bookableSlots,
        nextAvailableSlot: bookableSlots[0] || null,
      };
    })
    .sort((left, right) => left.machineName.localeCompare(right.machineName, 'zh-CN'));
};

const MachineMetric = ({ label, value }) => (
  <div className="rounded-md bg-slate-50 px-3 py-2">
    <div className="text-xs text-slate-500">{label}</div>
    <div className="mt-1 text-sm font-semibold text-slate-950">{value}</div>
  </div>
);

const MachineCard = ({ machine, now, onOpen }) => {
  const canReserve = Boolean(machine.nextAvailableSlot);

  return (
    <Card className="overflow-hidden border-slate-200 bg-white shadow-sm">
      <CardHeader className="border-b border-slate-100 p-5">
        <div className="flex items-start justify-between gap-4">
          <div className="flex min-w-0 items-start gap-3">
            <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-md bg-slate-950 text-white">
              <Cpu className="h-5 w-5" />
            </div>
            <div className="min-w-0">
              <CardTitle className="truncate text-lg tracking-normal">{machine.machineName}</CardTitle>
              <div className="mt-2 flex flex-wrap items-center gap-2">
                <Badge variant="outline">{machine.resourceType}</Badge>
                <Badge variant="secondary">{machine.gpuModel}</Badge>
              </div>
            </div>
          </div>
          <Badge variant={canReserve ? 'default' : 'secondary'}>
            {canReserve ? '可预约' : '12小时内暂无可预约时段'}
          </Badge>
        </div>
      </CardHeader>

      <CardContent className="space-y-4 p-5">
        <div className="grid grid-cols-2 gap-3">
          <MachineMetric
            label="最近可预约时段"
            value={machine.nextAvailableSlot ? formatSlotRange(machine.nextAvailableSlot.startTime, machine.nextAvailableSlot.endTime, now) : '-'}
          />
          <MachineMetric label="12小时内可约" value={`${machine.bookableSlots.length} 个时段`} />
        </div>

        <Button className="w-full" disabled={!canReserve} onClick={() => onOpen(machine.key)}>
          <CalendarCheck className="mr-2 h-4 w-4" />
          {canReserve ? '查看时段' : '暂无可约'}
        </Button>
      </CardContent>
    </Card>
  );
};

const SlotRow = ({ slot, now, onSelect, reserving }) => {
  const bookable = isBookableSlot(slot, now);

  return (
    <div className="grid gap-3 rounded-md border border-slate-200 p-3 sm:grid-cols-[1fr_auto_auto] sm:items-center">
      <div>
        <div className="text-sm font-medium text-slate-950">{formatSlotRange(slot.startTime, slot.endTime, now)}</div>
        <div className="mt-1 text-xs text-slate-500">预约时段</div>
      </div>
      <Badge className="w-fit" variant={bookable ? 'default' : 'secondary'}>
        {slotStatusLabel(slot, now)}
      </Badge>
      <Button size="sm" disabled={!bookable || reserving} onClick={() => onSelect(slot)}>
        {bookable ? '立即预约' : '不可预约'}
      </Button>
    </div>
  );
};

export const ResourceListPage = () => {
  const queryClient = useQueryClient();
  const [search, setSearch] = useState('');
  const [selectedMachineKey, setSelectedMachineKey] = useState(null);
  const [pendingSlot, setPendingSlot] = useState(null);
  const [bookingResult, setBookingResult] = useState(null);

  const currentUserQuery = useQuery({
    queryKey: ['currentUser'],
    queryFn: async () => {
      const res = await getCurrentUser();
      return res.data;
    },
    staleTime: 5 * 60 * 1000,
  });
  const currentUser = currentUserQuery.data || fallbackUser;

  const slotsQuery = useQuery({
    queryKey: ['slots'],
    queryFn: async () => {
      const res = await listSlots();
      return res.data;
    },
  });

  const now = useMemo(() => new Date(), [slotsQuery.dataUpdatedAt]);
  const machines = useMemo(() => buildMachineGroups(slotsQuery.data || [], now), [slotsQuery.data, now]);
  const selectedMachine = machines.find((machine) => machine.key === selectedMachineKey) || null;

  const filteredMachines = useMemo(() => {
    const keyword = search.trim().toLowerCase();
    if (!keyword) return machines;
    return machines.filter((machine) =>
      [machine.machineName, machine.resourceType, machine.gpuModel]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(keyword))
    );
  }, [machines, search]);

  const reserveMutation = useMutation({
    mutationFn: async () => {
      const res = await createBooking(pendingSlot.id);
      return res.data;
    },
    onSuccess: async (data) => {
      setBookingResult(data);
      await queryClient.invalidateQueries({ queryKey: ['slots'] });
      await queryClient.invalidateQueries({ queryKey: ['bookings', 'my'] });
      if (data.code === 'SUCCESS') {
        toast.success('预约成功');
        setPendingSlot(null);
      } else {
        toast.error(data.message || data.code || '预约失败');
      }
    },
    onError: (err) => {
      const payload = getErrorPayload(err);
      setBookingResult(payload);
      toast.error(payload.message);
    },
  });

  const openSlotDialog = (machineKey) => {
    setSelectedMachineKey(machineKey);
    setPendingSlot(null);
    setBookingResult(null);
  };

  const closeDialog = (open) => {
    if (!open && !reserveMutation.isPending) {
      setSelectedMachineKey(null);
      setPendingSlot(null);
      setBookingResult(null);
    }
  };

  return (
    <div className="mx-auto max-w-7xl space-y-6">
      <section className="flex flex-col gap-4 rounded-md border border-slate-200 bg-white p-5 shadow-sm md:flex-row md:items-end md:justify-between">
        <div>
          <div className="flex items-center gap-2 text-sm font-medium text-slate-500">
            <Server className="h-4 w-4" />
            资源列表
          </div>
          <h1 className="mt-2 text-2xl font-semibold tracking-normal text-slate-950">选择 GPU 机器和预约时段</h1>
          <p className="mt-2 max-w-2xl text-sm text-slate-500">
            每台机器展示为一张资源卡片，点击后选择未来 12 小时内的一小时时段。
          </p>
        </div>
        <Button variant="outline" onClick={() => slotsQuery.refetch()} disabled={slotsQuery.isFetching}>
          {slotsQuery.isFetching ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Zap className="mr-2 h-4 w-4" />}
          刷新资源
        </Button>
      </section>

      <section className="rounded-md border border-slate-200 bg-white p-4 shadow-sm">
        <div className="relative max-w-xl">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
          <Input
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            className="pl-9"
            placeholder="搜索机器名称或型号，例如 H100 / A100"
          />
        </div>
      </section>

      {slotsQuery.isLoading ? (
        <div className="flex h-64 items-center justify-center rounded-md border border-slate-200 bg-white">
          <Loader2 className="mr-2 h-5 w-5 animate-spin" />
          正在加载资源
        </div>
      ) : slotsQuery.isError ? (
        <div className="rounded-md border border-red-200 bg-red-50 p-5 text-sm text-red-700">
          资源加载失败：{getErrorPayload(slotsQuery.error).message}
        </div>
      ) : filteredMachines.length === 0 ? (
        <div className="rounded-md border border-slate-200 bg-white p-10 text-center text-sm text-slate-500">
          没有匹配的机器
        </div>
      ) : (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {filteredMachines.map((machine) => (
            <MachineCard key={machine.key} machine={machine} now={now} onOpen={openSlotDialog} />
          ))}
        </div>
      )}

      <Dialog open={Boolean(selectedMachine)} onOpenChange={closeDialog}>
        <DialogContent className="sm:max-w-2xl">
          {!pendingSlot ? (
            <>
              <DialogHeader>
                <DialogTitle>选择预约时段</DialogTitle>
                <DialogDescription>
                  {selectedMachine?.machineName} 未来 12 小时内的可预约窗口。
                </DialogDescription>
              </DialogHeader>

              <div className="space-y-3">
                {bookingResult && (
                  <div
                    className={
                      bookingResult.code === 'SUCCESS'
                        ? 'rounded-md border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-800'
                        : 'rounded-md border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800'
                    }
                  >
                    <div className="font-semibold">{bookingResult.code}</div>
                    <div className="mt-1">{bookingResult.message || bookingResult.reason}</div>
                    {bookingResult.reason && bookingResult.reason !== bookingResult.message && (
                      <div className="mt-1">reason: {bookingResult.reason}</div>
                    )}
                  </div>
                )}

                {selectedMachine?.visibleSlots.length ? (
                  selectedMachine.visibleSlots.map((slot) => (
                    <SlotRow
                      key={slot.id}
                      slot={slot}
                      now={now}
                      onSelect={(nextSlot) => {
                        setPendingSlot(nextSlot);
                        setBookingResult(null);
                      }}
                      reserving={reserveMutation.isPending}
                    />
                  ))
                ) : (
                  <div className="rounded-md border border-slate-200 bg-slate-50 p-6 text-center text-sm text-slate-500">
                    未来 12 小时内暂无可查看时段
                  </div>
                )}
              </div>

              <DialogFooter>
                <Button variant="outline" onClick={() => closeDialog(false)} disabled={reserveMutation.isPending}>
                  关闭
                </Button>
              </DialogFooter>
            </>
          ) : (
            <>
              <DialogHeader>
                <DialogTitle>确认预约</DialogTitle>
                <DialogDescription>预约成功后将占用该机器该时段的使用权。</DialogDescription>
              </DialogHeader>

              <div className="space-y-4">
                <div className="rounded-md border border-slate-200 bg-slate-50 p-4">
                  <div className="flex items-center gap-2 font-semibold text-slate-950">
                    <Cpu className="h-4 w-4" />
                    {selectedMachine?.machineName}
                  </div>
                  <div className="mt-3 flex items-center gap-2 text-sm text-slate-700">
                    <Clock3 className="h-4 w-4 text-slate-500" />
                    {formatSlotRange(pendingSlot.startTime, pendingSlot.endTime, now)}
                  </div>
                </div>

                <div className="flex items-center gap-2 rounded-md border border-slate-200 p-3 text-sm text-slate-700">
                  <UserRound className="h-4 w-4" />
                  当前用户：{currentUser.userName}
                </div>

                {bookingResult && bookingResult.code !== 'SUCCESS' && (
                  <div className="rounded-md border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">
                    <div className="font-semibold">{bookingResult.code}</div>
                    <div className="mt-1">{bookingResult.message || bookingResult.reason}</div>
                    {bookingResult.reason && bookingResult.reason !== bookingResult.message && (
                      <div className="mt-1">reason: {bookingResult.reason}</div>
                    )}
                  </div>
                )}
              </div>

              <DialogFooter>
                <Button variant="outline" onClick={() => setPendingSlot(null)} disabled={reserveMutation.isPending}>
                  返回时段
                </Button>
                <Button onClick={() => reserveMutation.mutate()} disabled={reserveMutation.isPending}>
                  {reserveMutation.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                  确认预约
                </Button>
              </DialogFooter>
            </>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
};
