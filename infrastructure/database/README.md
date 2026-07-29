# Database migrations

Flyway migrations are stored in `backend/src/main/resources/db/migration` so the backend artifact and local migration
container use the same reviewed files.

For local development, start dependencies and apply migrations with:

```shell
docker compose -f infrastructure/docker-compose.yml up -d postgres redis
docker compose -f infrastructure/docker-compose.yml --profile migration run --rm migrate
```

The checked-in defaults are disposable local-development values only. Hosted environments must supply credentials from a
managed secrets system and run migrations as a dedicated, least-privilege deployment step.
