/** Preset places in central Beijing, so an order can be placed without a geocoding service. */
export interface Landmark {
  id: string
  name: string
  lat: number
  lng: number
}

export const LANDMARKS: readonly Landmark[] = [
  { id: 'tiananmen', name: 'Tiananmen Square', lat: 39.9087, lng: 116.3975 },
  { id: 'forbidden-city', name: 'Forbidden City', lat: 39.9163, lng: 116.3972 },
  { id: 'wangfujing', name: 'Wangfujing', lat: 39.9143, lng: 116.4107 },
  { id: 'beijing-station', name: 'Beijing Railway Station', lat: 39.9025, lng: 116.4272 },
  { id: 'cbd', name: 'CBD / Guomao', lat: 39.9087, lng: 116.4605 },
  { id: 'sanlitun', name: 'Sanlitun', lat: 39.933, lng: 116.454 },
  { id: 'olympic-park', name: 'Olympic Park', lat: 39.9929, lng: 116.3966 },
  { id: 'zhongguancun', name: 'Zhongguancun', lat: 39.9836, lng: 116.3105 },
  { id: 'west-station', name: 'Beijing West Railway Station', lat: 39.8946, lng: 116.3223 },
  { id: 'temple-of-heaven', name: 'Temple of Heaven', lat: 39.8822, lng: 116.4066 },
]

export function findLandmark(id: string): Landmark | undefined {
  return LANDMARKS.find((landmark) => landmark.id === id)
}
