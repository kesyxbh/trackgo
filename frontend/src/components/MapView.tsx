import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { useEffect, useRef } from 'react'

export type MarkerKind = 'pickup' | 'dropoff' | 'rider' | 'rider-idle' | 'rider-busy' | 'rider-offline'

export interface MapMarker {
  id: string
  lat: number
  lng: number
  label: string
  kind: MarkerKind
}

export interface MapLine {
  id: string
  points: [number, number][]
}

interface Props {
  markers: MapMarker[]
  lines?: MapLine[]
  /**
   * The map re-fits itself to the markers only when this value changes. Without it, every 3-second
   * refresh would yank the view back and make the map impossible to pan.
   */
  fitKey: string
  height?: number
}

/** Markers are plain DOM built with textContent, so a label can never inject HTML. */
function buildIcon(marker: MapMarker): L.DivIcon {
  const root = document.createElement('div')
  root.className = `tg-pin tg-pin--${marker.kind}`
  const dot = document.createElement('span')
  dot.className = 'tg-pin__dot'
  const label = document.createElement('span')
  label.className = 'tg-pin__label'
  label.textContent = marker.label
  root.append(dot, label)
  return L.divIcon({ html: root, className: 'tg-pin-wrapper', iconSize: [0, 0] })
}

/**
 * A thin wrapper around Leaflet. React owns the data (markers, lines); Leaflet owns the pixels.
 * The first effect creates the map once; the second one redraws the overlay layer whenever the
 * data changes. The map itself is never re-created, so panning and zooming survive refreshes.
 */
export function MapView({ markers, lines = [], fitKey, height = 360 }: Props) {
  const containerRef = useRef<HTMLDivElement | null>(null)
  const mapRef = useRef<L.Map | null>(null)
  const layerRef = useRef<L.LayerGroup | null>(null)
  const fittedKeyRef = useRef<string | null>(null)

  useEffect(() => {
    const container = containerRef.current
    if (container === null) return

    const map = L.map(container).setView([39.9087, 116.4], 12)
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; OpenStreetMap contributors',
    }).addTo(map)
    mapRef.current = map
    layerRef.current = L.layerGroup().addTo(map)

    return () => {
      map.remove()
      mapRef.current = null
      layerRef.current = null
      fittedKeyRef.current = null
    }
  }, [])

  useEffect(() => {
    const map = mapRef.current
    const layer = layerRef.current
    if (map === null || layer === null) return

    layer.clearLayers()
    for (const line of lines) {
      L.polyline(line.points, { color: '#64748b', weight: 3, dashArray: '6 8' }).addTo(layer)
    }
    for (const marker of markers) {
      L.marker([marker.lat, marker.lng], { icon: buildIcon(marker), title: marker.label }).addTo(layer)
    }

    if (fittedKeyRef.current !== fitKey && markers.length > 0) {
      map.fitBounds(L.latLngBounds(markers.map((m): [number, number] => [m.lat, m.lng])), {
        padding: [48, 48],
        maxZoom: 15,
      })
      fittedKeyRef.current = fitKey
    }
  }, [markers, lines, fitKey])

  return <div ref={containerRef} className="map" style={{ height }} aria-label="Map" />
}
