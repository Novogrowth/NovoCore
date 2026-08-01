import { AccessLevel, Section, type Me, type SectionAccess } from '@/api/generated/model'

/**
 * The **invariant** half of a signed-in user — and deliberately nothing else.
 *
 * **Why this exists, and why it is this small.** Nineteen `Me` literals were hand-authored across
 * seven test files, and every one of them omitted `active`. Nothing noticed until the spec started
 * declaring primitive components required, at which point `tsc` reported all nineteen at once: they
 * had been describing a `/api/me` response the server never sends. Fixing one field meant editing
 * eleven sites. This is so that the next one is a single line.
 *
 * ⚠️ **What must NOT move in here: anything a test asserts on.** Which sections a role holds, at
 * which level, and whether the role is full-access are the *content* of these tests — a reader has
 * to see them at the call site or the test stops being evidence and becomes a pointer to a shared
 * file. So `role` and `sections` are **required parameters**, not defaults, and `id` and
 * `restrictedFields` are overridable for the tests where they carry meaning (self-detection on the
 * user and role screens; field restrictions in `permissions.test.ts`).
 *
 * What is left — `id`, `username`, `active`, `restrictedFields` — is the part no test reads and
 * every fixture has to get right anyway. That is exactly the part that drifted.
 *
 * **Not used by `app.test.tsx` or `session.test.tsx`**, on purpose: those build a raw JSON body and
 * assert on `displayName`, so their identity fields are content too.
 */
export function aUser(fields: Pick<Me, 'role' | 'sections'> & Partial<Me>): Me {
  return {
    id: 1,
    username: 'test.user',
    active: true,
    restrictedFields: [],
    ...fields,
  }
}

/**
 * Every section at one level — the "this role can reach everything" background.
 *
 * `available: true` throughout, which is what the backend reports for all but the two reserved
 * module sections. A test that cares about availability says so itself rather than using this.
 */
export function everySectionAt(level: AccessLevel): SectionAccess[] {
  return Object.values(Section).map((section) => ({ section, level, available: true }))
}

/** The seeded full-access role, as `/api/me` reports it. */
export const OWNER_ROLE = {
  id: 1,
  name: 'OWNER',
  fullAccess: true,
  systemRole: true,
} as const
