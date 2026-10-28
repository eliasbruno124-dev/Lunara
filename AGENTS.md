# Lunara maintenance

- Read `docs/CODE_GUIDE.md` and use `docs/CODE_MAP.md` to locate the relevant
  implementation. The map is a syntax inventory, not a runtime call graph.
- Before trusting generated line links, run `npm run code-map:check`.
- After changing repository source or documentation, run `npm run code-map:write`
  and then `npm run code-map:check`. Do not edit generated maps manually.
- Review behavioral descriptions in `docs/code-map-flows.json` when a flow
  changes. The freshness check verifies references, not the meaning of prose.
- Preserve identity isolation, encrypted local data, queued changes and explicit
  revocation. Use synthetic data for tests and screenshots.
- Deployment credentials, signing keys and production data never belong in this
  repository or its generated documentation.
