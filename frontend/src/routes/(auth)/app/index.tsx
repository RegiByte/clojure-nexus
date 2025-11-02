import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/(auth)/app/')({
  component: AppHome,
})

function AppHome() {
  return (
    <div className="px-4 py-6 sm:px-0">
      <div className="border-4 border-dashed border-gray-200 rounded-lg p-8">
        <h2 className="text-2xl font-bold text-gray-900 mb-4">
          Welcome to Nexus!
        </h2>
        <p className="text-gray-600">
          You are now authenticated and can access the protected application.
        </p>
        <p className="text-gray-600 mt-2">
          Start building your features here!
        </p>
      </div>
    </div>
  )
}
