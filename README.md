# Cryptofolio Backend (Spring Boot)

A Spring Boot port of the original Django REST Framework backend. It exposes the
exact same `/api/...` contract the Svelte frontend already uses, on port **8000**.

## Stack

- Java 21, Spring Boot 3.5
- Spring Web (MVC) + Spring Data JPA (Hibernate)
- PostgreSQL (database `cryptofolio`)

## Prerequisites

- A running PostgreSQL with a `cryptofolio` database. To create it:
  ```bash
  createdb cryptofolio
  ```
- JDK 21+ on the path (a Maven install is **not** required — use the bundled `./mvnw`).

## Configuration

See `src/main/resources/application.properties`. Override via environment variables:

| Property                | Env var              | Default                                            |
|-------------------------|----------------------|----------------------------------------------------|
| `spring.datasource.url` | `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/cryptofolio`  |
| `spring.datasource.username` | `SPRING_DATASOURCE_USERNAME` | `jc`                                   |
| `coingecko.api-key`     | `COINGECKO_API_KEY`  | _(empty)_                                          |

Hibernate runs with `ddl-auto=update`, so the schema is created automatically on
first start.

## Run

```bash
export COINGECKO_API_KEY=your-key-here   # optional, only needed for the refresh endpoint
./mvnw spring-boot:run
```

Or build a jar and run it:

```bash
./mvnw -DskipTests package
java -jar target/backend-0.0.1-SNAPSHOT.jar
```

## API

All routes are under `/api` and accept an optional trailing slash.

| Method | Path | Description |
|--------|------|-------------|
| GET    | `/api/all_coins/` | All coins ordered by market cap rank |
| POST   | `/api/all_coins_coingecko/` | Refresh coins from CoinGecko (needs API key) |
| POST   | `/api/create_profile/` | Create a portfolio profile (returns `hash_id`) |
| GET    | `/api/get_profile/{hashId}/` | Fetch a profile |
| GET    | `/api/profile/{hashId}/portfolio/` | Aggregated holdings (one row per coin) |
| GET    | `/api/profile/{hashId}/portfolio_transactions/` | All raw transactions |
| POST   | `/api/profile/{hashId}/add_coin/` | Add a BUY/SELL/TRANSFER record |
| GET    | `/api/profile/{hashId}/transactions/{coinId}/` | Transactions for one coin |
| PUT    | `/api/profile/{hashId}/transaction/{transactionId}/` | Partial update of a transaction |
| DELETE | `/api/profile/{hashId}/remove_coin/{coinId}/` | Remove a coin's records |
| GET    | `/api/profile/{hashId}/filtered_coins/list/` | List filtered coins |
| POST   | `/api/profile/{hashId}/filtered_coins/update/` | Replace filtered coins (`coin_ids`) |

## Notes on parity with the Django backend

- Decimal fields are serialized as **strings** and `current_price` as a **number**,
  matching Django REST Framework's defaults so the frontend needs no changes.
- The original Django `remove_coin` view looked coins up by `name`; this port looks
  up by `coin_id` first, then falls back to `name`, and deletes all matching records.
- This is a fresh PostgreSQL database — data from the old `db.sqlite3` is not migrated.
