# E2E tests: build the HSS image, seed a SQLite database and run the
# Diameter test-agent suite against the containerized HSS.
#
#   make run-e2e-tests
#   make run-e2e-single TEST='CxE2eTest$$Sar'   # single class/nested class/method
TEST ?= CxE2eTest

.PHONY: run-e2e-tests run-e2e-single

run-e2e-tests:
	docker compose -f docker-compose.e2e.yml up --build --abort-on-container-exit --exit-code-from e2e-tests e2e-tests; \
	status=$$?; \
	docker compose -f docker-compose.e2e.yml down; \
	exit $$status

# Run a single E2E test class, nested class or method:
#   make run-e2e-single TEST='CxE2eTest$$Sar#registration_storesScscfAndReturnsUserData'
run-e2e-single:
	SINGLE_TEST='$(TEST)' docker compose -f docker-compose.e2e.yml up --build --abort-on-container-exit --exit-code-from e2e-tests e2e-tests; \
	status=$$?; \
	docker compose -f docker-compose.e2e.yml down; \
	exit $$status
