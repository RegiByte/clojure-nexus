import { createFileRoute } from '@tanstack/react-router'
import { useEffect, useState } from 'react'
import {
  createSSEConnection,
  createMultiEventSSEConnection,
  type SSECounterEvent,
  type SSEPriceUpdateEvent,
  type SSENotificationEvent,
} from '@/lib/realtime'
import { Card } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'

export const Route = createFileRoute('/(auth)/app/sse')({
  component: SSEPlayground,
})

function SSEPlayground() {
  // Counter SSE state
  const [counterConnected, setCounterConnected] = useState(false)
  const [counterEvent, setCounterEvent] = useState<SSECounterEvent | null>(
    null,
  )
  const [counterHistory, setCounterHistory] = useState<SSECounterEvent[]>([])

  // Multi-event SSE state
  const [multiConnected, setMultiConnected] = useState(false)
  const [priceUpdates, setPriceUpdates] = useState<SSEPriceUpdateEvent[]>([])
  const [notifications, setNotifications] = useState<SSENotificationEvent[]>([])

  // Counter SSE connection
  useEffect(() => {
    const eventSource = createSSEConnection({
      onConnected: () => {
        setCounterConnected(true)
      },
      onMessage: (data) => {
        setCounterEvent(data)
        setCounterHistory((prev) => [...prev.slice(-9), data])
      },
      onError: () => {
        setCounterConnected(false)
      },
    })

    return () => {
      eventSource.close()
      setCounterConnected(false)
    }
  }, [])

  // Multi-event SSE connection
  useEffect(() => {
    const eventSource = createMultiEventSSEConnection({
      onConnected: () => {
        setMultiConnected(true)
      },
      onPriceUpdate: (data) => {
        setPriceUpdates((prev) => [...prev.slice(-9), data])
      },
      onNotification: (data) => {
        setNotifications((prev) => [...prev.slice(-9), data])
      },
      onError: () => {
        setMultiConnected(false)
      },
    })

    return () => {
      eventSource.close()
      setMultiConnected(false)
    }
  }, [])

  const formatTimestamp = (timestamp: number) => {
    return new Date(timestamp).toLocaleTimeString()
  }

  return (
    <div className="container mx-auto px-4 py-8">
      <div className="mb-8">
        <h1 className="text-3xl font-bold text-gray-900 mb-2">
          Server-Sent Events Playground
        </h1>
        <p className="text-gray-600">
          Real-time server-to-client communication using SSE
        </p>
      </div>

      <div className="grid gap-6 md:grid-cols-2">
        {/* Counter SSE Demo */}
        <Card className="p-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-xl font-semibold">Counter Events</h2>
            <Badge variant={counterConnected ? 'default' : 'destructive'}>
              {counterConnected ? 'Connected' : 'Disconnected'}
            </Badge>
          </div>

          <div className="space-y-4">
            {counterEvent && (
              <div className="p-4 bg-blue-50 rounded-lg border border-blue-200">
                <div className="text-sm text-gray-600 mb-1">
                  Current Counter
                </div>
                <div className="text-4xl font-bold text-blue-600">
                  {counterEvent.counter}
                </div>
                <div className="text-xs text-gray-500 mt-2">
                  {formatTimestamp(counterEvent.timestamp)}
                </div>
              </div>
            )}

            <div>
              <h3 className="text-sm font-medium text-gray-700 mb-2">
                Recent Events (Last 10)
              </h3>
              <div className="space-y-2 max-h-64 overflow-y-auto">
                {counterHistory.length === 0 ? (
                  <p className="text-sm text-gray-500">
                    Waiting for events...
                  </p>
                ) : (
                  counterHistory
                    .slice()
                    .reverse()
                    .map((event, idx) => (
                      <div
                        key={idx}
                        className="p-2 bg-gray-50 rounded border border-gray-200 text-sm"
                      >
                        <span className="font-mono font-semibold">
                          Counter: {event.counter}
                        </span>
                        <span className="text-gray-500 ml-2">
                          @ {formatTimestamp(event.timestamp)}
                        </span>
                      </div>
                    ))
                )}
              </div>
            </div>
          </div>
        </Card>

        {/* Multi-Event SSE Demo */}
        <Card className="p-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-xl font-semibold">Multi-Event Stream</h2>
            <Badge variant={multiConnected ? 'default' : 'destructive'}>
              {multiConnected ? 'Connected' : 'Disconnected'}
            </Badge>
          </div>

          <div className="space-y-4">
            {/* Price Updates */}
            <div>
              <h3 className="text-sm font-medium text-gray-700 mb-2 flex items-center">
                <span className="w-2 h-2 bg-green-500 rounded-full mr-2"></span>
                Price Updates
              </h3>
              <div className="space-y-2 max-h-32 overflow-y-auto">
                {priceUpdates.length === 0 ? (
                  <p className="text-sm text-gray-500">
                    Waiting for price updates...
                  </p>
                ) : (
                  priceUpdates
                    .slice()
                    .reverse()
                    .map((event, idx) => (
                      <div
                        key={idx}
                        className="p-2 bg-green-50 rounded border border-green-200 text-sm"
                      >
                        <span className="font-mono font-semibold text-green-700">
                          ${event.price}
                        </span>
                        <span className="text-gray-500 ml-2">
                          @ {formatTimestamp(event.timestamp)}
                        </span>
                      </div>
                    ))
                )}
              </div>
            </div>

            {/* Notifications */}
            <div>
              <h3 className="text-sm font-medium text-gray-700 mb-2 flex items-center">
                <span className="w-2 h-2 bg-purple-500 rounded-full mr-2"></span>
                Notifications
              </h3>
              <div className="space-y-2 max-h-32 overflow-y-auto">
                {notifications.length === 0 ? (
                  <p className="text-sm text-gray-500">
                    Waiting for notifications...
                  </p>
                ) : (
                  notifications
                    .slice()
                    .reverse()
                    .map((event, idx) => (
                      <div
                        key={idx}
                        className="p-2 bg-purple-50 rounded border border-purple-200 text-sm"
                      >
                        <span className="text-purple-700">
                          {event.message}
                        </span>
                        <span className="text-gray-500 ml-2">
                          @ {formatTimestamp(event.timestamp)}
                        </span>
                      </div>
                    ))
                )}
              </div>
            </div>
          </div>
        </Card>
      </div>

      {/* Info Section */}
      <Card className="mt-6 p-6 bg-gray-50">
        <h3 className="text-lg font-semibold mb-2">About Server-Sent Events</h3>
        <div className="text-sm text-gray-700 space-y-2">
          <p>
            <strong>Server-Sent Events (SSE)</strong> provide one-way
            communication from server to client over HTTP.
          </p>
          <ul className="list-disc list-inside space-y-1 ml-2">
            <li>Automatic reconnection on connection loss</li>
            <li>Simple text-based protocol</li>
            <li>Works over standard HTTP/HTTPS</li>
            <li>Perfect for real-time updates, notifications, and live feeds</li>
            <li>Lower overhead than WebSockets for one-way communication</li>
          </ul>
        </div>
      </Card>
    </div>
  )
}

