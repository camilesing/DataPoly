# Security Policy

## Reporting a vulnerability

We take security issues seriously and appreciate responsible disclosure.

- **Preferred:** use GitHub's private vulnerability reporting
  ("Report a vulnerability" under the Security tab of the repository).
- **Alternate:** email camilesing@gmail.com with details and reproduction steps.

Please do not open public issues for suspected vulnerabilities. We will
respond as soon as possible and credit reporters after a fix is released.

## Supported versions

Only the latest release line receives security fixes.

## Deployment security essentials

DataPoly ships with demo defaults that **must** be changed before any
production or internet-facing deployment:

- Change the seed admin account (`admin/123456`) and the demo app client
  credentials (`test/test`) on first login.
- Set `DATAPOLY_ADMIN_PASSWORD`, `DATAPOLY_REDIS_PASSWORD`, and especially
  `DATAPOLY_DS_AES_KEY` via environment variables — the built-in fallback
  AES key is public knowledge once the source is open; rotate it and re-enter
  datasource credentials so they are encrypted under your own key.
- Only the gateway (default port 8091) should be exposed; manager (8090) and
  executor (8092) must not be published. See the network segmentation notes in
  `AGENTS.md`.
- Review `docker-compose.yml` defaults (database passwords) for your
  environment.
