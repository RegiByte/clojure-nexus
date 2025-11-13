import { createFileRoute, Link } from '@tanstack/react-router'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'

export const Route = createFileRoute('/(auth)/app/')({
  component: AppHome,
})

function AppHome() {
  return (
    <div className="container mx-auto px-4 py-8">
      <div className="mb-8">
        <h1 className="text-3xl font-bold text-gray-900 mb-2">
          Welcome to Nexus!
        </h1>
        <p className="text-gray-600">
          You are now authenticated and can access the protected application.
        </p>
      </div>

      <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
        {/* SSE Playground Card */}
        <Card className="p-6 hover:shadow-lg transition-shadow">
          <div className="mb-4">
            <div className="w-12 h-12 bg-blue-100 rounded-lg flex items-center justify-center mb-3">
              <svg
                className="w-6 h-6 text-blue-600"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M13 10V3L4 14h7v7l9-11h-7z"
                />
              </svg>
            </div>
            <h3 className="text-xl font-semibold text-gray-900 mb-2">
              Server-Sent Events
            </h3>
            <p className="text-gray-600 text-sm mb-4">
              Explore real-time server-to-client communication with SSE. See
              live counter updates and multi-event streams in action.
            </p>
          </div>
          <Link to="/app/sse">
            <Button className="w-full">Open SSE Playground</Button>
          </Link>
        </Card>

        {/* WebSocket Playground Card */}
        <Card className="p-6 hover:shadow-lg transition-shadow">
          <div className="mb-4">
            <div className="w-12 h-12 bg-green-100 rounded-lg flex items-center justify-center mb-3">
              <svg
                className="w-6 h-6 text-green-600"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z"
                />
              </svg>
            </div>
            <h3 className="text-xl font-semibold text-gray-900 mb-2">
              WebSockets
            </h3>
            <p className="text-gray-600 text-sm mb-4">
              Test bidirectional real-time communication. Try echo messaging and
              broadcast to multiple clients simultaneously.
            </p>
          </div>
          <Link to="/app/websocket">
            <Button className="w-full">Open WebSocket Playground</Button>
          </Link>
        </Card>

        {/* Info Card */}
        <Card className="p-6 bg-gradient-to-br from-purple-50 to-pink-50 border-purple-200">
          <div className="mb-4">
            <div className="w-12 h-12 bg-purple-100 rounded-lg flex items-center justify-center mb-3">
              <svg
                className="w-6 h-6 text-purple-600"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
                />
              </svg>
            </div>
            <h3 className="text-xl font-semibold text-gray-900 mb-2">
              About Realtime Features
            </h3>
            <p className="text-gray-600 text-sm">
              These playgrounds demonstrate Aleph's powerful real-time
              capabilities. Perfect for building chat apps, live dashboards,
              notifications, and collaborative tools.
            </p>
          </div>
        </Card>
      </div>

      {/* Additional Info Section */}
      <Card className="mt-8 p-6 bg-gray-50">
        <h3 className="text-lg font-semibold text-gray-900 mb-3">
          Getting Started
        </h3>
        <div className="grid gap-4 md:grid-cols-2">
          <div>
            <h4 className="font-medium text-gray-900 mb-2">
              🔄 Server-Sent Events (SSE)
            </h4>
            <ul className="text-sm text-gray-600 space-y-1">
              <li>• One-way server to client communication</li>
              <li>• Automatic reconnection</li>
              <li>• Perfect for live feeds and notifications</li>
              <li>• Works over standard HTTP</li>
            </ul>
          </div>
          <div>
            <h4 className="font-medium text-gray-900 mb-2">
              💬 WebSockets
            </h4>
            <ul className="text-sm text-gray-600 space-y-1">
              <li>• Two-way bidirectional communication</li>
              <li>• Low latency persistent connection</li>
              <li>• Ideal for chat and real-time collaboration</li>
              <li>• Supports broadcasting to multiple clients</li>
            </ul>
          </div>
        </div>
      </Card>
    </div>
  )
}
