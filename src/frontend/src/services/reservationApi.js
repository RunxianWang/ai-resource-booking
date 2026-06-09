import { api } from '@/lib/api';

export const getCurrentUser = () => api.get('/users/me');

export const listSlots = () => api.get('/slots');

export const queryInventory = (slotId) => api.get(`/slots/${slotId}`);

export const warmupSlot = (slotId) => api.post(`/slots/${slotId}/warmup`);

export const createBooking = (slotIdOrUserId, maybeSlotId) => {
  const slotId = maybeSlotId ?? slotIdOrUserId;
  return api.post('/bookings', { slotId: Number(slotId) });
};

export const listMyBookings = () => api.get('/bookings/my');

export const listUserBookings = (userId) => api.get(`/bookings/users/${userId}`);

export const cancelBooking = (bookingId) => api.post(`/bookings/${bookingId}/cancel`);

export const resetData = (slotId) => api.post(`/dev/reset/${slotId}`);

export const verifyConsistency = (slotId) => api.get(`/dev/verify/${slotId}`);
