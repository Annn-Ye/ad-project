# Candidate messaging, Community UI, and Agent stability fixes

## Completed

- Added an idempotent Candidate-to-Recruiter inquiry flow for active public jobs. The Android Job detail action now opens the resulting conversation directly instead of only switching to the Messages list.
- Kept a confirmed Agent resume-age change synchronized with the Candidate profile age in the same transaction.
- Added Android Community feed image thumbnails and fixed Community direct-message navigation so one Back action returns to the post without reopening the conversation.
- Fixed the Admin company action layout and constrained recruiter interview modal form controls within their desktop grid cells.
- Added a narrow deterministic Agent-planner path for explicit age changes, simple resume-section reads, and incomplete generic resume edits. Existing preview and explicit confirmation safeguards remain unchanged.

## Changed modules

- Backend conversations, resume/profile synchronization, integration tests, and OpenAPI contract.
- Android Candidate jobs, conversations, Community feed, and navigation.
- Web global layout styles.
- Agent planner and planner tests.

## API and database

- Added `POST /api/v1/candidate/conversations/job/{jobId}`. It requires a Candidate session and returns the existing or newly created inquiry conversation.
- Added `CANDIDATE_INQUIRY` to the documented conversation types.
- No database migration is required: the existing conversation unique key already makes the Candidate/job/recruiter combination idempotent.

## Verification

- Backend: `ConversationIntegrationTest` and `AgentRunIntegrationTest` — 32 passed.
- Android: `:app:compileDebugKotlin` — passed.
- Web: typecheck and production build — passed.
- Agent service: planner tests — 13 passed (one existing FastAPI/TestClient deprecation warning only).

## Limits and next safe step

- Community private messages remain in their existing Community-specific data model; this change fixes Back navigation only. Merging them into ordinary application conversations would need a separate schema/API design decision.
- CSS behavior was build-checked but should receive a short browser visual check at the recruiter interview modal and Admin company review panel.
