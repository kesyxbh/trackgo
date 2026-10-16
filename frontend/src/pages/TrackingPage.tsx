import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { getAnomaly, getTracking } from '../api/deliveries.ts'
import { ChatWidget } from '../components/ChatWidget.tsx'
import { ErrorNote } from '../components/ErrorNote.tsx'
import { MapView, type MapLine, type MapMarker } from '../components/MapView.tsx'
import { RiderBadge, StatusBadge } from '../components/StatusBadge.tsx'
import { StepProgress } from '../components/StepProgress.tsx'
import { useSelectedCustomer } from '../identity/useSelected.ts'
import { formatCoordinate, formatEta, formatKm, POLL_MS, timeAgo } from '../lib/format.ts'

export function TrackingPage() {
  const params = useParams()
  const deliveryId = Number(params.id)
  const valid = Number.isInteger(deliveryId) && deliveryId > 0
  const { selected: customer } = useSelectedCustomer()

  // Two independent polls: the fast, cheap tracking data and the anomaly check. They are separate
  // queries on purpose: the anomaly note may take a few seconds the first time (a language model
  // writes it) and must never delay the status and map updates.
  const tracking = useQuery({
    queryKey: ['tracking', deliveryId],
    queryFn: () => getTracking(deliveryId),
    enabled: valid,
    refetchInterval: POLL_MS,
  })
  const anomaly = useQuery({
    queryKey: ['anomaly', deliveryId],
    queryFn: () => getAnomaly(deliveryId),
    enabled: valid,
    refetchInterval: POLL_MS,
  })

  if (!valid) return <p className="note note--error">That is not a valid delivery id.</p>
  if (tracking.isLoading) return <p className="muted">Loading…</p>
  if (tracking.error) {
    return (
      <>
        <ErrorNote error={tracking.error} />
        <Link to="/customer">Back to my deliveries</Link>
      </>
    )
  }
  if (!tracking.data) return null

  const { delivery, remainingKm, etaMinutes } = tracking.data
  const rider = delivery.rider

  const markers: MapMarker[] = []
  if (delivery.pickupLat !== null && delivery.pickupLng !== null) {
    markers.push({ id: 'pickup', lat: delivery.pickupLat, lng: delivery.pickupLng, label: `Pickup: ${delivery.pickupAddr}`, kind: 'pickup' })
  }
  if (delivery.dropoffLat !== null && delivery.dropoffLng !== null) {
    markers.push({ id: 'dropoff', lat: delivery.dropoffLat, lng: delivery.dropoffLng, label: `Drop-off: ${delivery.dropoffAddr}`, kind: 'dropoff' })
  }
  if (rider && rider.lat !== null && rider.lng !== null) {
    markers.push({ id: 'rider', lat: rider.lat, lng: rider.lng, label: rider.name, kind: 'rider' })
  }
  const lines: MapLine[] =
    delivery.pickupLat !== null && delivery.pickupLng !== null && delivery.dropoffLat !== null && delivery.dropoffLng !== null
      ? [{ id: 'route', points: [[delivery.pickupLat, delivery.pickupLng], [delivery.dropoffLat, delivery.dropoffLng]] }]
      : []

  return (
    <>
      <div className="page-head">
        <h1>Delivery #{delivery.id}</h1>
        <StatusBadge status={delivery.status} />
      </div>

      {anomaly.data?.detected && (
        <aside className="alert" role="status">
          <strong>Heads up</strong>
          <p>{anomaly.data.message}</p>
          <p className="muted">
            {anomaly.data.aiGenerated ? 'Written by the AI assistant' : 'Automatic notice'} · rider last seen{' '}
            {timeAgo(anomaly.data.lastLocationAt)}
          </p>
        </aside>
      )}

      <StepProgress status={delivery.status} />

      <MapView markers={markers} lines={lines} fitKey={`${delivery.id}:${rider ? 'with-rider' : 'no-rider'}`} />

      <div className="grid">
        <section className="panel">
          <h2>Trip</h2>
          <dl>
            <dt>From</dt>
            <dd>{delivery.pickupAddr}</dd>
            <dt>To</dt>
            <dd>{delivery.dropoffAddr}</dd>
            <dt>Remaining</dt>
            <dd>{formatKm(remainingKm)}</dd>
            <dt>Estimated arrival</dt>
            <dd>{delivery.status === 'DELIVERED' ? 'Delivered' : formatEta(etaMinutes)}</dd>
          </dl>
        </section>

        <section className="panel">
          <h2>Rider</h2>
          {rider === null ? (
            <p className="muted">No rider assigned yet. The system assigns the nearest idle rider automatically.</p>
          ) : (
            <dl>
              <dt>Name</dt>
              <dd>
                {rider.name} <RiderBadge status={rider.status} />
              </dd>
              <dt>Position</dt>
              <dd>
                {formatCoordinate(rider.lat)}, {formatCoordinate(rider.lng)}
              </dd>
              <dt>Last update</dt>
              <dd>{timeAgo(rider.locationUpdatedAt)}</dd>
            </dl>
          )}
        </section>
      </div>

      {customer !== null && <ChatWidget customerId={customer.id} />}
    </>
  )
}
