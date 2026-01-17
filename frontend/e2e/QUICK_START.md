# E2E Tests - Quick Start Guide

**Task I4.T8:** E2E tests for voting flow

## TL;DR - Run Tests Now

```bash
# Terminal 1: Start backend
cd backend
mvn quarkus:dev

# Terminal 2: Run tests
cd frontend
npm run test:e2e
```

Expected result: **7 tests pass** ✅

---

## Prerequisites Checklist

Before running tests, ensure:

- [ ] **Backend running** at http://localhost:8080
  - Verify: `curl http://localhost:8080/q/health`
  - Should return: `{"status": "UP"}`

- [ ] **Test room exists** in database
  - Room ID: `e2e-test-room`
  - If missing, see "Create Test Room" section below

- [ ] **Playwright browsers installed**
  - Run: `npx playwright install chromium`

---

## Create Test Room (if needed)

If tests fail with "Room not found", create the test room:

```bash
# Check your backend API documentation for room creation
# You may need a valid JWT token

curl -X POST http://localhost:8080/api/v1/rooms \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE" \
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

Alternative: Check if your backend has database seeding scripts or integration test setup that creates test rooms automatically.

---

## Test Commands

### Run all tests (headless)
```bash
npm run test:e2e
```

### Run with visible browser (debugging)
```bash
npm run test:e2e:headed
```

### Run with interactive UI
```bash
npm run test:e2e:ui
```

### Run specific test
```bash
npx playwright test -g "should join room"
```

---

## What Tests Cover

✅ **7 test scenarios** covering:

1. Join room, cast vote, see UI update
2. Reveal votes with statistics
3. Multi-user synchronization (2 browser contexts)
4. WebSocket reconnection and state preservation
5. Round reset
6. Role-based controls (host vs voter)
7. Connection status indicators

---

## Troubleshooting

### "WebSocket connection failed"
→ Backend not running. Start with: `cd backend && mvn quarkus:dev`

### "Room not found (404)"
→ Test room doesn't exist. Create it (see "Create Test Room" above)

### "Test timeout"
→ Backend slow or database issue. Check backend logs.

### Tests flaky (sometimes pass, sometimes fail)
→ Increase timeouts or check backend performance

---

## Success Criteria

✅ All 7 tests pass
✅ No WebSocket connection errors
✅ Test execution time < 2 minutes
✅ No screenshots in `test-results/` directory (only created on failure)

---

## Full Documentation

For comprehensive details, see:
- **TEST_VERIFICATION_REPORT.md** - Complete test verification report
- **README.md** - Full E2E testing documentation
- **voting.spec.ts** - Test implementation (337 lines, 7 tests)

---

**Questions?** Check the troubleshooting sections in README.md or TEST_VERIFICATION_REPORT.md.
