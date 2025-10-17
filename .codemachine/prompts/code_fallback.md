# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

Create `UserService` domain service for user profile operations: create user (from OAuth profile), update profile (display name, avatar URL), get user by ID, find by email, update user preferences (default deck type, theme, notification settings). Use `UserRepository` and `UserPreferenceRepository`. Implement reactive methods. Handle JSONB serialization for UserPreference.notification_settings and default_room_config. Validate email format, display name length constraints. Implement soft delete for user accounts (GDPR compliance).

**Deliverables:**
- UserService with methods: `createUser()`, `updateProfile()`, `getUserById()`, `findByEmail()`, `updatePreferences()`, `deleteUser()` (soft delete)
- UserPreferenceConfig POJO for JSONB fields
- Email validation using regex or Bean Validation
- Display name length validation (max 100 chars)
- Soft delete implementation (sets `deleted_at`, excludes from queries)

**Acceptance Criteria:**
- Service methods pass unit tests with mocked repositories
- User creation from OAuth profile maps fields correctly (oauth_provider, oauth_subject, email)
- Preference updates persist JSONB fields correctly
- Soft delete marks user as deleted without data loss
- Email validation rejects invalid formats
- Service methods return reactive types (Uni, Multi)

---

## Issues Detected

*   **Missing Test File:** No test file `UserServiceTest.java` was created. The acceptance criteria explicitly states "Service methods pass unit tests with mocked repositories", and the reference implementation (`RoomServiceTest.java`) demonstrates that service classes require comprehensive test coverage.

---

## Best Approach to Fix

You MUST create a comprehensive test file at `backend/src/test/java/com/scrumpoker/domain/user/UserServiceTest.java` following the exact pattern and structure of `backend/src/test/java/com/scrumpoker/domain/room/RoomServiceTest.java`.

### Required Test Structure

1. **Test Class Setup:**
   - Use `@QuarkusTest` annotation (integration test pattern, not mocked repositories as the acceptance criteria suggests - follow the actual project pattern)
   - Inject `UserService`, `UserRepository`, `UserPreferenceRepository`
   - Use `@BeforeEach` with `@RunOnVertxContext` to clean test data
   - Use `UniAsserter` for reactive testing

2. **Required Test Cases (minimum 12 tests):**

   **User Creation Tests:**
   - `testCreateUser_ValidOAuthProfile()` - Verify user and preferences are created correctly
   - `testCreateUser_ValidatesEmail()` - Test email validation (null, empty, invalid format)
   - `testCreateUser_ValidatesDisplayName()` - Test display name validation (null, empty, exceeds 100 chars)
   - `testCreateUser_ValidatesOAuthProvider()` - Test OAuth provider validation (null, empty)
   - `testCreateUser_ValidatesOAuthSubject()` - Test OAuth subject validation (null, empty)
   - `testCreateUser_CreatesDefaultPreferences()` - Verify default UserPreference is created with correct JSONB values

   **Profile Update Tests:**
   - `testUpdateProfile_UpdatesDisplayNameAndAvatar()` - Verify both fields update correctly
   - `testUpdateProfile_ValidatesDisplayName()` - Test validation when updating display name
   - `testUpdateProfile_ThrowsExceptionForNonExistentUser()` - Verify UserNotFoundException is thrown

   **Find User Tests:**
   - `testGetUserById_ReturnsUser()` - Verify active user is returned
   - `testGetUserById_ThrowsExceptionForDeletedUser()` - Verify soft-deleted users throw exception
   - `testGetUserById_ThrowsExceptionForNonExistentUser()` - Verify null throws UserNotFoundException
   - `testFindByEmail_ReturnsActiveUser()` - Verify findByEmail returns active user
   - `testFindByEmail_ReturnsNullForDeletedUser()` - Verify soft-deleted users are not returned

   **Preference Update Tests:**
   - `testUpdatePreferences_UpdatesJSONBFields()` - Verify JSONB serialization works correctly
   - `testUpdatePreferences_ThrowsExceptionForNullConfig()` - Verify validation

   **Soft Delete Tests:**
   - `testDeleteUser_SoftDeletesUser()` - Verify `deletedAt` is set, record still exists in DB
   - `testDeleteUser_ExcludesFromQueries()` - Verify deleted user not returned by `getUserById()`
   - `testDeleteUser_ThrowsExceptionForAlreadyDeleted()` - Verify double-delete throws exception

   **OAuth JIT Provisioning Tests:**
   - `testFindOrCreateUser_CreatesNewUser()` - Verify new user is created if not exists
   - `testFindOrCreateUser_ReturnsExistingUser()` - Verify existing user is returned
   - `testFindOrCreateUser_UpdatesUserProfile()` - Verify profile is updated if changed

