import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { CalendarClock, Clock3, Loader2, UserRound, XCircle } from 'lucide-react';
import { toast } from 'sonner';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { cancelBooking, getCurrentUser, listMyBookings, queryInventory } from '@/services/reservationApi';
import { formatDateTime, formatSlotRange, getErrorPayload, statusLabel } from '@/lib/format';

const fallbackUser = { userId: 1, userName: '演示用户 1' };

const statusVariant = (status) => {
  if (status === 'RESERVED' || status === 'SUCCESS') return 'default';
  if (status === 'CANCELLED' || status === 'CANCELED' || status === 'FINISHED') return 'secondary';
  return 'outline';
};

const canCancel = (booking) => booking.status === 'RESERVED' || booking.status === 'SUCCESS';

const BookingRow = ({ booking, onCancel, canceling }) => (
  <Card className="border-slate-200 bg-white shadow-sm">
    <CardContent className="grid gap-4 p-4 md:grid-cols-[1.3fr_1.2fr_0.7fr_0.9fr_auto] md:items-center">
      <div>
        <div className="font-semibold text-slate-950">{booking.resourceName || 'GPU 机器'}</div>
        <div className="mt-1 text-sm text-slate-500">机器名称</div>
      </div>
      <div>
        <div className="flex items-center gap-2 text-sm font-medium text-slate-950">
          <Clock3 className="h-4 w-4 text-slate-500" />
          {booking.slotDetail
            ? formatSlotRange(booking.slotDetail.startTime, booking.slotDetail.endTime)
            : '时段信息不可用'}
        </div>
        <div className="mt-1 text-sm text-slate-500">预约时段</div>
      </div>
      <div>
        <div className="text-xs text-slate-500">状态</div>
        <Badge className="mt-1" variant={statusVariant(booking.status)}>
          {statusLabel(booking.status)}
        </Badge>
      </div>
      <div>
        <div className="text-xs text-slate-500">提交时间</div>
        <div className="mt-1 text-sm font-medium">{formatDateTime(booking.createdAt)}</div>
      </div>
      <div className="flex justify-end">
        {canCancel(booking) && (
          <Button
            variant="outline"
            disabled={canceling}
            onClick={() => onCancel(booking.bookingId)}
            className="border-red-200 text-red-700 hover:bg-red-50 hover:text-red-800"
          >
            {canceling ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <XCircle className="mr-2 h-4 w-4" />}
            取消预约
          </Button>
        )}
      </div>
    </CardContent>
  </Card>
);

export const MyBookingsPage = () => {
  const queryClient = useQueryClient();
  const [cancelingBookingId, setCancelingBookingId] = useState(null);

  const currentUserQuery = useQuery({
    queryKey: ['currentUser'],
    queryFn: async () => {
      const res = await getCurrentUser();
      return res.data;
    },
    staleTime: 5 * 60 * 1000,
  });
  const currentUser = currentUserQuery.data || fallbackUser;

  const bookingsQuery = useQuery({
    queryKey: ['bookings', 'my'],
    queryFn: async () => {
      const bookingRes = await listMyBookings();
      const bookings = bookingRes.data || [];
      const slotIds = Array.from(new Set(bookings.map((booking) => booking.slotId).filter(Boolean)));

      const detailEntries = await Promise.all(
        slotIds.map(async (slotId) => {
          try {
            const detailRes = await queryInventory(slotId);
            return [slotId, detailRes.data];
          } catch {
            return [slotId, null];
          }
        })
      );
      const detailsBySlotId = new Map(detailEntries);

      return bookings.map((booking) => ({
        ...booking,
        slotDetail: detailsBySlotId.get(booking.slotId) || null,
      }));
    },
  });

  const cancelMutation = useMutation({
    mutationFn: async (bookingId) => {
      setCancelingBookingId(bookingId);
      const res = await cancelBooking(bookingId);
      return res.data;
    },
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['bookings', 'my'] });
      queryClient.invalidateQueries({ queryKey: ['slots'] });
      if (data.code === 'SUCCESS') {
        toast.success('已取消预约');
      } else {
        toast.error(data.message || data.code || '取消失败');
      }
    },
    onError: (err) => {
      const payload = getErrorPayload(err);
      toast.error(payload.message);
    },
    onSettled: () => setCancelingBookingId(null),
  });

  const bookings = bookingsQuery.data || [];

  return (
    <div className="mx-auto max-w-7xl space-y-6">
      <section className="rounded-md border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex items-center gap-2 text-sm font-medium text-slate-500">
          <CalendarClock className="h-4 w-4" />
          我的预约
        </div>
        <h1 className="mt-2 text-2xl font-semibold tracking-normal text-slate-950">查看并管理我的预约记录</h1>
        <p className="mt-2 max-w-2xl text-sm text-slate-500">
          这里展示当前演示用户的机器时段预约。已预约状态可以取消，已取消或已结束的预约仅保留记录。
        </p>
        <div className="mt-4 inline-flex items-center gap-2 rounded-md border border-slate-200 px-3 py-2 text-sm text-slate-700">
          <UserRound className="h-4 w-4" />
          当前用户：{currentUser.userName}
        </div>
      </section>

      <section className="space-y-3">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-semibold text-slate-950">预约记录</h2>
          <Button variant="outline" onClick={() => bookingsQuery.refetch()} disabled={bookingsQuery.isFetching}>
            {bookingsQuery.isFetching && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
            刷新
          </Button>
        </div>

        {bookingsQuery.isLoading ? (
          <div className="flex h-52 items-center justify-center rounded-md border border-slate-200 bg-white">
            <Loader2 className="mr-2 h-5 w-5 animate-spin" />
            正在加载预约记录
          </div>
        ) : bookingsQuery.isError ? (
          <div className="rounded-md border border-red-200 bg-red-50 p-5 text-sm text-red-700">
            预约记录加载失败：{getErrorPayload(bookingsQuery.error).message}
          </div>
        ) : bookings.length === 0 ? (
          <div className="rounded-md border border-slate-200 bg-white p-10 text-center text-sm text-slate-500">
            当前用户暂无预约记录
          </div>
        ) : (
          <div className="space-y-3">
            {bookings.map((booking) => (
              <BookingRow
                key={booking.bookingId}
                booking={booking}
                onCancel={(bookingId) => cancelMutation.mutate(bookingId)}
                canceling={cancelMutation.isPending && cancelingBookingId === booking.bookingId}
              />
            ))}
          </div>
        )}
      </section>
    </div>
  );
};
