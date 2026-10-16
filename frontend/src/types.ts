// Shapes of the JSON the TrackGo backend returns. Each interface mirrors one Java record in
// backend/src/main/java/com/trackgo/dto. Keep the two in sync by hand: there is no code generation.

export type DeliveryStatus = 'PENDING' | 'ASSIGNED' | 'IN_TRANSIT' | 'DELIVERED'
export type RiderStatus = 'IDLE' | 'BUSY' | 'OFFLINE'

export interface Customer {
  id: number
  username: string
}

export interface Rider {
  id: number
  name: string
  status: RiderStatus
  /** null until the rider reports a position for the first time. */
  lat: number | null
  lng: number | null
  /** ISO-8601 timestamp of the last position report. */
  locationUpdatedAt: string | null
}

export interface Delivery {
  id: number
  status: DeliveryStatus
  pickupAddr: string
  pickupLat: number | null
  pickupLng: number | null
  dropoffAddr: string
  dropoffLat: number | null
  dropoffLng: number | null
  customer: Customer
  /** null until a rider is assigned. */
  rider: Rider | null
  createdAt: string
  updatedAt: string
}

export interface Tracking {
  delivery: Delivery
  /** null while unknown, for example before a rider is assigned. */
  remainingKm: number | null
  etaMinutes: number | null
  assumedSpeedKmh: number
}

export interface Anomaly {
  detected: boolean
  type: string | null
  staleSeconds: number | null
  lastLocationAt: string | null
  message: string | null
  /** true when the message was written by the language model, false for the fixed fallback text. */
  aiGenerated: boolean
}

export interface SimulatorStatus {
  enabled: boolean
  paused: boolean
  speedKmh: number
  tickMs: number
}

export interface CreateDeliveryRequest {
  customerId: number
  pickupAddr: string
  pickupLat: number
  pickupLng: number
  dropoffAddr: string
  dropoffLat: number
  dropoffLng: number
}

export interface ToolCall {
  name: string
  args: Record<string, unknown>
  ok: boolean
}

export interface AiChatResponse {
  reply: string
  toolCalls: ToolCall[]
}

export interface AiStatus {
  configured: boolean
  model: string
}
