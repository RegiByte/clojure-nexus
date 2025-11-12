/**
 * API client utilities for communicating with the Clojure backend
 */

import axios, { type AxiosError, type AxiosRequestConfig } from 'axios'

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:3456'

export interface ApiError {
  error: string
  message?: string
}

export interface User {
  id: string
  email: string
  firstName: string
  lastName: string
  middleName?: string
  createdAt?: string
  updatedAt?: string
}

export interface LoginCredentials {
  email: string
  password: string
}

export interface LoginResponse {
  message: string
  user: User
}

export interface AuthMeResponse {
  user: User
}

export interface RegisterCredentials {
  email: string
  password: string
  firstName: string
  lastName: string
  middleName?: string
}

export interface RegisterResponse {
  message: string
  user: User
}

/**
 * Custom error class for API errors
 */
export class ApiRequestError extends Error {
  constructor(
    message: string,
    public status: number,
    public data?: unknown,
  ) {
    super(message)
    this.name = 'ApiRequestError'
  }
}

/**
 * Axios instance configured for the backend API
 */
const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: true, // Important for cookie-based auth
})

/**
 * Generic API request handler
 */
async function apiRequest<T>(
  endpoint: string,
  options: AxiosRequestConfig = {},
): Promise<T> {
  try {
    const response = await apiClient.request<T>({
      url: endpoint,
      ...options,
    })

    return response.data
  } catch (error) {
    if (axios.isAxiosError(error)) {
      const axiosError = error as AxiosError<ApiError>
      const status = axiosError.response?.status || 0
      const data = axiosError.response?.data

      const errorMessage =
        data?.error ||
        data?.message ||
        axiosError.message ||
        `Request failed with status ${status}`

      throw new ApiRequestError(errorMessage, status, data)
    }

    throw new ApiRequestError(
      error instanceof Error ? error.message : 'Network error',
      0,
    )
  }
}

/**
 * Auth API endpoints
 */
export const authApi = {
  /**
   * Register a new user account
   */
  register: async (
    credentials: RegisterCredentials,
  ): Promise<RegisterResponse> => {
    // Convert camelCase to kebab-case for backend
    const data = {
      email: credentials.email,
      password: credentials.password,
      firstName: credentials.firstName,
      lastName: credentials.lastName,
      ...(credentials.middleName && { middleName: credentials.middleName }),
    }

    return apiRequest<RegisterResponse>('/api/users/register', {
      method: 'POST',
      data,
    })
  },

  /**
   * Login with email and password (cookie-based auth)
   */
  login: async (credentials: LoginCredentials): Promise<LoginResponse> => {
    return apiRequest<LoginResponse>('/auth/login', {
      method: 'POST',
      data: credentials,
    })
  },

  /**
   * Logout (removes auth cookie)
   */
  logout: async (): Promise<{ message: string }> => {
    return apiRequest<{ message: string }>('/auth/logout', {
      method: 'POST',
    })
  },

  /**
   * Get current user info (requires auth cookie)
   */
  me: async (): Promise<AuthMeResponse> => {
    return apiRequest<AuthMeResponse>('/auth/me', {
      method: 'GET',
    })
  },
}
