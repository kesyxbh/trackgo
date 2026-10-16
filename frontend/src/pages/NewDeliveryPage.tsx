import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { createDelivery } from '../api/deliveries.ts'
import { ErrorNote } from '../components/ErrorNote.tsx'
import { MapView, type MapMarker } from '../components/MapView.tsx'
import { useSelectedCustomer } from '../identity/useSelected.ts'
import { findLandmark, LANDMARKS } from '../lib/landmarks.ts'

export function NewDeliveryPage() {
  const { selected: customer } = useSelectedCustomer()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const [pickupId, setPickupId] = useState('tiananmen')
  const [dropoffId, setDropoffId] = useState('cbd')

  const pickup = findLandmark(pickupId)
  const dropoff = findLandmark(dropoffId)
  const samePlace = pickupId === dropoffId

  const create = useMutation({
    mutationFn: createDelivery,
    onSuccess: async (delivery) => {
      await queryClient.invalidateQueries({ queryKey: ['deliveries'] })
      navigate(`/customer/deliveries/${delivery.id}`)
    },
  })

  const submit = (event: FormEvent) => {
    event.preventDefault()
    if (customer === null || pickup === undefined || dropoff === undefined || samePlace) return
    create.mutate({
      customerId: customer.id,
      pickupAddr: pickup.name,
      pickupLat: pickup.lat,
      pickupLng: pickup.lng,
      dropoffAddr: dropoff.name,
      dropoffLat: dropoff.lat,
      dropoffLng: dropoff.lng,
    })
  }

  const markers: MapMarker[] = []
  if (pickup) markers.push({ id: 'pickup', lat: pickup.lat, lng: pickup.lng, label: `Pickup: ${pickup.name}`, kind: 'pickup' })
  if (dropoff) markers.push({ id: 'dropoff', lat: dropoff.lat, lng: dropoff.lng, label: `Drop-off: ${dropoff.name}`, kind: 'dropoff' })

  return (
    <>
      <div className="page-head">
        <h1>New delivery</h1>
      </div>

      <form className="form" onSubmit={submit}>
        <label>
          Pickup
          <select value={pickupId} onChange={(event) => setPickupId(event.target.value)}>
            {LANDMARKS.map((landmark) => (
              <option key={landmark.id} value={landmark.id}>
                {landmark.name}
              </option>
            ))}
          </select>
        </label>

        <label>
          Drop-off
          <select value={dropoffId} onChange={(event) => setDropoffId(event.target.value)}>
            {LANDMARKS.map((landmark) => (
              <option key={landmark.id} value={landmark.id}>
                {landmark.name}
              </option>
            ))}
          </select>
        </label>

        {samePlace && <p className="note note--warn">Pickup and drop-off must be different places.</p>}
        <ErrorNote error={create.error} />

        <button type="submit" disabled={customer === null || samePlace || create.isPending}>
          {create.isPending ? 'Placing order…' : 'Place order'}
        </button>
      </form>

      <MapView markers={markers} fitKey={`${pickupId}:${dropoffId}`} height={320} />
    </>
  )
}
