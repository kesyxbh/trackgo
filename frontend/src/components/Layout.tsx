import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { useSelectedCustomer, useSelectedRider } from '../identity/useSelected.ts'

function CustomerPicker() {
  const { customers, selected, select } = useSelectedCustomer()
  if (customers.length === 0) return null
  return (
    <label className="picker">
      Acting as
      <select value={selected?.id ?? ''} onChange={(event) => select(Number(event.target.value))}>
        {customers.map((customer) => (
          <option key={customer.id} value={customer.id}>
            {customer.username}
          </option>
        ))}
      </select>
    </label>
  )
}

function RiderPicker() {
  const { riders, selected, select } = useSelectedRider()
  if (riders.length === 0) return null
  return (
    <label className="picker">
      Acting as
      <select value={selected?.id ?? ''} onChange={(event) => select(Number(event.target.value))}>
        {riders.map((rider) => (
          <option key={rider.id} value={rider.id}>
            {rider.name}
          </option>
        ))}
      </select>
    </label>
  )
}

/** Which identity picker to show depends on the role, which is just the URL prefix. */
function IdentityPicker() {
  const { pathname } = useLocation()
  if (pathname.startsWith('/customer')) return <CustomerPicker />
  if (pathname.startsWith('/rider')) return <RiderPicker />
  return <span className="muted">Dispatcher view</span>
}

export function Layout() {
  return (
    <div className="app">
      <header className="topbar">
        <NavLink to="/" className="brand">
          TrackGo
        </NavLink>
        <nav className="roles" aria-label="Role">
          <NavLink to="/customer">Customer</NavLink>
          <NavLink to="/rider">Rider</NavLink>
          <NavLink to="/dispatch">Dispatcher</NavLink>
        </nav>
        <IdentityPicker />
      </header>
      <main className="content">
        <Outlet />
      </main>
    </div>
  )
}
