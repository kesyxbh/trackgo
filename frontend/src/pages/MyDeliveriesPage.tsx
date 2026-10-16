import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { listDeliveries } from '../api/deliveries.ts'
import { ChatWidget } from '../components/ChatWidget.tsx'
import { ErrorNote } from '../components/ErrorNote.tsx'
import { StatusBadge } from '../components/StatusBadge.tsx'
import { useSelectedCustomer } from '../identity/useSelected.ts'
import { POLL_MS, timeAgo } from '../lib/format.ts'

export function MyDeliveriesPage() {
  const { selected: customer, isLoading: customersLoading, error: customersError } = useSelectedCustomer()

  const deliveries = useQuery({
    queryKey: ['deliveries', { customerId: customer?.id }],
    queryFn: () => listDeliveries({ customerId: customer?.id }),
    enabled: customer !== null,
    refetchInterval: POLL_MS,
  })

  if (customersLoading) return <p className="muted">Loading…</p>
  if (customersError) return <ErrorNote error={customersError} />
  if (customer === null) return <p className="note">No customers exist yet.</p>

  return (
    <>
      <div className="page-head">
        <h1>My deliveries</h1>
        <Link className="button" to="/customer/new">
          New delivery
        </Link>
      </div>

      <ErrorNote error={deliveries.error} />
      {deliveries.data?.length === 0 && (
        <p className="note">You have no deliveries yet. Create one with “New delivery”.</p>
      )}

      <ul className="cards">
        {deliveries.data?.map((delivery) => (
          <li key={delivery.id} className="card">
            <div className="card__row">
              <Link to={`/customer/deliveries/${delivery.id}`} className="card__title">
                Delivery #{delivery.id}
              </Link>
              <StatusBadge status={delivery.status} />
            </div>
            <p>
              {delivery.pickupAddr} <span aria-hidden="true">→</span> {delivery.dropoffAddr}
            </p>
            <p className="muted">
              Created {timeAgo(delivery.createdAt)}
              {delivery.rider ? ` · Rider: ${delivery.rider.name}` : ''}
            </p>
          </li>
        ))}
      </ul>

      <ChatWidget customerId={customer.id} />
    </>
  )
}
