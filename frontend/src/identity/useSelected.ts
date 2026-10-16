import { useQuery } from '@tanstack/react-query'
import { listCustomers, listRiders } from '../api/misc.ts'
import { POLL_MS } from '../lib/format.ts'
import { useIdentity } from './IdentityContext.ts'

/**
 * The customer the UI currently acts as. If nothing valid was chosen yet, the first customer is
 * used. The fallback is computed on every render instead of being written back with an effect, so
 * there is no "select the first one" state juggling to get wrong.
 */
export function useSelectedCustomer() {
  const { customerId, setCustomerId } = useIdentity()
  const query = useQuery({ queryKey: ['customers'], queryFn: listCustomers, staleTime: 60_000 })
  const customers = query.data ?? []
  const selected = customers.find((customer) => customer.id === customerId) ?? customers[0] ?? null
  return { customers, selected, select: setCustomerId, isLoading: query.isLoading, error: query.error }
}

/** Same idea for the rider; riders are polled because their status and position change constantly. */
export function useSelectedRider() {
  const { riderId, setRiderId } = useIdentity()
  const query = useQuery({ queryKey: ['riders'], queryFn: () => listRiders(), refetchInterval: POLL_MS })
  const riders = query.data ?? []
  const selected = riders.find((rider) => rider.id === riderId) ?? riders[0] ?? null
  return { riders, selected, select: setRiderId, isLoading: query.isLoading, error: query.error }
}
