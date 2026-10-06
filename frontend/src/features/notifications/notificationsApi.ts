import { apiClient, get, post } from '../../api/client';
import type { PagedResponse } from '../../types/api';
export interface Notification {
  id: number; type: string; title: string; message: string; bookingCode: string;
  navigationTarget: string | null; createdAt: string; readAt: string | null;
}
export interface Preferences { bookingPaymentEmail: boolean; bookingChangeEmail: boolean; tripReminderEmail: boolean }
export interface Delivery { id: number; status: string; attemptCount: number; errorSummary: string | null }
export { notificationTarget } from './notificationModel';
export const notificationsApi = (operator = false) => {
  const path = operator ? '/operator/notifications' : '/notifications';
  return {
    list: async (page: number, size: number, signal?: AbortSignal) =>
      (await apiClient.get<PagedResponse<Notification>>(path, { params: { page, size }, signal })).data,
    count: (signal?: AbortSignal) => get<number>(`${path}/unread-count`, undefined, signal),
    read: (id: number) => apiClient.patch(`${path}/${id}/read`),
    all: () => post<boolean>(`${path}/read-all`),
    deliveries: (id: number, signal?: AbortSignal) => get<Delivery[]>(`${path}/${id}/deliveries`, undefined, signal),
    retry: (id: number) => post<boolean>(`${path}/${id}/retry`),
  };
};
