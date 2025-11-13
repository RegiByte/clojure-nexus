/**
 * Realtime communication utilities for SSE and WebSocket connections
 */

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:3456'

// =============================================================================
// TypeScript Interfaces
// =============================================================================

export interface SSECounterEvent {
  counter: number
  timestamp: number
}

export interface SSEPriceUpdateEvent {
  price: number
  timestamp: number
}

export interface SSENotificationEvent {
  message: string
  timestamp: number
}

export interface SSEConnectedEvent {
  message: string
}

export interface WebSocketMessage {
  type: 'welcome' | 'echo' | 'broadcast' | 'system'
  message: string
}

// =============================================================================
// Server-Sent Events (SSE) Utilities
// =============================================================================

export interface SSEConnectionOptions {
  onMessage?: (data: SSECounterEvent) => void
  onConnected?: (data: SSEConnectedEvent) => void
  onError?: (error: Event) => void
}

/**
 * Creates an SSE connection to the counter events endpoint
 */
export function createSSEConnection(
  options: SSEConnectionOptions,
): EventSource {
  const eventSource = new EventSource(`${API_BASE_URL}/api/realtime/sse/events`)

  eventSource.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data) as SSECounterEvent
      options.onMessage?.(data)
    } catch (error) {
      console.error('Failed to parse SSE message:', error)
    }
  }

  eventSource.addEventListener('connected', (event) => {
    try {
      const data = JSON.parse(event.data) as SSEConnectedEvent
      options.onConnected?.(data)
    } catch (error) {
      console.error('Failed to parse connected event:', error)
    }
  })

  eventSource.onerror = (error) => {
    console.error('SSE Error:', error)
    options.onError?.(error)
  }

  return eventSource
}

export interface SSEMultiEventOptions {
  onPriceUpdate?: (data: SSEPriceUpdateEvent) => void
  onNotification?: (data: SSENotificationEvent) => void
  onConnected?: (data: SSEConnectedEvent) => void
  onError?: (error: Event) => void
}

/**
 * Creates an SSE connection with multiple event types
 */
export function createMultiEventSSEConnection(
  options: SSEMultiEventOptions,
): EventSource {
  const eventSource = new EventSource(
    `${API_BASE_URL}/api/realtime/sse/multi-events`,
  )

  eventSource.addEventListener('price-update', (event) => {
    try {
      const data = JSON.parse(event.data) as SSEPriceUpdateEvent
      options.onPriceUpdate?.(data)
    } catch (error) {
      console.error('Failed to parse price-update event:', error)
    }
  })

  eventSource.addEventListener('notification', (event) => {
    try {
      const data = JSON.parse(event.data) as SSENotificationEvent
      options.onNotification?.(data)
    } catch (error) {
      console.error('Failed to parse notification event:', error)
    }
  })

  eventSource.addEventListener('connected', (event) => {
    try {
      const data = JSON.parse(event.data) as SSEConnectedEvent
      options.onConnected?.(data)
    } catch (error) {
      console.error('Failed to parse connected event:', error)
    }
  })

  eventSource.onerror = (error) => {
    console.error('SSE Error:', error)
    options.onError?.(error)
  }

  return eventSource
}

// =============================================================================
// WebSocket Utilities
// =============================================================================

export interface WebSocketConnectionOptions {
  onMessage?: (data: WebSocketMessage) => void
  onOpen?: () => void
  onClose?: () => void
  onError?: (error: Event) => void
}

/**
 * Creates a WebSocket connection to the echo endpoint
 */
export function createEchoWebSocket(
  options: WebSocketConnectionOptions,
): WebSocket {
  const ws = new WebSocket(
    `${API_BASE_URL.replace('http', 'ws')}/api/realtime/ws/echo`,
  )

  ws.onopen = () => {
    console.log('WebSocket connected')
    options.onOpen?.()
  }

  ws.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data) as WebSocketMessage
      options.onMessage?.(data)
    } catch (error) {
      console.error('Failed to parse WebSocket message:', error)
    }
  }

  ws.onerror = (error) => {
    console.error('WebSocket error:', error)
    options.onError?.(error)
  }

  ws.onclose = () => {
    console.log('WebSocket closed')
    options.onClose?.()
  }

  return ws
}

/**
 * Creates a WebSocket connection to the broadcast endpoint
 */
export function createBroadcastWebSocket(
  options: WebSocketConnectionOptions,
): WebSocket {
  const ws = new WebSocket(
    `${API_BASE_URL.replace('http', 'ws')}/api/realtime/ws/broadcast`,
  )

  ws.onopen = () => {
    console.log('Broadcast WebSocket connected')
    options.onOpen?.()
  }

  ws.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data) as WebSocketMessage
      options.onMessage?.(data)
    } catch (error) {
      console.error('Failed to parse broadcast message:', error)
    }
  }

  ws.onerror = (error) => {
    console.error('Broadcast WebSocket error:', error)
    options.onError?.(error)
  }

  ws.onclose = () => {
    console.log('Broadcast WebSocket closed')
    options.onClose?.()
  }

  return ws
}

/**
 * Sends a message through a WebSocket connection
 */
export function sendWebSocketMessage(ws: WebSocket, message: string): void {
  if (ws.readyState === WebSocket.OPEN) {
    ws.send(message)
  } else {
    console.warn('WebSocket is not open. Current state:', ws.readyState)
  }
}

