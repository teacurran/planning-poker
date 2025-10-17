# Code Refinement Task

The previous code submission did not pass verification. The RoomController implementation exists and is correct, but there is a **critical project structure issue** preventing the endpoints from being accessible.

---

## Original Task Description

Implement JAX-RS REST controllers for room CRUD operations following OpenAPI specification from I2.T1. Create `RoomController` with endpoints: `POST /api/v1/rooms` (create room), `GET /api/v1/rooms/{roomId}` (get room), `PUT /api/v1/rooms/{roomId}/config` (update config), `DELETE /api/v1/rooms/{roomId}` (delete), `GET /api/v1/users/{userId}/rooms` (list user's rooms). Inject `RoomService`, convert entities to DTOs, handle exceptions (404 for room not found, 400 for validation errors). Add `@RolesAllowed` annotations for authorization (room owner can delete, authenticated users can create). Return reactive `Uni<>` types for non-blocking I/O.

---

## Issues Detected

*   **Critical Project Structure Issue:** The RoomController and all related domain/service classes are located in `backend/src/main/java/com/scrumpoker/` but the Maven build at the project root is compiling code from `src/main/java/com/terrencecurran/planningpoker/` (legacy codebase). When starting Quarkus with `./mvnw quarkus:dev`, the server runs with the OLD endpoints (`/api/rooms`) instead of the new endpoints (`/api/v1/rooms`).

*   **Verification Test Failure:** When testing `POST http://localhost:8080/api/v1/rooms`, the server returns 404 and shows only legacy endpoints. The new RoomController at `backend/src/main/java/com/scrumpoker/api/rest/RoomController.java` is NOT being compiled or loaded.

*   **Test Compilation Errors:** The legacy tests in `src/test/java/com/terrencecurran/planningpoker/StartupVerificationTest.java` fail to compile due to missing `isModerator` field, preventing `mvnw quarkus:dev` from starting successfully (had to move tests to /tmp to proceed).

*   **Misaligned Source Directories:** There are TWO separate codebases in this project:
    - **Legacy:** `src/main/java/com/terrencecurran/planningpoker/` (old package, old endpoints)
    - **New:** `backend/src/main/java/com/scrumpoker/` (new package, new endpoints matching task requirements)

---

## Best Approach to Fix

You MUST resolve the project structure mismatch. There are two possible approaches:

### **Option A (Recommended): Configure pom.xml to use backend/ directory**

1. Edit the root `pom.xml` file to change the source directory from `src/main/java` to `backend/src/main/java`:
   ```xml
   <build>
       <sourceDirectory>backend/src/main/java</sourceDirectory>
       <resources>
           <resource>
               <directory>backend/src/main/resources</directory>
           </resource>
       </resources>
       <testSourceDirectory>backend/src/test/java</testSourceDirectory>
       <testResources>
           <testResource>
               <directory>backend/src/test/resources</directory>
           </testResource>
       </testResources>
   </build>
   ```

2. Verify the configuration works by running:
   ```bash
   ./mvnw clean compile
   ```

3. Check that `target/classes/com/scrumpoker/api/rest/RoomController.class` is compiled.

4. Start Quarkus and verify endpoints:
   ```bash
   ./mvnw quarkus:dev -DskipTests
   ```

5. Test that `POST http://localhost:8080/api/v1/rooms` returns 201 Created (not 404).

### **Option B (Alternative): Move new code from backend/ to src/**

If modifying pom.xml is not desired, move all files from `backend/src/main/java/com/scrumpoker/` to `src/main/java/com/scrumpoker/` and update imports/references accordingly. This is more invasive and error-prone.

---

## Additional Fixes Required

1. **Fix or Remove Legacy Tests:** The tests in `src/test/java/com/terrencecurran/planningpoker/StartupVerificationTest.java` reference a non-existent `isModerator` field. Either:
   - Delete these legacy tests (they're for the old codebase)
   - OR move them to `/tmp` or a separate directory
   - OR fix the `isModerator` references if needed for legacy compatibility

2. **Verify Endpoints After Fix:** Once the project structure is corrected, run these curl commands to verify all 5 endpoints work:
   ```bash
   # Create room
   curl -X POST http://localhost:8080/api/v1/rooms \
     -H "Content-Type: application/json" \
     -d '{"title": "Test Room", "privacyMode": "PUBLIC"}'

   # Should return 201 Created with roomId

   # Get room (use roomId from above)
   curl http://localhost:8080/api/v1/rooms/{roomId}

   # Update config
   curl -X PUT http://localhost:8080/api/v1/rooms/{roomId}/config \
     -H "Content-Type: application/json" \
     -d '{"title": "Updated Title"}'

   # Delete room
   curl -X DELETE http://localhost:8080/api/v1/rooms/{roomId}

   # List user rooms
   curl "http://localhost:8080/api/v1/users/00000000-0000-0000-0000-000000000000/rooms?page=0&size=10"
   ```

3. **Database Configuration:** Ensure PostgreSQL is running on port 5445 (or update `application.properties` to match the actual port). Current `.env` file has `POSTGRES_PORT=5445`.

---

## Summary

The RoomController implementation code is correct and complete, but it's in the wrong source directory (`backend/` instead of `src/`). The Maven build must be configured to compile from `backend/src/main/java` or the code must be moved to `src/main/java`. Choose **Option A** (modify pom.xml) as it's cleaner and preserves the intended backend/ directory structure.
