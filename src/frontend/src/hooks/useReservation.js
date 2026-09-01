import { useState, useCallback, useEffect, useRef } from 'react';
import { toast } from 'sonner';
import {
  queryInventory,
  warmupSlot,
  createBooking,
  resetData,
  verifyConsistency,
} from '@/services/reservationApi';

export const useReservation = () => {
  const [slotId, setSlotId] = useState('1');
  const [userId, setUserId] = useState('3001');
  const [inventory, setInventory] = useState(null);
  const [businessResult, setBusinessResult] = useState(null);
  const [debugResult, setDebugResult] = useState(null);
  const [consistency, setConsistency] = useState(null);
  const [loading, setLoading] = useState(false);
  const loadedRef = useRef(false);

  const errorMessage = (err) =>
    err.response?.data?.message || err.response?.data?.reason || err.message || '请求失败';

  const normalizeError = useCallback((err) => ({
    code: err.response?.data?.code || 'SYSTEM_ERROR',
    message: errorMessage(err),
    reason: err.response?.data?.reason || err.message,
    traceId: err.response?.data?.traceId,
    raw: err.response?.data || null,
  }), []);

  const makeBookingResult = useCallback((data, fallbackUserId, fallbackSlotId) => {
    const code = data?.code || 'SYSTEM_ERROR';
    const descriptions = {
      SUCCESS: `用户 ${data?.userId ?? fallbackUserId} 已成功预约资源时段 ${data?.slotId ?? fallbackSlotId}`,
      DUPLICATE_BOOKING: `用户 ${data?.userId ?? fallbackUserId} 已预约过该资源时段，请更换用户或资源`,
      SOLD_OUT: '当前资源剩余名额不足，请选择其他时段或稍后重试',
      NOT_WARMED_UP: 'Redis 库存尚未预热，请先在开发者工具中执行预热',
      REDIS_ERROR: 'Redis 执行异常，请检查缓存服务状态',
      INVALID_REQUEST: data?.reason || data?.message || '请求参数不正确',
      RESOURCE_NOT_FOUND: '资源时段不存在，请检查 slotId',
      INTERNAL_ERROR: '后端系统异常，请稍后重试',
      SYSTEM_ERROR: data?.message || '请求未完成，请检查网络或服务状态',
    };
    const titles = {
      SUCCESS: '预约成功',
      DUPLICATE_BOOKING: '重复预约',
      SOLD_OUT: '库存不足',
      NOT_WARMED_UP: '系统错误',
      REDIS_ERROR: '系统错误',
      INVALID_REQUEST: '系统错误',
      RESOURCE_NOT_FOUND: '系统错误',
      INTERNAL_ERROR: '系统错误',
      SYSTEM_ERROR: '系统错误',
    };

    return {
      code,
      title: titles[code] || '系统错误',
      description: descriptions[code] || data?.message || data?.reason || '预约请求处理失败',
      traceId: data?.traceId,
      bookingId: data?.bookingId,
      isSuccess: code === 'SUCCESS',
    };
  }, []);

  const refreshDashboard = useCallback(async (targetSlotId = slotId) => {
    setLoading(true);
    try {
      const [inventoryData, consistencyData] = await Promise.all([
        queryInventory(targetSlotId),
        verifyConsistency(targetSlotId),
      ]);
      setInventory(inventoryData.data);
      setConsistency(consistencyData.data);
      setDebugResult({
        action: '页面初始化',
        code: 'SUCCESS',
        message: '已加载库存和一致性数据',
        data: {
          inventory: inventoryData.data,
          consistency: consistencyData.data,
        },
      });
    } catch (err) {
      const normalized = normalizeError(err);
      setDebugResult({ action: '页面初始化', ...normalized });
      toast.error(`页面数据加载失败：${normalized.message}`);
    } finally {
      setLoading(false);
    }
  }, [slotId, normalizeError]);

  const handleQuery = useCallback(async (targetSlotId = slotId, options = {}) => {
    if (options.showLoading !== false) {
      setLoading(true);
    }
    try {
      const res = await queryInventory(targetSlotId);
      setInventory(res.data);
      setDebugResult({
        action: '查询库存',
        code: 'SUCCESS',
        message: '库存查询成功',
        data: res.data,
      });
      return res.data;
    } catch (err) {
      const normalized = normalizeError(err);
      setDebugResult({ action: '查询库存', ...normalized });
      toast.error(`库存查询失败：${normalized.message}`);
      return null;
    } finally {
      if (options.showLoading !== false) {
        setLoading(false);
      }
    }
  }, [slotId, normalizeError]);

  const handleVerify = useCallback(async (targetSlotId = slotId, options = {}) => {
    if (options.showLoading !== false) {
      setLoading(true);
    }
    try {
      const res = await verifyConsistency(targetSlotId);
      setConsistency(res.data);
      setDebugResult({
        action: '一致性校验',
        code: res.data.stockConsistent && res.data.messageConsistent ? 'SUCCESS' : 'INCONSISTENT',
        message: '一致性校验完成',
        data: res.data,
      });
      return res.data;
    } catch (err) {
      const normalized = normalizeError(err);
      setDebugResult({ action: '一致性校验', ...normalized });
      toast.error(`一致性校验失败：${normalized.message}`);
      return null;
    } finally {
      if (options.showLoading !== false) {
        setLoading(false);
      }
    }
  }, [slotId, normalizeError]);

  const handleWarmup = useCallback(async () => {
    setLoading(true);
    try {
      const res = await warmupSlot(slotId);
      setDebugResult({ action: 'Redis 预热', ...res.data });
      if (res.data.code === 'SUCCESS') {
        toast.success('Redis 预热成功');
        await refreshDashboard(slotId);
      } else {
        toast.error(res.data.message || 'Redis 预热失败');
      }
    } catch (err) {
      const normalized = normalizeError(err);
      setDebugResult({ action: 'Redis 预热', ...normalized });
      toast.error(`Redis 预热失败：${normalized.message}`);
    } finally {
      setLoading(false);
    }
  }, [slotId, refreshDashboard, normalizeError]);

  const handleBook = useCallback(async (targetUserId = userId, targetSlotId = slotId) => {
    setLoading(true);
    try {
      const res = await createBooking(targetSlotId, 1);
      setDebugResult({ action: '提交预约', ...res.data });
      const result = makeBookingResult(res.data, targetUserId, targetSlotId);
      setBusinessResult(result);
      if (res.data.code === 'SUCCESS') {
        toast.success('预约成功');
      } else {
        toast.error(result.title);
      }
      await refreshDashboard(targetSlotId);
    } catch (err) {
      const normalized = normalizeError(err);
      setDebugResult({ action: '提交预约', ...normalized });
      const result = makeBookingResult(normalized, targetUserId, targetSlotId);
      setBusinessResult(result);
      toast.error(`预约请求失败：${normalized.message}`);
    } finally {
      setLoading(false);
    }
  }, [userId, slotId, refreshDashboard, normalizeError, makeBookingResult]);

  const handleReset = useCallback(async () => {
    setLoading(true);
    let success = false;
    try {
      const res = await resetData(slotId);
      setDebugResult({ action: '重置数据', ...res.data });
      if (res.data.code === 'SUCCESS') {
        toast.success('重置成功，正在刷新数据');
        success = true;
      } else {
        toast.error(res.data.message || '重置失败');
      }
    } catch (err) {
      const normalized = normalizeError(err);
      setDebugResult({ action: '重置数据', ...normalized });
      toast.error(`重置失败：${normalized.message}`);
    } finally {
      setLoading(false);
    }

    if (success) {
      await refreshDashboard(slotId);
    }
  }, [slotId, refreshDashboard, normalizeError]);

  useEffect(() => {
    if (loadedRef.current) return;
    loadedRef.current = true;
    refreshDashboard('1');
  }, [refreshDashboard]);

  return {
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
  };
};
