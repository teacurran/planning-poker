# E2E Tests for Planning Poker

This directory contains Playwright end-to-end tests for the Planning Poker application.

## Test Suites

### Authentication Tests (`auth.spec.ts`)
- OAuth login flow with Google/Microsoft
- Token storage and refresh
- Logout functionality
- Protected route access

### Voting Flow Tests (`voting.spec.ts`)
- Single-user voting: join room, cast vote, see UI update
- Host reveal flow: trigger reveal, see animation and statistics
- Multi-user synchronization: two users voting simultaneously
- WebSocket reconnection and state preservation
- Round reset functionality
- Host controls visibility based on role

## Prerequisites

### Backend Server Required

**IMPORTANT:** The voting tests (`voting.spec.ts`) require the backend WebSocket server to be running.

Before running voting tests, start the backend server:

```bash
cd backend
mvn quarkus:dev
```

The backend should be accessible at `http://localhost:8080`.

### Test Database Setup

The voting tests expect a room to exist in the database with ID: `e2e-test-room`.

You have two options:

1. **Manual Room Creation via API:**
   ```bash
   curl -X POST http://localhost:8080/api/v1/rooms \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer <valid-token>" \
     -d '{
       "roomId": "e2e-test-room",
       "title": "E2E Test Room",
       "privacyMode": "PUBLIC",
       "config": {
         "votingSystem": "FIBONACCI",
         "allowRevote": true,
         "autoRevealVotes": false,
         "deckType": "fibonacci"
       }
     }'
   ```

2. **Database Seeding Script:**
   The backend integration tests may already create test rooms. Check if `e2e-test-room` exists in your test database.

## Running Tests

### Run All E2E Tests
```bash
npm run test:e2e
```

### Run Specific Test File
```bash
npx playwright test auth.spec.ts
npx playwright test voting.spec.ts
```

### Run Tests with UI (Interactive Mode)
```bash
npm run test:e2e:ui
```

### Run Tests in Headed Mode (See Browser)
```bash
npm run test:e2e:headed
```

### Run Specific Test by Name
```bash
npx playwright test -g "should join room, cast vote"
```

## Test Architecture

### Fixtures
Test fixtures are located in `frontend/e2e/fixtures/`:

- `mockOAuthResponse.ts`: Mock data for authentication tests
- `mockVotingData.ts`: Mock data and helpers for voting tests

### Route Mocking Strategy

- **Auth tests:** All API requests are mocked (no backend required)
- **Voting tests:** API requests are mocked, but WebSocket connections use the real backend server

This hybrid approach ensures:
- Deterministic test data for API responses
- Real integration testing for WebSocket bidirectional communication
- Faster test execution (no external OAuth providers)

## Test Coverage

### Acceptance Criteria Met

✅ `npm run test:e2e` executes voting tests
✅ Card selection updates UI instantly (optimistic update)
✅ Reveal animation plays after host reveal
✅ Multi-user test verifies synchronization (2 browser contexts)
✅ Reconnection test verifies WebSocket resilience
✅ Tests can run in CI pipeline (with backend server startup)

### Test Scenarios

#### Single User Flow
1. User joins room via WebSocket
2. Connection status shows "Connected"
3. Participant list displays user
4. Host starts new round
5. User selects voting card
6. UI shows "Vote Cast!" immediately (optimistic)
7. Server confirms vote (participant list checkmark)

#### Multi-User Flow
1. Two browser contexts (Alice as HOST, Bob as VOTER)
2. Both join same room
3. Both see each other in participant list
4. Alice starts round
5. Both cast different votes (5 and 8)
6. Alice reveals votes
7. Both see statistics (average: 6.5, median: 6.5)
8. Both see all votes displayed

#### Reconnection Flow
1. User joins and casts vote
2. WebSocket connection forcefully closed
3. UI shows "Disconnected - Reconnecting..."
4. Connection automatically re-established
5. Vote state preserved (still shows "Vote Cast!")

#### Round Reset Flow
1. Host starts round, casts vote, reveals
2. Host clicks "Reset" button
3. UI returns to "Waiting for Round to Start"
4. Votes cleared, ready for new round

## Debugging Tests

### Enable Browser Console Logs
```typescript
page.on('console', msg => console.log('[BROWSER]', msg.text()));
```

### Take Screenshots on Failure
Playwright automatically takes screenshots on failure. Find them in `test-results/` directory.

### Trace Viewer (for debugging)
```bash
npx playwright test --trace on
npx playwright show-trace trace.zip
```

## CI/CD Integration

To run E2E tests in CI pipeline, ensure:

1. Backend server starts before frontend tests:
   ```yaml
   - name: Start Backend
     run: |
       cd backend
       mvn quarkus:dev &
       # Wait for backend to be ready
       timeout 60 bash -c 'until curl -f http://localhost:8080/q/health; do sleep 2; done'

   - name: Run E2E Tests
     run: |
       cd frontend
       npm run test:e2e
   ```

2. Test database is seeded with required data (e2e-test-room)

3. Vite dev server auto-starts via `playwright.config.ts` webServer config (already configured)

## Troubleshooting

### Tests Fail with "WebSocket connection failed"
- Ensure backend is running on `localhost:8080`
- Check backend logs for WebSocket handshake errors
- Verify JWT token validation is not blocking test users

### Tests Timeout
- Increase timeout in `playwright.config.ts` (default: 30s)
- Check if backend is slow to respond
- Verify database connection is working

### Flaky Tests
- Check for race conditions in WebSocket event handlers
- Ensure proper use of `await` for all async operations
- Use Playwright's auto-waiting assertions (avoid manual `setTimeout`)

### Room Not Found (404)
- Verify `e2e-test-room` exists in database
- Check backend logs for room creation errors
- Manually create room using API call (see Prerequisites)

## Future Improvements

- [ ] Add visual regression testing (screenshot comparison)
- [ ] Test error scenarios (network failures, invalid tokens)
- [ ] Test accessibility (keyboard navigation, screen readers)
- [ ] Test responsive design (mobile, tablet viewports)
- [ ] Add performance metrics (WebSocket latency, render time)
- [ ] Test vote retraction (if allowRevote is enabled)
- [ ] Test observer role (cannot vote, can only watch)
