# Project: {{PROJECT_NAME}}

## Overview
{{PROJECT_DESCRIPTION}}

## Tech Stack
- Monorepo: pnpm workspace / turborepo
- Frontend: [TBD]
- Backend: [TBD]
- Common: TypeScript, ESLint, Prettier

## Common Commands
- Build: `pnpm build`
- Test: `pnpm test`
- Lint: `pnpm lint`
- Format: `pnpm format`
- Typecheck: `pnpm typecheck`

## Code Style
- **REQUIRED**: Keep functions small (50 lines max)
- **REQUIRED**: Keep files under 300 lines
- Naming: camelCase (variables/functions), PascalCase (types/classes/components)
- Prefer named exports

## Workflow
1. Read existing code before making changes
2. Write tests first, then implement (TDD)
3. Typecheck, lint, and test must all pass before commit
4. Use Conventional Commits format

## Directory Structure
- `packages/web/` - Frontend
- `packages/api/` - Backend API
- `packages/shared/` - Shared libraries
- `docs/plans/` - Design documents
