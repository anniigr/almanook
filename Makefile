# Almanook developer entry points (documented in README.md).
SHELL := /bin/bash
ENV := $(shell test -f .env && echo .env || echo .env.example)

.PHONY: help env infra up down reset logs backend frontend test test-backend test-frontend e2e build lint fmt

help: ## list targets
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-15s\033[0m %s\n", $$1, $$2}'

env: ## create .env from .env.example
	@test -f .env || { cp .env.example .env && echo "created .env — edit passwords and keys"; }

infra: env ## start PostgreSQL only (for local backend/frontend development)
	docker compose up -d postgres

up: env ## build and start the full stack (PostgreSQL, backend, web on http://localhost:$${WEB_PORT:-3000})
	docker compose up -d --build

down: ## stop the stack (data is kept)
	docker compose down

reset: ## stop the stack and delete the database volume
	docker compose down -v

logs: ## follow backend logs
	docker compose logs -f backend

backend: env ## run the backend on the host (migrations run automatically on start)
	set -a && source $(ENV) && set +a && cd backend && ./mvnw -q spring-boot:run

frontend: ## run the Vite dev server on http://localhost:5173 (proxies /api to the backend)
	set -a && source $(ENV) && set +a && cd frontend && npm run dev

test: test-backend test-frontend ## all unit + integration tests

test-backend: ## backend unit + Testcontainers integration tests (needs Docker)
	cd backend && ./mvnw -B verify

test-frontend: ## frontend lint, type check and component tests
	cd frontend && npm run lint && npm run typecheck && npm test

e2e: ## Playwright end-to-end tests against a running stack (E2E_BASE_URL, default http://localhost:3000)
	cd frontend && npx playwright test

build: ## production artifacts: backend jar and frontend dist
	cd backend && ./mvnw -B -q -DskipTests package && cd ../frontend && npm run build

lint: ## formatting and lint checks
	cd backend && ./mvnw -B -q spotless:check && cd ../frontend && npm run lint

fmt: ## apply formatting
	cd backend && ./mvnw -B -q spotless:apply && cd ../frontend && npm run format
