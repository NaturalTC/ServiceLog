import { useEffect, useState } from 'react'

// Returns the value only after it has stopped changing for `delayMs`,
// so typing "smith" sends one request instead of five.
export function useDebouncedValue<T>(value: T, delayMs: number): T {
  const [debounced, setDebounced] = useState(value)

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs)
    return () => clearTimeout(timer)
  }, [value, delayMs])

  return debounced
}
