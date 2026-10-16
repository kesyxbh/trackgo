import { useState, type ReactNode } from 'react'
import { IdentityContext } from './IdentityContext.ts'

const STORAGE_KEY = 'trackgo.identity'

interface Stored {
  customerId: number | null
  riderId: number | null
}

function load(): Stored {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) {
      const parsed = JSON.parse(raw) as Partial<Stored>
      return { customerId: parsed.customerId ?? null, riderId: parsed.riderId ?? null }
    }
  } catch {
    // Storage can be unavailable (private mode) or hold garbage; start fresh.
  }
  return { customerId: null, riderId: null }
}

function save(value: Stored) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(value))
  } catch {
    // Not persisting is fine: the choice just will not survive a reload.
  }
}

export function IdentityProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<Stored>(load)

  const update = (next: Stored) => {
    setState(next)
    save(next)
  }

  return (
    <IdentityContext.Provider
      value={{
        customerId: state.customerId,
        riderId: state.riderId,
        setCustomerId: (id) => update({ ...state, customerId: id }),
        setRiderId: (id) => update({ ...state, riderId: id }),
      }}
    >
      {children}
    </IdentityContext.Provider>
  )
}
