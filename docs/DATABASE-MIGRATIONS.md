# Database migrations (Flyway)

The database schema is owned by **Flyway**, not Hibernate. Prod and dev run with
`spring.jpa.hibernate.ddl-auto=validate`, which means Hibernate **only checks** that the live
schema matches the JPA entities and fails fast on drift — it never issues `CREATE`/`ALTER`.

This replaced `ddl-auto=update`, which silently failed to add NOT NULL columns and could not alter
existing CHECK constraints on the already-populated Neon database — the class of bug that took prod
down twice (`students.status`, and the STAFF `app_users_role_check`).

## Where migrations live

    src/main/resources/db/migration/
      V1__baseline_schema.sql     <- the whole schema as of Flyway adoption

`V1` is a faithful transcription of the DDL Hibernate itself generates from the current entities, so
`validate` boots cleanly against a database that `V1` built.

## How it behaves on boot

- **Fresh / empty database** (a new Vici Neon, the docker dev DB, the `seed` profile): Flyway runs
  `V1` (and any later `V2…`) to build the schema, then Hibernate validates it.
- **Already-populated database** (the deployed Neon, originally built by the old `ddl-auto=update`):
  `spring.flyway.baseline-on-migrate=true` makes Flyway stamp it as baseline **V1** and **skip** it —
  the existing tables are left untouched — then apply `V2…` going forward.

## Changing the schema

1. Add a new file `V<n>__short_description.sql` (next integer, e.g. `V2__add_student_phone_verified.sql`)
   containing raw PostgreSQL DDL:

   ```sql
   ALTER TABLE students ADD COLUMN phone_verified boolean DEFAULT false NOT NULL;
   ```

2. Update the JPA entity to match (add the field). Keep `@ColumnDefault(...)` on new non-null fields
   so the entity's expectation and the DDL default agree and `V1` stays regenerable.
3. Boot the app. Flyway applies the pending migration automatically; the deployed Neon runs it on its
   next deploy. **No more hand-run `ALTER`s in the Neon SQL editor.**

### Rules

- **Never edit a migration that has already been applied.** Flyway checksums applied migrations and
  will refuse to start if one changes. Always add a new `V<n>`.
- **Never set `ddl-auto` back to `update`** — that reintroduces the silent-failure bug.
- Migrations are **PostgreSQL-specific**. Tests run on in-memory H2 with `spring.flyway.enabled=false`
  and Hibernate `create-drop`, so migrations do not run in the test suite.

## Regenerating the V1 baseline (rarely needed)

If the entities are ever rebased and you want to rebuild `V1` from scratch, dump Hibernate's DDL
against a Postgres database and transcribe it:

    ./mvnw spring-boot:run -Dspring-boot.run.arguments="\
      --spring.jpa.hibernate.ddl-auto=none \
      --spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action=create \
      --spring.jpa.properties.jakarta.persistence.schema-generation.scripts.create-target=target/generated-schema.sql \
      --spring.jpa.properties.jakarta.persistence.schema-generation.scripts.create-source=metadata"
