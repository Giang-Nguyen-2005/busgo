export interface ApiResponse<T> { data: T }

export interface PagedResponse<T> {
  data: T[]
  pagination: { page: number; size: number; totalElements: number; totalPages: number }
}

export interface ApiError {
  code: string
  message: string
  details: unknown
  timestamp: string
}
