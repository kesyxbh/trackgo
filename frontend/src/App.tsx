import { Navigate, Route, Routes } from 'react-router-dom'
import { Layout } from './components/Layout.tsx'
import { DispatchPage } from './pages/DispatchPage.tsx'
import { MyDeliveriesPage } from './pages/MyDeliveriesPage.tsx'
import { NewDeliveryPage } from './pages/NewDeliveryPage.tsx'
import { RiderConsolePage } from './pages/RiderConsolePage.tsx'
import { TrackingPage } from './pages/TrackingPage.tsx'

export function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<Navigate to="/customer" replace />} />
        <Route path="customer" element={<MyDeliveriesPage />} />
        <Route path="customer/new" element={<NewDeliveryPage />} />
        <Route path="customer/deliveries/:id" element={<TrackingPage />} />
        <Route path="rider" element={<RiderConsolePage />} />
        <Route path="dispatch" element={<DispatchPage />} />
        <Route path="*" element={<p className="note">Page not found.</p>} />
      </Route>
    </Routes>
  )
}
