import { api } from '@/lib/api';

export const login = (username, password) => api.post('/auth/login', { username, password });
export const register = (username, password) => api.post('/auth/register', { username, password });
export const logout = () => api.post('/auth/logout');
export const listAdminMachines = () => api.get('/admin/machines');
export const updateMachineStatus = (id, status) => api.patch(`/admin/machines/${id}/status`, { status });

export const getCurrentUser = () => api.get('/users/me');

export const listSlots = () => api.get('/slots');

export const queryInventory = (slotId) => api.get(`/slots/${slotId}`);

export const warmupSlot = (slotId) => api.post(`/slots/${slotId}/warmup`);

export const createBooking = (slotIdOrUserId, maybeDurationHours) => {
  return api.post('/bookings', {
    slotId: Number(slotIdOrUserId),
    durationHours: Number(maybeDurationHours ?? 1),
  });
};

export const listMyBookings = () => api.get('/bookings/my');

export const listUserBookings = (userId) => api.get(`/bookings/users/${userId}`);

export const cancelBooking = (bookingId) => api.post(`/bookings/${bookingId}/cancel`);

export const resetData = (slotId) => api.post(`/dev/reset/${slotId}`);

export const verifyConsistency = (slotId) => api.get(`/dev/verify/${slotId}`);
