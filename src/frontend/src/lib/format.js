const toDate = (value) => {
  if (!value) return null;
  const date = value instanceof Date ? value : new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
};

const pad2 = (value) => String(value).padStart(2, '0');

const isSameDate = (left, right) =>
  left.getFullYear() === right.getFullYear() &&
  left.getMonth() === right.getMonth() &&
  left.getDate() === right.getDate();

export const formatDateTime = (value) => {
  const date = toDate(value);
  if (!date) return '-';
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())} ${pad2(date.getHours())}:${pad2(date.getMinutes())}:${pad2(date.getSeconds())}`;
};

export const formatCount = (value) => {
  if (value === null || value === undefined) return '-';
  return Number(value).toLocaleString('zh-CN');
};

export const formatSlotRange = (startValue, endValue, baseValue = new Date()) => {
  const start = toDate(startValue);
  const end = toDate(endValue);
  const base = toDate(baseValue) || new Date();
  if (!start || !end) return '-';

  const tomorrow = new Date(base);
  tomorrow.setDate(base.getDate() + 1);

  let prefix;
  if (isSameDate(start, base)) {
    prefix = '今天';
  } else if (isSameDate(start, tomorrow)) {
    prefix = '明天';
  } else {
    prefix = `${start.getFullYear()}-${pad2(start.getMonth() + 1)}-${pad2(start.getDate())}`;
  }

  return `${prefix} ${pad2(start.getHours())}:${pad2(start.getMinutes())} - ${pad2(end.getHours())}:${pad2(end.getMinutes())}`;
};

export const isWithinBookingWindow = (slot, nowValue = new Date()) => {
  const now = toDate(nowValue) || new Date();
  const start = toDate(slot?.startTime);
  const end = toDate(slot?.endTime);
  if (!start || !end) return false;

  const nextHour = new Date(now);
  nextHour.setMinutes(0, 0, 0);
  nextHour.setHours(nextHour.getHours() + 1);
  const midnight = new Date(now);
  midnight.setHours(24, 0, 0, 0);
  return start >= nextHour && end <= midnight && isSameDate(start, now);
};

export const isBookableSlot = (slot, nowValue = new Date()) =>
  isWithinBookingWindow(slot, nowValue) &&
  slot?.status === 'AVAILABLE' &&
  Number(slot?.availableCount) > 0;

export const statusLabel = (status) => {
  const labels = {
    AVAILABLE: '可预约',
    RESERVED: '已被预约',
    CANCELLED: '已取消',
    CANCELED: '已取消',
    FINISHED: '已结束',
    SOLD_OUT: '已约满',
    SUCCESS: '已预约',
    OPEN: '开放',
  };
  return labels[status] || status || '-';
};

export const getErrorPayload = (err) => {
  const data = err?.response?.data;
  return {
    code: data?.code || 'REQUEST_FAILED',
    message: data?.message || err?.message || '请求失败',
    reason: data?.reason || data?.message || err?.message || '请稍后重试',
    traceId: data?.traceId,
  };
};
