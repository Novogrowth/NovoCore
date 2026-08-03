import { defineConfig } from 'orval'

/**
 * The API client is generated from the committed, drift-checked spec — never hand-written.
 *
 * `docs/api/openapi.json` is produced by OpenApiSpecIT from the live handler mapping and guarded
 * by a CI check on the backend side, so it is the one description of the API that cannot quietly
 * disagree with the API. Generating from it means the same is true of the TypeScript.
 *
 * Regenerate with `npm run api:generate`. The output is committed, and frontend.yml regenerates
 * and diffs it, for the same reason the backend checks the spec itself.
 */
export default defineConfig({
  novocore: {
    input: {
      target: '../docs/api/openapi.json',
      override: {
        /**
         * The spec has no tags — every operation is named `SomeController_method` — and without
         * tags orval emits all 174 operations into a single file. This derives a tag from the
         * controller name so the output is one module per controller.
         *
         * It runs on orval's in-memory copy. The committed spec is never rewritten by a frontend
         * build: it belongs to the backend, and a generator that edits its own input is a
         * generator whose output nobody can reproduce.
         */
        transformer: (spec) => {
          const HTTP_VERBS = ['get', 'put', 'post', 'delete', 'patch', 'head', 'options']

          interface Operation {
            operationId?: string
            tags?: string[]
          }

          for (const operations of Object.values(spec.paths ?? {})) {
            for (const [verb, operation] of Object.entries(operations ?? {})) {
              if (!HTTP_VERBS.includes(verb.toLowerCase())) continue
              if (typeof operation !== 'object' || operation === null) continue
              const op = operation as Operation
              if (!op.operationId) continue

              if (!op.tags?.length) {
                const controller = op.operationId.split('_')[0] ?? 'api'
                op.tags = [
                  controller
                    .replace(/Controller$/, '')
                    .replace(/([a-z0-9])([A-Z])/g, '$1-$2')
                    .toLowerCase(),
                ]
              }
            }
          }

          /*
           * A de-duplication block stood here until 2026-08-03 and is deliberately gone.
           *
           * `InventoryController_writeOff` was the operationId of two operations — OpenAPI forbids
           * it, orval produced two hooks with one name, and the TypeScript did not compile. This
           * file worked around it by suffixing the HTTP verb. Backend queue item 1 fixed the cause:
           * the POST handler is `createWriteOff`, and `OpenApiSpecIT` now REFUSES to write a spec
           * containing a duplicate rather than emitting an invalid document silently.
           *
           * So the workaround is deleted rather than left dormant, along with the assertion in
           * `spec-hygiene.test.ts` that pinned it — that test was written to fail in both
           * directions, including on the day the collision became empty, and this is that day. A
           * workaround outliving its cause is worse than either, because the next reader cannot
           * tell whether it is load-bearing.
           */

          return spec
        },
      },
    },
    output: {
      mode: 'tags-split',
      target: 'src/api/generated/endpoints',
      schemas: 'src/api/generated/model',
      client: 'react-query',
      httpClient: 'axios',
      /*
       * Deliberately NOT `httpClient: 'fetch'`, which wraps every response as
       * `{ data, status, headers }` and would leave each of a hundred screens writing
       * `query.data?.data`. This shape hands the mutator one options object and returns the body,
       * so `query.data` is the thing the endpoint returns. axios is not installed and is not
       * needed — only the signature is borrowed, and `src/api/http.ts` implements it with fetch.
       */
      clean: true,
      override: {
        mutator: {
          path: './src/api/http.ts',
          name: 'apiMutator',
        },
        query: {
          /*
           * ⚠️ `useQuery: true` was set here and it is NOT a harmless default.
           *
           * It forces EVERY operation to be generated as a query — including all 92 writes (51 POST,
           * 31 PATCH, 7 PUT, 3 DELETE). A component that merely rendered `useProductControllerRename`
           * would have sent the PATCH on mount, and again on every refetch, invalidation and window
           * focus: writes executed as reads, repeatedly, by rendering.
           *
           * Nothing caught it during the foundations pass because nothing consumed a write hook
           * yet — the first screen to attempt one is what found it. Left to orval's own rule now:
           * GET becomes a query, everything else becomes a mutation.
           *
           * `client-shape.test.ts` asserts the shape of all 174 operations and was proven to fail
           * against this exact config, so it cannot come back silently.
           */
          signal: true,
        },
      },
    },
  },
})