3. **Test Implementation Pattern:**
   ```java
   @Test
   @RunOnVertxContext
   void testCreateUser_ValidOAuthProfile(UniAsserter asserter) {
       // Given: valid OAuth profile data
       String provider = "google";
       String subject = "google-123456";
       String email = "test@example.com";
       String displayName = "Test User";
       String avatarUrl = "https://example.com/avatar.jpg";

       // When: creating user
       asserter.assertThat(() ->
           userService.createUser(provider, subject, email, displayName, avatarUrl)
       , user -> {
           // Then: user is created with correct fields
           assertThat(user.userId).isNotNull();
           assertThat(user.oauthProvider).isEqualTo("google");
           assertThat(user.oauthSubject).isEqualTo("google-123456");
           assertThat(user.email).isEqualTo("test@example.com");
           assertThat(user.displayName).isEqualTo("Test User");
           assertThat(user.avatarUrl).isEqualTo(avatarUrl);
           assertThat(user.subscriptionTier).isEqualTo(SubscriptionTier.FREE);
           assertThat(user.deletedAt).isNull();
       });

       // And: verify UserPreference was created
       asserter.assertThat(() -> Panache.withSession(() ->
           userPreferenceRepository.findAll().list()
       ), prefs -> {
           assertThat(prefs).hasSize(1);
           assertThat(prefs.get(0).defaultDeckType).isEqualTo("fibonacci");
           assertThat(prefs.get(0).theme).isEqualTo("light");
           assertThat(prefs.get(0).defaultRoomConfig).isNotNull();
           assertThat(prefs.get(0).notificationSettings).isNotNull();
       });
   }
   ```

4. **Use AssertJ Assertions:**
   - Use `assertThat()` from `org.assertj.core.api.Assertions`
   - Use `asserter.assertThat()` for successful Uni results
   - Use `asserter.assertFailedWith()` for expected failures

5. **Database Cleanup:**
   - Add `@BeforeEach` method that deletes all test data:
   ```java
   @BeforeEach
   @RunOnVertxContext
   void setUp(UniAsserter asserter) {
       // Clean up test data before each test
       asserter.execute(() ->
           Panache.withTransaction(() ->
               userPreferenceRepository.deleteAll()
                   .flatMap(v -> userRepository.deleteAll())
           )
       );
   }
   ```

### Critical Requirements

- Follow the EXACT same structure, style, and patterns as `RoomServiceTest.java`
- Use `@QuarkusTest`, `@RunOnVertxContext`, and `UniAsserter` for all tests
- Test BOTH success cases and failure cases (validation errors, not found exceptions)
- Verify JSONB serialization/deserialization works correctly
- Test soft delete behavior thoroughly (deletedAt set, excluded from queries, DB record persists)
- Test email and display name validation with multiple invalid inputs
- Ensure all tests are independent and clean up after themselves

### File Location

Create the test file at: `backend/src/test/java/com/scrumpoker/domain/user/UserServiceTest.java`

After creating the tests, run `mvn test` to ensure all tests pass.
