# Contributing to DataPoly

Thanks for your interest in contributing!

## Getting started

1. Fork the repository and create your branch from `main`.
2. Set up the environment:
   - JDK 8 (the project targets Java 8)
   - Maven 3.6+
   - Docker (for the database containers and the front-end build)

```bash
# compile
mvn compile

# run the full test suite
mvn -B -ntp test -pl datapoly-common,datapoly-template,datapoly-core,datapoly-executor,datapoly-gateway,datapoly-manager -am
```

## Front-end (built-in manager UI)

The built-in UI (`datapoly-manager-ui`) is a Vue 2 project that requires
Node 14. Because modern local Node versions cannot run its webpack 3 toolchain,
build it with the provided script (works on any host with Docker, no local
Node required):

```bash
sh ./build-ui.sh
```

This builds the UI inside a `node:14-alpine` container and syncs
`dist/index.html` + `dist/static/` into `datapoly-manager/src/main/resources/`
(wiping the previous copies first so stale hashed files do not linger).

Those synced files are build artifacts: they are **not committed to git**
(`.gitignore` excludes them). `build.sh` and `docker-maven-build.sh` run this
step automatically before `mvn package`, so the release tarball and the Docker
images always contain the UI. A bare `mvn package` does not build the UI — the
jar then contains no management UI (or whatever UI was last synced locally).
CI runs tests only and does not need the UI assets.

To rebuild the UI without the wrapper script (for UI-only development), the
raw container command is:

```bash
docker run --rm -v $PWD/datapoly-manager-ui:/app -w /app node:14-alpine \
  sh -c "npm config set registry https://registry.npmmirror.com && \
         npm install --no-audit --no-fund --no-package-lock --legacy-peer-deps && \
         npm run build"
```

If you build this way, copy `dist/index.html` and `dist/static/` into
`datapoly-manager/src/main/resources/` before running `mvn package`.

## Local smoke testing

The `.devcontainer/` directory contains a dev container (JDK 8 + Maven,
MySQL 8, PostgreSQL 14) for end-to-end smoke tests:

```bash
docker compose -f .devcontainer/docker-compose.yml up -d
```

See `AGENTS.md` (section "构建与测试") for how the three nodes
(manager / executor / gateway) are started inside the container.

## Pull request guidelines

- Keep changes focused; one PR per topic.
- Add or update tests for bug fixes and new features where practical.
- Make sure `mvn test` passes (see command above).
- Do not commit build artifacts, `node_modules`, IDE files or JDBC driver jars
  (the `drivers/` directory is local-only and git-ignored).
- Do not hardcode credentials, internal IPs or personal namespaces.
- Source files carry no license header by project convention; the repository
  LICENSE (BSD 3-Clause) governs. Do not re-introduce per-file headers.
- If you touch the vendored `io.modelcontextprotocol.*` sources, keep their
  upstream copyright headers untouched (see `NOTICE`).

## Reporting bugs

Open an issue using the bug report template. For security vulnerabilities,
follow [SECURITY.md](SECURITY.md) instead of opening a public issue.
