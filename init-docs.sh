#!/usr/bin/env bash
set -euo pipefail

mkdir -p docs/architecture docs/ai-notes

touch \
  docs/architecture/overview.en.md \
  docs/architecture/overview.es.md \
  docs/architecture/connection-flow.en.md \
  docs/architecture/connection-flow.es.md \
  docs/architecture/spectrum-audio-flow.en.md \
  docs/architecture/spectrum-audio-flow.es.md \
  docs/architecture/frontend-frequency-mapping.en.md \
  docs/architecture/frontend-frequency-mapping.es.md \
  docs/ai-notes/codex-findings-2026-04-11.en.md \
  docs/ai-notes/codex-findings-2026-04-11.es.md

echo "Estructura creada."
find docs -type f | sort
