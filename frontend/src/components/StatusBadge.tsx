import { RIDER_STATUS_LABEL, STATUS_LABEL } from '../lib/format.ts'
import type { DeliveryStatus, RiderStatus } from '../types.ts'

export function StatusBadge({ status }: { status: DeliveryStatus }) {
  return <span className={`badge badge--${status.toLowerCase()}`}>{STATUS_LABEL[status]}</span>
}

export function RiderBadge({ status }: { status: RiderStatus }) {
  return <span className={`badge badge--rider-${status.toLowerCase()}`}>{RIDER_STATUS_LABEL[status]}</span>
}
