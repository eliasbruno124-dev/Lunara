import 'dotenv/config';
import { defineConfig } from 'prisma/config';

export default defineConfig({
  schema: 'schema.prisma',
  migrations: {
    path: 'migrations',
  },
  datasource: {
    // Client generation during an image build has no live database. Migration
    // and runtime always override this harmless, non-routable build fallback.
    url:
      process.env.DATABASE_URL ??
      'postgresql://build-only:build-only@127.0.0.1:5432/lunara-build-only',
  },
});
