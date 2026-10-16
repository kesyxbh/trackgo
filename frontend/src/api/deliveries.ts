import type { Anomaly, CreateDeliveryRequest, Delivery, DeliveryStatus, Tracking } from '../types.ts'
import { api } from './client.ts'

export function createDelivery(request: CreateDeliveryRequest) {
  return api<Delivery>('/deliveries', { method: 'POST', body: request })
}

/** Delivery + rider position + remaining distance and ETA: what the tracking page polls. */
export function getTracking(id: number) {
  return api<Tracking>(`/deliveries/${id}/tracking`)
}

export function getAnomaly(id: number) {
  return api<Anomaly>(`/deliveries/${id}/anomaly`)
}

export interface DeliveryFilter {
  customerId?: number
  riderId?: number
  status?: DeliveryStatus
}

export function listDeliveries(filter: DeliveryFilter = {}) {
  const query = new URLSearchParams()
  if (filter.customerId !== undefined) query.set('customerId', String(filter.customerId))
  if (filter.riderId !== undefined) query.set('riderId', String(filter.riderId))
  if (filter.status !== undefined) query.set('status', filter.status)
  const suffix = query.size > 0 ? `?${query.toString()}` : ''
  return api<Delivery[]>(`/deliveries${suffix}`)
}

export function assignRider(deliveryId: number, riderId: number) {
  return api<Delivery>(`/deliveries/${deliveryId}/assign`, { method: 'POST', body: { riderId } })
}

export function autoAssign(deliveryId: number) {
  return api<Delivery>(`/deliveries/${deliveryId}/auto-assign`, { method: 'POST' })
}

export function updateDeliveryStatus(deliveryId: number, status: DeliveryStatus) {
  return api<Delivery>(`/deliveries/${deliveryId}/status`, { method: 'PATCH', body: { status } })
}
