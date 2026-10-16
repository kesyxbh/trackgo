import type { DeliveryStatus, RiderStatus } from '../types.ts'

/** How often the UI re-reads the server. The backend has no push channel in this version. */
export const POLL_MS = 3000

/** The four stages of a delivery, in order. Index 0..3 drives the progress indicator. */
export const STEPS: readonly DeliveryStatus[] = ['PENDING', 'ASSIGNED', 'IN_TRANSIT', 'DELIVERED']

export const STATUS_LABEL: Record<DeliveryStatus, string> = {
  PENDING: 'Waiting for a rider',
  ASSIGNED: 'Rider heading to pickup',
  IN_TRANSIT: 'On the way to the drop-off',
  DELIVERED: 'Delivered',
}

export const RIDER_STATUS_LABEL: Record<RiderStatus, string> = {
  IDLE: 'Idle',
  BUSY: 'Busy',
  OFFLINE: 'Offline',
}

export function stepIndex(status: DeliveryStatus): number {
  return STEPS.indexOf(status)
}

/** "just now", "12 s ago", "3 min ago", or "never" when there is no timestamp. */
export function timeAgo(iso: string | null, nowMs: number = Date.now()): string {
  if (iso === null) return 'never'
  const seconds = Math.max(0, Math.round((nowMs - Date.parse(iso)) / 1000))
  if (seconds < 5) return 'just now'
  if (seconds < 60) return `${seconds} s ago`
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes} min ago`
  return `${Math.floor(minutes / 60)} h ago`
}

/** Distances under a kilometre are shown in metres, because "0.1 km" reads badly. */
export function formatKm(km: number | null): string {
  if (km === null) return '—'
  if (km < 1) return `${Math.round(km * 1000)} m`
  return `${km.toFixed(1)} km`
}

export function formatEta(minutes: number | null): string {
  if (minutes === null) return '—'
  if (minutes < 1) return 'under a minute'
  return `${Math.round(minutes)} min`
}

export function formatCoordinate(value: number | null): string {
  return value === null ? '—' : value.toFixed(5)
}
