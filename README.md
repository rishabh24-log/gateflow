# GateFlow

Distributed API gateway with a live admin dashboard. Built for a backend / systems portfolio.

Traffic hits GateFlow first. The gateway checks an API key, applies a Redis rate limit, proxies to a downstream Usage service (API consumption reports), records the call, and can open a circuit if that service keeps failing. The React dashboard authenticates with JWT and shows real metrics, keys, usage, routes, and logs.

## Architecture

```
Browser dashboard  --JWT-->  GateFlow :8081
                                │
                     X-API-Key + rate limit
                                │
                                ▼
                         Usage service :8082

Postgres  → API keys + request logs
Redis     → per-key request counts (shared by gateway instances)
```

Two gateway containers (`8081` and `8083`) share Postgres and Redis, so rate limits stay consistent across instances.

## Features

- Reverse-proxy routing to `/usage/**`
- API key create / revoke, SHA-256 hashed at rest
- Per-key rate limit (Redis, local fallback if Redis is down)
- JWT login for `/api/admin/**`
- Request logs and overview metrics from real traffic
- Circuit breaker and 3s upstream timeout
- API tester in the dashboard
- Docker Compose with Postgres, Redis, Usage, and two gateways

## Demo login

- Username: `admin`
- Password: `gateflow`

A demo API key is created on first boot. Copy it from the gateway logs (`Created demo API key`).

## Run locally (fastest)

Needs Java 17 and Node.js. Redis is optional locally; limits fall back to memory.

Terminal 1 — Usage service:

```powershell
cd usage-service
..\backend\mvnw.cmd -f pom.xml spring-boot:run
```

Terminal 2 — Gateway:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Terminal 3 — Dashboard:

```powershell
cd frontend
npm install
npm run dev
```

Open [http://localhost:5173](http://localhost:5173) and sign in.

Try the gateway:

```powershell
curl.exe -H "X-API-Key: YOUR_KEY" http://localhost:8081/usage
```

Without a key you get `401`. Hit it over the quota (default 30/min) and you get `429`.

## Run with Docker

```powershell
cd backend
.\mvnw.cmd -DskipTests package
cd ..\usage-service
..\backend\mvnw.cmd -f pom.xml -DskipTests package
cd ..
docker compose up --build
```
## Stack

Java 17, Spring Boot 4, Spring Security, JPA, Postgres/H2, Redis, React, TypeScript, Vite, Docker Compose.
