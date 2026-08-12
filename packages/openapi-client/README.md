# Lunara OpenAPI client

This workspace exports the NestJS route metadata as a deterministic OpenAPI
3.0.3 snapshot and generates a Kotlin Coroutine/Retrofit2/Moshi client with
OpenAPI Generator 7.24.0.

```bash
npm run generate --workspace @lunara/openapi-client
npm run check --workspace @lunara/openapi-client
```

The generator runs from the pinned Docker image, so a local Java installation
is not required. Normal installations use `Authorization: Bearer`. If a reverse
proxy consumes that header for Basic authentication, configure the Android
OkHttp interceptor to send the Lunara token as
`X-Lunara-Authorization: Bearer <token>` instead.

Do not add passwords, bootstrap tokens, invitation codes or gateway credentials
to the OpenAPI file or generated client defaults.
