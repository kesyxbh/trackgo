import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { assignRider, autoAssign, listDeliveries } from '../api/deliveries.ts'
import { getSimulator, listRiders, pauseSimulator, resumeSimulator } from '../api/misc.ts'
import { ErrorNote } from '../components/ErrorNote.tsx'
import { MapView, type MapMarker } from '../components/MapView.tsx'
import { RiderBadge, StatusBadge } from '../components/StatusBadge.tsx'
import { formatCoordinate, POLL_MS, timeAgo } from '../lib/format.ts'
import type { MarkerKind } from '../components/MapView.tsx'
import type { RiderStatus } from '../types.ts'

const RIDER_MARKER: Record<RiderStatus, MarkerKind> = {
  IDLE: 'rider-idle',
  BUSY: 'rider-busy',
  OFFLINE: 'rider-offline',
}

export function DispatchPage() {
  const queryClient = useQueryClient()
  // deliveryId -> rider the dispatcher picked in that row's dropdown
  const [choice, setChoice] = useState<Record<number, number>>({})

  const deliveries = useQuery({ queryKey: ['deliveries', 'all'], queryFn: () => listDeliveries(), refetchInterval: POLL_MS })
  const riders = useQuery({ queryKey: ['riders'], queryFn: () => listRiders(), refetchInterval: POLL_MS })
  const simulator = useQuery({ queryKey: ['simulator'], queryFn: getSimulator, refetchInterval: POLL_MS })

  const refresh = async () => {
    await queryClient.invalidateQueries({ queryKey: ['deliveries'] })
    await queryClient.invalidateQueries({ queryKey: ['riders'] })
  }

  const assign = useMutation({
    mutationFn: (input: { deliveryId: number; riderId: number }) => assignRider(input.deliveryId, input.riderId),
    onSuccess: refresh,
  })
  const auto = useMutation({ mutationFn: autoAssign, onSuccess: refresh })
  const toggleSimulator = useMutation({
    mutationFn: (pause: boolean) => (pause ? pauseSimulator() : resumeSimulator()),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['simulator'] }),
  })

  const all = deliveries.data ?? []
  const pending = all.filter((delivery) => delivery.status === 'PENDING')
  const active = all.filter((delivery) => delivery.status === 'ASSIGNED' || delivery.status === 'IN_TRANSIT')
  const idleRiders = (riders.data ?? []).filter((rider) => rider.status === 'IDLE')

  const markers: MapMarker[] = []
  for (const rider of riders.data ?? []) {
    if (rider.lat !== null && rider.lng !== null) {
      markers.push({ id: `rider-${rider.id}`, lat: rider.lat, lng: rider.lng, label: rider.name, kind: RIDER_MARKER[rider.status] })
    }
  }
  for (const delivery of pending) {
    if (delivery.pickupLat !== null && delivery.pickupLng !== null) {
      markers.push({ id: `pickup-${delivery.id}`, lat: delivery.pickupLat, lng: delivery.pickupLng, label: `#${delivery.id} pickup`, kind: 'pickup' })
    }
  }

  const error = assign.error ?? auto.error ?? deliveries.error ?? riders.error

  return (
    <>
      <div className="page-head">
        <h1>Dispatch</h1>
        {simulator.data && (
          <div className="actions">
            <span className="muted">
              Simulator: {simulator.data.enabled ? (simulator.data.paused ? 'paused' : 'running') : 'disabled'} ·{' '}
              {simulator.data.speedKmh} km/h
            </span>
            <button
              type="button"
              disabled={!simulator.data.enabled || toggleSimulator.isPending}
              onClick={() => toggleSimulator.mutate(!simulator.data.paused)}
            >
              {simulator.data.paused ? 'Resume riders' : 'Pause riders'}
            </button>
          </div>
        )}
      </div>

      <ErrorNote error={error} />

      <MapView markers={markers} fitKey="dispatch" height={320} />

      <section className="panel">
        <h2>Waiting for a rider ({pending.length})</h2>
        {pending.length === 0 ? (
          <p className="muted">Nothing is waiting. New orders are assigned automatically within a few seconds.</p>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>#</th>
                  <th>Customer</th>
                  <th>Trip</th>
                  <th>Created</th>
                  <th>Assign</th>
                </tr>
              </thead>
              <tbody>
                {pending.map((delivery) => (
                  <tr key={delivery.id}>
                    <td>{delivery.id}</td>
                    <td>{delivery.customer.username}</td>
                    <td>
                      {delivery.pickupAddr} → {delivery.dropoffAddr}
                    </td>
                    <td>{timeAgo(delivery.createdAt)}</td>
                    <td>
                      <div className="actions">
                        <button type="button" disabled={auto.isPending} onClick={() => auto.mutate(delivery.id)}>
                          Nearest rider
                        </button>
                        <select
                          aria-label={`Rider for delivery ${delivery.id}`}
                          value={choice[delivery.id] ?? ''}
                          onChange={(event) => setChoice({ ...choice, [delivery.id]: Number(event.target.value) })}
                        >
                          <option value="">Pick a rider…</option>
                          {idleRiders.map((rider) => (
                            <option key={rider.id} value={rider.id}>
                              {rider.name}
                            </option>
                          ))}
                        </select>
                        <button
                          type="button"
                          disabled={choice[delivery.id] === undefined || assign.isPending}
                          onClick={() => {
                            const riderId = choice[delivery.id]
                            if (riderId !== undefined) assign.mutate({ deliveryId: delivery.id, riderId })
                          }}
                        >
                          Assign
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className="panel">
        <h2>On the road ({active.length})</h2>
        {active.length === 0 ? (
          <p className="muted">No active deliveries.</p>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>#</th>
                  <th>Status</th>
                  <th>Trip</th>
                  <th>Rider</th>
                </tr>
              </thead>
              <tbody>
                {active.map((delivery) => (
                  <tr key={delivery.id}>
                    <td>{delivery.id}</td>
                    <td>
                      <StatusBadge status={delivery.status} />
                    </td>
                    <td>
                      {delivery.pickupAddr} → {delivery.dropoffAddr}
                    </td>
                    <td>{delivery.rider?.name ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className="panel">
        <h2>Riders</h2>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Name</th>
                <th>Status</th>
                <th>Position</th>
                <th>Last report</th>
              </tr>
            </thead>
            <tbody>
              {(riders.data ?? []).map((rider) => (
                <tr key={rider.id}>
                  <td>{rider.name}</td>
                  <td>
                    <RiderBadge status={rider.status} />
                  </td>
                  <td>
                    {formatCoordinate(rider.lat)}, {formatCoordinate(rider.lng)}
                  </td>
                  <td>{timeAgo(rider.locationUpdatedAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </>
  )
}
