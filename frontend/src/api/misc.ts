import type { AiChatResponse, AiStatus, Customer, Rider, RiderStatus, SimulatorStatus } from '../types.ts'
import { api } from './client.ts'

export function listCustomers() {
  return api<Customer[]>('/customers')
}

export function listRiders(status?: RiderStatus) {
  return api<Rider[]>(status ? `/riders?status=${status}` : '/riders')
}

export function reportRiderLocation(riderId: number, lat: number, lng: number) {
  return api<Rider>(`/riders/${riderId}/location`, { method: 'PATCH', body: { lat, lng } })
}

export function getSimulator() {
  return api<SimulatorStatus>('/simulator')
}

export function pauseSimulator() {
  return api<SimulatorStatus>('/simulator/pause', { method: 'POST' })
}

export function resumeSimulator() {
  return api<SimulatorStatus>('/simulator/resume', { method: 'POST' })
}

export function getAiStatus() {
  return api<AiStatus>('/ai/status')
}

/**
 * Sends one chat message. The customer is identified by the X-Customer-Id header (this app has no
 * login). The server, not the model, decides whose deliveries the assistant may read.
 */
export function sendChatMessage(customerId: number, conversationId: string, message: string) {
  return api<AiChatResponse>('/ai/chat', {
    method: 'POST',
    headers: { 'X-Customer-Id': String(customerId) },
    body: { conversationId, message },
  })
}
