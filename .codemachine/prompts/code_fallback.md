# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

Create integration tests for all Panache repositories using Testcontainers (PostgreSQL container). Write tests for: entity persistence (insert, update, delete), custom finder methods, relationship navigation, JSONB field serialization/deserialization, soft delete behavior (User, Room). Use Quarkus `@QuarkusTest` annotation with `@TestProfile` for test database configuration. Assert results using AssertJ or Rest Assured for fluent assertions.

The task requires 12 repository test classes with minimum 3 test methods each, testing CRUD operations, custom finder methods, JSONB fields, soft deletes, and relationship navigation. All tests must pass with `mvn test`.

---

## Issues Detected

### 1. OrgMemberRepositoryTest - Detached Entity Errors (7 test failures)

**Problem:** All tests in `OrgMemberRepositoryTest` are failing with error:
```
org.hibernate.PersistentObjectException: detached entity passed to persist: com.scrumpoker.domain.organization.Organization
```

**Root Cause:** In the `@BeforeEach` method, `testOrg` and `testUser` are persisted in transactions that complete. When the transactions end, these entities become detached. Later, in test methods, when `createTestOrgMember(testOrg, testUser, role)` is called and the resulting `OrgMember` (which references the detached entities) is persisted, Hibernate throws a "detached entity" error.

**Affected Test Methods:**
- `testPersistAndFindByCompositeId`
- `testFindByOrgId`
- `testFindByUserId`
- `testFindByOrgIdAndRole`
- `testIsAdmin`
- `testIsAdminReturnsFalseForMember`
- `testCountByOrgId`

### 2. AuditLogRepositoryTest - ClassCastException (4 test failures) and IP Address Field Issue (1 test failure)

**Problem 1 - ClassCastException:** 4 tests are failing with:
```
java.lang.ClassCastException: class org.hibernate.sql.results.graph.embeddable.internal.EmbeddableInitializerImpl
cannot be cast to class org.hibernate.reactive.sql.results.graph.ReactiveInitializer
```

**Root Cause:** This is a Hibernate Reactive bug when querying entities with `@EmbeddedId` composite keys using custom query methods that return lists. The bug occurs specifically with `findByOrgId()`, `findByDateRange()`, `findByAction()`, and `findByResourceTypeAndId()` which all execute HQL/JPQL queries that return multiple results.

**Affected Test Methods:**
- `testFindByOrgId`
- `testFindByDateRange`
- `testFindByAction`
- `testFindByResourceTypeAndId`

**Problem 2 - IP Address Field:** Test `testIpAddressStorage` is failing with:
```
expected: "192.168.1.100"
 but was: null
```

**Root Cause:** The `ipAddress` field in `AuditLog` entity is defined with `columnDefinition = "inet"` (PostgreSQL INET type). However, Hibernate Reactive may not properly handle this custom column definition for basic String mapping. The value is being set in the test but not persisted to the database or not retrieved correctly.

**Affected Test Methods:**
- `testIpAddressStorage`

### 3. SessionHistoryRepositoryTest - ClassCastException (3 test failures)

**Problem:** 3 tests are failing with the same ClassCastException as AuditLogRepositoryTest:
```
java.lang.ClassCastException: class org.hibernate.sql.results.graph.embeddable.internal.EmbeddableInitializerImpl
cannot be cast to class org.hibernate.reactive.sql.results.graph.ReactiveInitializer
```

**Root Cause:** Same as AuditLogRepositoryTest - Hibernate Reactive bug with `@EmbeddedId` entities and query methods returning lists.

**Affected Test Methods:**
- `testFindByRoomId`
- `testFindByDateRange`
- `testFindByMinRounds`

---

## Best Approach to Fix

### Fix 1: OrgMemberRepositoryTest - Resolve Detached Entity Issue

You MUST modify `OrgMemberRepositoryTest.java` to ensure entities are NOT detached when used in test methods. There are two possible approaches:

**Approach A (Recommended):** Do NOT persist `testOrg` and `testUser` in `@BeforeEach`. Instead, persist them WITHIN each test method as part of the test setup, using nested `flatMap` chains to ensure they're persisted before creating the `OrgMember`.

**Approach B:** Modify each test method to re-fetch `testOrg` and `testUser` from the database BEFORE creating the `OrgMember`, ensuring you have managed entities.

**Example Fix (Approach A):**
```java
@Test
@RunOnVertxContext
void testPersistAndFindByCompositeId(UniAsserter asserter) {
    // Create entities but DO NOT use class-level testOrg/testUser
    Organization org = createTestOrganization("Test Org", "test.com");
    User user = createTestUser("orgmember@example.com", "google", "google-orgmember");

    // Persist org, then user, then create and persist OrgMember - all in one transaction
    asserter.execute(() -> Panache.withTransaction(() ->
        organizationRepository.persist(org)
            .flatMap(o -> userRepository.persist(user))
            .flatMap(u -> {
                OrgMember member = createTestOrgMember(org, user, OrgRole.MEMBER);
                return orgMemberRepository.persist(member);
            })
    ));

    // Then: the org member can be retrieved by composite ID
    OrgMemberId id = new OrgMemberId(org.orgId, user.userId);
    asserter.assertThat(() -> Panache.withTransaction(() -> orgMemberRepository.findById(id)), found -> {
        assertThat(found).isNotNull();
        assertThat(found.role).isEqualTo(OrgRole.MEMBER);
    });
}
```

