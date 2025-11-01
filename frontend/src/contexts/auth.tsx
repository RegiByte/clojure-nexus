import {
  useQuery,
  useMutation,
  useQueryClient,
  queryOptions,
} from '@tanstack/react-query'
import { authApi, type User, type LoginCredentials } from '@/lib/api'

interface AuthContextType {
  user: User | null
  isLoading: boolean
  isAuthenticated: boolean
  login: (credentials: LoginCredentials) => Promise<void>
  logout: () => Promise<void>
  refetchUser: () => Promise<void>
  isLoginPending: boolean
  isLogoutPending: boolean
}

// Query key for the current user
export const AUTH_USER_QUERY_KEY = ['auth', 'user'] as const

export const authenticatedUserQueryOptions = () =>
  queryOptions({
    queryKey: AUTH_USER_QUERY_KEY,
    queryFn: async () => {
      try {
        const response = await authApi.me()
        return response.user
      } catch (error) {
        // User is not authenticated
        return null
      }
    },
    retry: false, // Don't retry on auth failures
    staleTime: 5 * 60 * 1000, // Consider data fresh for 5 minutes
  })

export function useAuth(): AuthContextType {
  const queryClient = useQueryClient()

  // Query for fetching the current user
  const {
    data: userData,
    isLoading,
    refetch,
  } = useQuery(authenticatedUserQueryOptions())

  // Mutation for login
  const loginMutation = useMutation({
    mutationFn: async (credentials: LoginCredentials) => {
      const response = await authApi.login(credentials)
      return response.user
    },
    onSuccess: (user) => {
      // Update the user query cache with the logged-in user
      queryClient.setQueryData(AUTH_USER_QUERY_KEY, user)
    },
  })

  // Mutation for logout
  const logoutMutation = useMutation({
    mutationFn: async () => {
      await authApi.logout()
    },
    onSuccess: () => {
      // Clear the user query cache
      queryClient.setQueryData(AUTH_USER_QUERY_KEY, null)
    },
  })

  const login = async (credentials: LoginCredentials) => {
    await loginMutation.mutateAsync(credentials)
  }

  const logout = async () => {
    await logoutMutation.mutateAsync()
  }

  const refetchUser = async () => {
    await refetch()
  }

  return {
    user: userData ?? null,
    isLoading,
    isAuthenticated: !!userData,
    login,
    logout,
    refetchUser,
    isLoginPending: loginMutation.isPending,
    isLogoutPending: logoutMutation.isPending,
  }
}
