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
          const operationsById = new Map<string, { operation: Operation; verb: string }[]>()

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

              const existing = operationsById.get(op.operationId) ?? []
              existing.push({ operation: op, verb: verb.toLowerCase() })
              operationsById.set(op.operationId, existing)
            }
          }

          /*
           * ⚠️ WORKAROUND FOR A DEFECT IN THE COMMITTED SPEC, not a generation preference.
           *
           * OpenAPI requires operationId to be unique across the document, and
           * `InventoryController_writeOff` is not: `POST /api/inventory/write-offs` and
           * `GET /api/inventory/write-offs/{id}` are two Java methods of the same name, and
           * OpenApiSpecIT derives the id as Controller_method. Generating from it produces two
           * hooks with one name and the TypeScript does not compile.
           *
           * The real fix is on the backend — either rename one method or have the generator
           * disambiguate — and until it lands the collision is resolved here by suffixing the
           * verb, deterministically for every member of the group so the result does not depend
           * on the order operations appear in the file. `spec-hygiene.test.ts` fails when the set
           * of collisions changes in either direction, including when it is finally empty.
           */
          for (const [operationId, group] of operationsById) {
            if (group.length < 2) continue
            console.warn(
              `orval: operationId "${operationId}" is used by ${group.length} operations, ` +
                `which OpenAPI forbids. Suffixing with the HTTP verb — see spec-hygiene.test.ts.`,
            )
            for (const { operation, verb } of group) {
              operation.operationId = `${operationId}${verb.charAt(0).toUpperCase()}${verb.slice(1)}`
            }
          }

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
           * It forces EVERY operation to be generated as a query — including all 66 POST, PUT,
           * PATCH and DELETE routes. A component that merely rendered `useProductControllerRename`
           * would have sent the PATCH on mount, and again on every refetch, invalidation and window
           * focus: writes executed as reads, repeatedly, by rendering.
           *
           * Nothing caught it during the foundations pass because nothing consumed a write hook
           * yet — the first screen to attempt one is what found it. Left to orval's own rule now:
           * GET becomes a query, everything else becomes a mutation.
           *
           * `product-detail.test.tsx` asserts a render sends no write, so this cannot come back
           * silently.
           */
          signal: true,
        },
      },
    },
  },
})
