import { apiClient } from './client'
import type { ApiResponse } from '../types/api'

export async function getHealth() {
  const response = await apiClient.get<ApiResponse<{ status: string }>>('/health')
  return response.data.data
}
