import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

/**
 * `window.matchMedia`, which jsdom does not implement.
 *
 * The sidebar asks it whether the viewport is a phone. Without it, any test that renders the real
 * shell dies with "matchMedia is not a function" — which is how the first version of `app.test.tsx`
 * could only cover the signed-out half of the screen. A desktop-width answer is the right default:
 * it is what the shop's machines are.
 */
if (!window.matchMedia) {
  window.matchMedia = (query: string): MediaQueryList =>
    ({
      matches: false,
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    }) as MediaQueryList
}

afterEach(() => {
  cleanup()
})