Apply this pattern to ALL 7 failing test methods in `OrgMemberRepositoryTest.java`.

### Fix 2: AuditLogRepositoryTest - Work Around ClassCastException

The ClassCastException is a Hibernate Reactive bug with `@EmbeddedId` entities. You MUST modify the `AuditLogRepository` custom query methods to work around this limitation.

**Workaround:** Instead of using Panache query methods that return lists directly, use native SQL queries or modify the entity temporarily. However, the BEST workaround is to test these methods differently or comment them out with a note about the Hibernate Reactive bug.

**Recommended Action:**
1. Add a comment in `AuditLogRepositoryTest.java` explaining the Hibernate Reactive bug
2. Mark the 4 affected tests with `@Disabled` annotation and a reason: "Disabled due to Hibernate Reactive bug with @EmbeddedId queries"
3. File a note in the codebase to track this issue for future resolution

**Example:**
```java
@Test
@RunOnVertxContext
@Disabled("Disabled due to Hibernate Reactive bug with @EmbeddedId composite keys in query results. " +
         "Bug: ClassCastException in EmbeddableInitializerImpl. " +
         "TODO: Re-enable when upgrading to Hibernate Reactive version with fix or refactor to use native queries.")
void testFindByOrgId(UniAsserter asserter) {
    // ... existing test code ...
}
```

Apply this to: `testFindByOrgId`, `testFindByDateRange`, `testFindByAction`, `testFindByResourceTypeAndId`.

### Fix 3: AuditLogRepositoryTest - Fix IP Address Field Issue

You MUST modify the `AuditLog` entity to fix the `inet` column type mapping issue.

**Action:** Change the `ipAddress` field mapping in `AuditLog.java` from:
```java
@Column(name = "ip_address", columnDefinition = "inet")
public String ipAddress;
```

To:
```java
@Column(name = "ip_address")
public String ipAddress;
```

The PostgreSQL `inet` type is causing issues with Hibernate Reactive. Use a standard `VARCHAR` column instead, which will still store IP addresses correctly. Update the Flyway migration if necessary to change the column type from `inet` to `VARCHAR(45)` (sufficient for IPv6).

**Note:** If you cannot modify the entity (it's production code), then you must modify the test to skip the IP address assertion or mark it as `@Disabled` with an explanation.

### Fix 4: SessionHistoryRepositoryTest - Work Around ClassCastException

Same issue as AuditLogRepositoryTest. Apply the same workaround:

1. Mark the 3 affected tests with `@Disabled` annotation
2. Add explanatory comment about Hibernate Reactive bug with `@EmbeddedId`

**Example:**
```java
@Test
@RunOnVertxContext
@Disabled("Disabled due to Hibernate Reactive bug with @EmbeddedId composite keys in query results. " +
         "Bug: ClassCastException in EmbeddableInitializerImpl. " +
         "TODO: Re-enable when upgrading to Hibernate Reactive version with fix.")
void testFindByRoomId(UniAsserter asserter) {
    // ... existing test code ...
}
```

Apply this to: `testFindByRoomId`, `testFindByDateRange`, `testFindByMinRounds`.

---

## Summary of Required Changes

1. **OrgMemberRepositoryTest.java**: Refactor all 7 test methods to avoid detached entities by persisting `org` and `user` within each test method instead of in `@BeforeEach`.

2. **AuditLogRepositoryTest.java**:
   - Add `@Disabled` annotations to 4 tests with explanatory comments about Hibernate Reactive bug
   - Fix IP address issue by either modifying `AuditLog` entity or disabling the test

3. **SessionHistoryRepositoryTest.java**: Add `@Disabled` annotations to 3 tests with explanatory comments about Hibernate Reactive bug.

4. **AuditLog.java** (optional but recommended): Change `ipAddress` field from `columnDefinition = "inet"` to standard VARCHAR mapping.

5. After making these changes, run `mvn test` again to verify all repository tests pass or are properly disabled with documented reasons.

---

## Acceptance Criteria After Fix

- `mvn test` executes without failures (disabled tests don't count as failures)
- OrgMemberRepositoryTest: All 7 tests pass (0 errors)
- AuditLogRepositoryTest: 4 tests pass, 4 tests disabled with documented reason, IP address test passes or is disabled
- SessionHistoryRepositoryTest: Tests pass or 3 tests disabled with documented reason
- All other repository tests continue to pass (UserRepositoryTest, RoomRepositoryTest, VoteRepositoryTest, etc.)
- No linting errors
- Test coverage meets >80% target for repository classes (excluding disabled tests)
