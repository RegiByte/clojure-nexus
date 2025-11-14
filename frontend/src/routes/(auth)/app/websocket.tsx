import { createFileRoute } from '@tanstack/react-router'
import { useEffect, useState, useRef } from 'react'
import {
  createEchoWebSocket,
  createBroadcastWebSocket,
  sendWebSocketMessage,
  type WebSocketMessage,
} from '@/lib/realtime'
import { Card } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'

export const Route = createFileRoute('/(auth)/app/websocket')({
  component: WebSocketPlayground,
})

interface MessageWithTimestamp extends WebSocketMessage {
  timestamp: number
}

function WebSocketPlayground() {
  // Echo WebSocket state
  const [echoConnected, setEchoConnected] = useState(false)
  const [echoMessages, setEchoMessages] = useState<MessageWithTimestamp[]>([])
  const [echoInput, setEchoInput] = useState('')
  const echoWsRef = useRef<WebSocket | null>(null)

  // Broadcast WebSocket state
  const [broadcastConnected, setBroadcastConnected] = useState(false)
  const [broadcastMessages, setBroadcastMessages] = useState<
    MessageWithTimestamp[]
  >([])
  const [broadcastInput, setBroadcastInput] = useState('')
  const broadcastWsRef = useRef<WebSocket | null>(null)

  // Echo WebSocket connection
  useEffect(() => {
    const ws = createEchoWebSocket({
      onOpen: () => {
        setEchoConnected(true)
      },
      onMessage: (data) => {
        setEchoMessages((prev) => [
          ...prev,
          { ...data, timestamp: Date.now() },
        ])
      },
      onClose: () => {
        setEchoConnected(false)
      },
      onError: () => {
        setEchoConnected(false)
      },
    })

    echoWsRef.current = ws

    return () => {
      ws.close()
      setEchoConnected(false)
    }
  }, [])

  // Broadcast WebSocket connection
  useEffect(() => {
    const ws = createBroadcastWebSocket({
      onOpen: () => {
        setBroadcastConnected(true)
      },
      onMessage: (data) => {
        setBroadcastMessages((prev) => [
          ...prev,
          { ...data, timestamp: Date.now() },
        ])
      },
      onClose: () => {
        setBroadcastConnected(false)
      },
      onError: () => {
        setBroadcastConnected(false)
      },
    })

    broadcastWsRef.current = ws

    return () => {
      ws.close()
      setBroadcastConnected(false)
    }
  }, [])

  const handleEchoSend = () => {
    if (echoWsRef.current && echoInput.trim()) {
      sendWebSocketMessage(echoWsRef.current, echoInput)
      setEchoInput('')
    }
  }

  const handleBroadcastSend = () => {
    if (broadcastWsRef.current && broadcastInput.trim()) {
      sendWebSocketMessage(broadcastWsRef.current, broadcastInput)
      setBroadcastInput('')
    }
  }

  const handleKeyPress = (
    e: React.KeyboardEvent,
    handler: () => void,
  ) => {
    if (e.key === 'Enter') {
      handler()
    }
  }

  const formatTimestamp = (timestamp: number) => {
    return new Date(timestamp).toLocaleTimeString()
  }

  const getMessageBadgeVariant = (type: string) => {
    switch (type) {
      case 'welcome':
        return 'default'
      case 'echo':
        return 'secondary'
      case 'broadcast':
        return 'default'
      case 'system':
        return 'outline'
      default:
        return 'outline'
    }
  }

  return (
    <div className="container mx-auto px-4 py-8">
      <div className="mb-8">
        <h1 className="text-3xl font-bold text-gray-900 mb-2">
          WebSocket Playground
        </h1>
        <p className="text-gray-600">
          Real-time bidirectional communication using WebSockets
        </p>
      </div>

      <div className="grid gap-6 md:grid-cols-2">
        {/* Echo WebSocket Demo */}
        <Card className="p-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-xl font-semibold">Echo WebSocket</h2>
            <Badge variant={echoConnected ? 'default' : 'destructive'}>
              {echoConnected ? 'Connected' : 'Disconnected'}
            </Badge>
          </div>

          <div className="space-y-4">
            {/* Message Input */}
            <div className="flex gap-2">
              <Input
                type="text"
                placeholder="Type a message..."
                value={echoInput}
                onChange={(e) => setEchoInput(e.target.value)}
                onKeyPress={(e) => handleKeyPress(e, handleEchoSend)}
                disabled={!echoConnected}
                className="flex-1"
              />
              <Button
                onClick={handleEchoSend}
                disabled={!echoConnected || !echoInput.trim()}
              >
                Send
              </Button>
            </div>

            {/* Messages Display */}
            <div>
              <h3 className="text-sm font-medium text-gray-700 mb-2">
                Messages
              </h3>
              <div className="space-y-2">
                {echoMessages.length === 0 ? (
                  <p className="text-sm text-gray-500">
                    No messages yet. Send a message to get started!
                  </p>
                ) : (
                  echoMessages.map((msg, idx) => (
                    <div
                      key={idx}
                      className="p-3 bg-gray-50 rounded-lg border border-gray-200"
                    >
                      <div className="flex items-center justify-between mb-1">
                        <Badge variant={getMessageBadgeVariant(msg.type)}>
                          {msg.type}
                        </Badge>
                        <span className="text-xs text-gray-500">
                          {formatTimestamp(msg.timestamp)}
                        </span>
                      </div>
                      <p className="text-sm text-gray-700 break-words">
                        {msg.message}
                      </p>
                    </div>
                  ))
                )}
              </div>
            </div>
          </div>
        </Card>

        {/* Broadcast WebSocket Demo */}
        <Card className="p-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-xl font-semibold">Broadcast WebSocket</h2>
            <Badge variant={broadcastConnected ? 'default' : 'destructive'}>
              {broadcastConnected ? 'Connected' : 'Disconnected'}
            </Badge>
          </div>

          <div className="space-y-4">
            {/* Message Input */}
            <div className="flex gap-2">
              <Input
                type="text"
                placeholder="Broadcast a message..."
                value={broadcastInput}
                onChange={(e) => setBroadcastInput(e.target.value)}
                onKeyPress={(e) => handleKeyPress(e, handleBroadcastSend)}
                disabled={!broadcastConnected}
                className="flex-1"
              />
              <Button
                onClick={handleBroadcastSend}
                disabled={!broadcastConnected || !broadcastInput.trim()}
              >
                Send
              </Button>
            </div>

            {/* Messages Display */}
            <div>
              <h3 className="text-sm font-medium text-gray-700 mb-2">
                Broadcast Messages
              </h3>
              <div className="space-y-2 max-h-96 overflow-y-auto">
                {broadcastMessages.length === 0 ? (
                  <p className="text-sm text-gray-500">
                    No messages yet. Open multiple tabs to test broadcasting!
                  </p>
                ) : (
                  broadcastMessages.map((msg, idx) => (
                    <div
                      key={idx}
                      className={`p-3 rounded-lg border ${
                        msg.type === 'system'
                          ? 'bg-blue-50 border-blue-200'
                          : 'bg-gray-50 border-gray-200'
                      }`}
                    >
                      <div className="flex items-center justify-between mb-1">
                        <Badge variant={getMessageBadgeVariant(msg.type)}>
                          {msg.type}
                        </Badge>
                        <span className="text-xs text-gray-500">
                          {formatTimestamp(msg.timestamp)}
                        </span>
                      </div>
                      <p className="text-sm text-gray-700 break-words">
                        {msg.message}
                      </p>
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
        <h3 className="text-lg font-semibold mb-2">About WebSockets</h3>
        <div className="text-sm text-gray-700 space-y-2">
          <p>
            <strong>WebSockets</strong> provide full-duplex bidirectional
            communication between client and server.
          </p>
          <ul className="list-disc list-inside space-y-1 ml-2">
            <li>Real-time two-way communication</li>
            <li>Lower latency than HTTP polling</li>
            <li>Persistent connection</li>
            <li>Perfect for chat, gaming, and collaborative applications</li>
            <li>
              <strong>Echo:</strong> Server echoes back any message you send
            </li>
            <li>
              <strong>Broadcast:</strong> Messages are sent to all connected
              clients (try opening multiple tabs!)
            </li>
          </ul>
        </div>
      </Card>
    </div>
  )
}

