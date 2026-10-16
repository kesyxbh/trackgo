import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { FormEvent } from 'react'
import { listDeliveries, updateDeliveryStatus } from '../api/deliveries.ts'
import { reportRiderLocation } from '../api/misc.ts'
import { ErrorNote } from '../components/ErrorNote.tsx'
import { RiderBadge, StatusBadge } from '../components/StatusBadge.tsx'
import { useSelectedRider } from '../identity/useSelected.ts'
import { formatCoordinate, POLL_MS, timeAgo } from '../lib/format.ts'
import type { Rider } from '../types.ts'

/** Manual position report. Uncontrolled inputs: the form data is read once, on submit. */
function PositionForm({ rider }: { rider: Rider }) {
  const queryClient = useQueryClient()
  const report = useMutation({
    mutationFn: (input: { lat: number; lng: number }) => reportRiderLocation(rider.id, input.lat, input.lng),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['riders'] }),
  })

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const lat = Number(data.get('lat'))
    const lng = Number(data.get('lng'))
    if (Number.isFinite(lat) && Number.isFinite(lng)) {
      report.mutate({ lat, lng })
    }
  }

  return (
    <form className="form form--inline" onSubmit={submit}>
      <label>
        Latitude
        <input name="lat" type="number" step="any" min={-90} max={90} defaultValue={rider.lat ?? 39.9087} required />
      </label>
      <label>
        Longitude
        <input name="lng" type="number" step="any" min={-180} max={180} defaultValue={rider.lng ?? 116.3975} required />
      </label>
      <button type="submit" disabled={report.isPending}>
        Report position
      </button>
      <ErrorNote error={report.error} />
    </form>
  )
}

export function RiderConsolePage() {
  const { selected: rider, isLoading, error } = useSelectedRider()
  const queryClient = useQueryClient()

  const deliveries = useQuery({
    queryKey: ['deliveries', { riderId: rider?.id }],
    queryFn: () => listDeliveries({ riderId: rider?.id }),
    enabled: rider !== null,
    refetchInterval: POLL_MS,
  })

  const advance = useMutation({
    mutationFn: (input: { id: number; status: 'IN_TRANSIT' | 'DELIVERED' }) =>
      updateDeliveryStatus(input.id, input.status),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['deliveries'] })
      await queryClient.invalidateQueries({ queryKey: ['riders'] })
    },
  })

  if (isLoading) return <p className="muted">Loading…</p>
  if (error) return <ErrorNote error={error} />
  if (rider === null) return <p className="note">No riders exist yet.</p>

  const active = deliveries.data?.find((delivery) => delivery.status !== 'DELIVERED')
  const finished = deliveries.data?.filter((delivery) => delivery.status === 'DELIVERED') ?? []

  return (
    <>
      <div className="page-head">
        <h1>{rider.name}</h1>
        <RiderBadge status={rider.status} />
      </div>

      <p className="muted">
        Position {formatCoordinate(rider.lat)}, {formatCoordinate(rider.lng)} · reported {timeAgo(rider.locationUpdatedAt)}.
        The background simulator also moves riders on its own; the controls below let you act by hand.
      </p>

      <section className="panel">
        <h2>Current delivery</h2>
        <ErrorNote error={deliveries.error} />
        {active === undefined ? (
          <p className="muted">No active delivery. You are waiting for an assignment.</p>
        ) : (
          <>
            <div className="card__row">
              <strong>Delivery #{active.id}</strong>
              <StatusBadge status={active.status} />
            </div>
            <p>
              {active.pickupAddr} <span aria-hidden="true">→</span> {active.dropoffAddr}
            </p>
            <p className="muted">Customer: {active.customer.username}</p>
            <div className="actions">
              <button
                type="button"
                disabled={active.status !== 'ASSIGNED' || advance.isPending}
                onClick={() => advance.mutate({ id: active.id, status: 'IN_TRANSIT' })}
              >
                Picked up
              </button>
              <button
                type="button"
                disabled={active.status !== 'IN_TRANSIT' || advance.isPending}
                onClick={() => advance.mutate({ id: active.id, status: 'DELIVERED' })}
              >
                Delivered
              </button>
            </div>
            <ErrorNote error={advance.error} />
          </>
        )}
      </section>

      <section className="panel">
        <h2>Report my position</h2>
        {/* key: switching rider resets the form to that rider's coordinates. */}
        <PositionForm key={rider.id} rider={rider} />
      </section>

      {finished.length > 0 && (
        <section className="panel">
          <h2>Completed</h2>
          <ul className="plain">
            {finished.map((delivery) => (
              <li key={delivery.id}>
                #{delivery.id}: {delivery.pickupAddr} → {delivery.dropoffAddr}
              </li>
            ))}
          </ul>
        </section>
      )}
    </>
  )
}
