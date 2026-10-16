import { createContext, useContext } from 'react'

/**
 * "Who am I?" for an application without login. A customer id and a rider id are remembered in the
 * browser; the role itself is simply the part of the URL you are on (/customer, /rider, /dispatch).
 */
export interface Identity {
  customerId: number | null
  riderId: number | null
  setCustomerId: (id: number) => void
  setRiderId: (id: number) => void
}

export const IdentityContext = createContext<Identity | null>(null)

export function useIdentity(): Identity {
  const value = useContext(IdentityContext)
  if (value === null) {
    throw new Error('useIdentity must be used inside <IdentityProvider>')
  }
  return value
}
