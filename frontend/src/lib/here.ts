import { useCallback, useEffect, useState } from 'react'

// Where the phone is, for "you're at this customer's house". The location stays on the
// phone: it's compared with the customers' addresses here and never sent anywhere.
// A web app only gets the location while it's open, which is all this needs.

export interface Here {
  latitude: number
  longitude: number
  accuracy: number // metres
}

export interface Located {
  latitude: number | null
  longitude: number | null
}

const CONSENT_KEY = 'servicelog.location'
export const supported = typeof navigator !== 'undefined' && 'geolocation' in navigator

// Close enough to count as "at the house": the map point of an address can be off by a few
// houses, and the phone's own fix adds its error on top
export const AT_THE_HOUSE_METRES = 150

function choice(): 'on' | 'off' | null {
  try {
    const value = localStorage.getItem(CONSENT_KEY)
    return value === 'on' || value === 'off' ? value : null
  } catch {
    return null
  }
}

function remember(on: boolean) {
  try {
    localStorage.setItem(CONSENT_KEY, on ? 'on' : 'off')
  } catch {
    // private browsing: it just asks again next time
  }
}

// Straight-line distance in metres (haversine)
export function distanceMetres(a: { latitude: number; longitude: number }, b: Located): number | null {
  if (b.latitude == null || b.longitude == null) return null
  const rad = Math.PI / 180
  const dLat = (b.latitude - a.latitude) * rad
  const dLon = (b.longitude - a.longitude) * rad
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(a.latitude * rad) * Math.cos(b.latitude * rad) * Math.sin(dLon / 2) ** 2
  return 2 * 6_371_000 * Math.asin(Math.sqrt(h))
}

export function atTheHouse(here: Here, place: Located): boolean {
  const d = distanceMetres(here, place)
  return d != null && d <= AT_THE_HOUSE_METRES + Math.min(here.accuracy, 100)
}

// "350 ft" close by, "1.4 mi" further out
export function formatDistance(metres: number): string {
  const feet = metres * 3.28084
  if (feet < 1000) return `${Math.round(feet / 10) * 10} ft`
  const miles = metres / 1609.34
  return `${miles < 10 ? miles.toFixed(1) : Math.round(miles)} mi`
}

// Follows the phone's location while the screen is open, once the owner has turned it on.
// `enable` asks iOS for permission (from a tap, so the prompt makes sense).
export function useHere() {
  const [on, setOn] = useState(supported && choice() === 'on')
  // Turned on or said "not now" before: don't offer it again
  const [decided, setDecided] = useState(choice() !== null)
  const [here, setHere] = useState<Here | null>(null)
  const [denied, setDenied] = useState(false)

  useEffect(() => {
    if (!on || !supported) return
    let watch: number | null = null
    const start = () => {
      if (watch != null || document.visibilityState !== 'visible') return
      watch = navigator.geolocation.watchPosition(
        (p) => {
          setDenied(false)
          setHere({ latitude: p.coords.latitude, longitude: p.coords.longitude, accuracy: p.coords.accuracy })
        },
        (err) => {
          if (err.code === err.PERMISSION_DENIED) setDenied(true)
        },
        { enableHighAccuracy: true, maximumAge: 20_000, timeout: 30_000 },
      )
    }
    const stop = () => {
      if (watch != null) navigator.geolocation.clearWatch(watch)
      watch = null
    }
    // Only while the app is on screen: no location use in the background
    const onVisibility = () => (document.visibilityState === 'visible' ? start() : stop())
    start()
    document.addEventListener('visibilitychange', onVisibility)
    return () => {
      stop()
      document.removeEventListener('visibilitychange', onVisibility)
    }
  }, [on])

  const enable = useCallback(() => {
    remember(true)
    setOn(true)
    setDecided(true)
  }, [])

  const disable = useCallback(() => {
    remember(false)
    setOn(false)
    setDecided(true)
    setHere(null)
  }, [])

  return { supported, on, decided, here, denied, enable, disable }
}

// One fresh fix, for "who's this house?" (asks iOS the first time)
export function locateOnce(): Promise<Here> {
  return new Promise((resolve, reject) => {
    if (!supported) {
      reject(new Error('This phone can’t share its location with the app.'))
      return
    }
    navigator.geolocation.getCurrentPosition(
      (p) => {
        remember(true)
        resolve({ latitude: p.coords.latitude, longitude: p.coords.longitude, accuracy: p.coords.accuracy })
      },
      (err) =>
        reject(
          new Error(
            err.code === err.PERMISSION_DENIED
              ? 'Location is off for ServiceLog. Turn it on in Settings > Privacy & Security > Location Services.'
              : 'Couldn’t get a location fix. Try again in a moment.',
          ),
        ),
      { enableHighAccuracy: true, maximumAge: 10_000, timeout: 20_000 },
    )
  })
}
