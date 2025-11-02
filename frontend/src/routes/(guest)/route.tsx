import { ensureGuest } from '@/lib/auth'
import { createFileRoute, Outlet } from '@tanstack/react-router'

export const Route = createFileRoute('/(guest)')({
  component: RouteComponent,
  beforeLoad: async ({ context }) => {
    const { queryClient } = context
    await ensureGuest(queryClient)
  },
})

function RouteComponent() {
  return <Outlet />
}
