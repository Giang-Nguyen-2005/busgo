import { get } from './client';
import './operatorApi';
import type { PagedResponse } from '../types/api';
import type { CustomerFilters, OperatorCustomer, CustomerDetail } from '../types/operatorCustomers';
export const customersApi = {
  directory: (filters: CustomerFilters, signal?: AbortSignal) => get<PagedResponse<OperatorCustomer>>('/operator/customers', filters, signal),
  detail: (key: string, page: number, size: number, signal?: AbortSignal) => get<CustomerDetail>(`/operator/customers/${encodeURIComponent(key)}`, { page, size }, signal),
};
