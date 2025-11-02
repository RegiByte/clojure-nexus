import { ensureAuthenticated } from '@/lib/auth'
import { createFileRoute, Outlet } from '@tanstack/react-router'

export const Route = createFileRoute('/(auth)')({
  component: RouteComponent,
  beforeLoad: async ({ context }) => {
    const { queryClient } = context
    await ensureAuthenticated(queryClient, '/app')
  },
})

function RouteComponent() {
  return <Outlet />
}
