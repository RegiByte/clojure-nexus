import { createFileRoute, redirect } from '@tanstack/react-router'
import logo from '../logo.svg'
import { authenticatedUserQueryOptions } from '@/lib/auth'

export const Route = createFileRoute('/')({
  beforeLoad: async ({ context }) => {
    const { queryClient } = context

    const auth = await queryClient.ensureQueryData(
      authenticatedUserQueryOptions(),
    )
    if (!auth?.id) {
      throw redirect({
        to: '/login',
        search: {
          redirect: '/',
        },
      })
    }
  },
  component: App,
})

function App() {
  return (
    <div className="text-center">
      <header className="min-h-screen flex flex-col items-center justify-center bg-[#282c34] text-white text-[calc(10px+2vmin)]">
        <img
          src={logo}
          className="h-[40vmin] pointer-events-none animate-[spin_20s_linear_infinite]"
          alt="logo"
        />
        <div className="space-y-4">
          <h1 className="text-4xl font-bold">Welcome to Nexus</h1>
          <p>
            A modern full-stack application with Clojure backend and React
            frontend
          </p>
          <div className="mt-8">
            <a
              href="/login"
              className="inline-block px-6 py-3 bg-indigo-600 text-white font-medium rounded-md hover:bg-indigo-700 transition-colors"
            >
              Sign In
            </a>
          </div>
        </div>
      </header>
    </div>
  )
}
