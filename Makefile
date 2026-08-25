# Convenience targets. Everything here also works as a plain command, so you
# are never locked into make.

SHELL := /bin/bash
.DEFAULT_GOAL := help

.PHONY: help
help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) \
		| awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-22s\033[0m %s\n", $$1, $$2}'

# ---- server ---------------------------------------------------------------
.PHONY: up
up: ## Start the whole stack (db + server + caddy)
	docker compose up -d --build

.PHONY: down
down: ## Stop the stack
	docker compose down

.PHONY: logs
logs: ## Follow server logs
	docker compose logs -f server

.PHONY: server-test
server-test: ## Run Go tests
	cd server && go test ./...

.PHONY: server-tidy
server-tidy: ## Sync go.mod/go.sum
	cd server && go mod tidy

.PHONY: server-run
server-run: ## Run the server locally against a local Postgres
	cd server && NUVA_ENV=development \
		NUVA_DATABASE_URL=postgres://nuva:nuva@localhost:5432/nuva?sslmode=disable \
		NUVA_JWT_SECRET=dev-secret-dev-secret-dev-secret-1 \
		NUVA_MEDIA_DIR=./.media \
		go run ./cmd/nuva-server

# ---- android --------------------------------------------------------------
.PHONY: apk-debug
apk-debug: ## Build a debug APK
	cd android && gradle :app:assembleDebug

.PHONY: apk-release
apk-release: ## Build a signed release APK (needs the keystore)
	cd android && gradle :app:assembleRelease

.PHONY: keystore
keystore: ## Create the release keystore (run once, then back it up)
	./scripts/make-keystore.sh

.PHONY: secrets
secrets: ## Generate .env secrets
	./scripts/gen-secrets.sh
