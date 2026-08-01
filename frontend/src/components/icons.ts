/**
 * Every icon NovoCore uses, and the only place `@phosphor-icons/react` is named.
 *
 * **Never import from the `@phosphor-icons/react` barrel.** ESLint fails the build on one, and the
 * reason is measured rather than stylistic:
 *
 * The package ships 1,512 icon modules re-exported from a single index, each under two names — its
 * own and a deprecated alias — for 3,045 exports in total. Production is fine: Rollup tree-shakes
 * the barrel and the built bundle carries only what is reachable. **Development is not.** Vite does
 * not tree-shake; it pre-bundles a bare import into one optimised chunk and serves the whole thing.
 * That chunk measured **16.78 MB decoded — 64% of everything the dev server sent for one page** —
 * and cost 436 ms of main-thread parse and execute on an unthrottled machine, 1.6 s at 4× CPU
 * throttling and 2.5 s at 6×. React itself costs 38 ms by the same measurement.
 *
 * Nine icons were reaching for all 3,045. Importing each from its own `dist/csr/<Name>` entry point
 * is what the package's `exports` map is for — `./dist/csr/*` resolves to `./dist/csr/*.es.js` with
 * types alongside — and it leaves dev with nine small modules instead of one enormous one.
 *
 * Call sites import from `@/components/icons` and are otherwise unchanged, so the names below are
 * deliberately the same ones the barrel exports.
 *
 * **Adding an icon:** add a line here, using the `<Name>Icon` export rather than the bare `<Name>`
 * — the latter is deprecated upstream and both are present in every module. The file name carries
 * no `Icon` suffix, so `SidebarIcon` lives in `dist/csr/Sidebar`.
 */

export { CaretDownIcon } from '@phosphor-icons/react/dist/csr/CaretDown'
export { CaretLeftIcon } from '@phosphor-icons/react/dist/csr/CaretLeft'
export { CaretRightIcon } from '@phosphor-icons/react/dist/csr/CaretRight'
export { CaretUpIcon } from '@phosphor-icons/react/dist/csr/CaretUp'
export { CaretUpDownIcon } from '@phosphor-icons/react/dist/csr/CaretUpDown'
export { CheckIcon } from '@phosphor-icons/react/dist/csr/Check'
export { PlusIcon } from '@phosphor-icons/react/dist/csr/Plus'
export { SidebarIcon } from '@phosphor-icons/react/dist/csr/Sidebar'
export { WarningCircleIcon } from '@phosphor-icons/react/dist/csr/WarningCircle'
export { XIcon } from '@phosphor-icons/react/dist/csr/X'

/*
 * No `Icon` type is re-exported here, deliberately: nothing takes an icon as a prop yet, and knip
 * reports an unused export as a finding. When something does need it, it lives at
 * `@phosphor-icons/react/dist/lib/types` — a types-only module, so it erases and costs no bytes.
 * Take it from there rather than from the barrel, which would pull the whole chunk back in for any
 * file that also imports a value from here.
 */
