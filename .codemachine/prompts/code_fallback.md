# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

Implement JAX-RS REST controllers for room CRUD operations following OpenAPI specification from I2.T1. Create `RoomController` with endpoints: `POST /api/v1/rooms` (create room), `GET /api/v1/rooms/{roomId}` (get room), `PUT /api/v1/rooms/{roomId}/config` (update config), `DELETE /api/v1/rooms/{roomId}` (delete), `GET /api/v1/users/{userId}/rooms` (list user's rooms). Inject `RoomService`, convert entities to DTOs, handle exceptions (404 for room not found, 400 for validation errors). Add `@RolesAllowed` annotations for authorization (room owner can delete, authenticated users can create). Return reactive `Uni<>` types for non-blocking I/O.

---

## Issues Detected

*   **Incomplete Implementation:** The `PUT /api/v1/rooms/{roomId}/config` endpoint accepts an `UpdateRoomConfigRequest` DTO that includes an optional `privacyMode` field (line 17 in UpdateRoomConfigRequest.java), but the controller's `updateRoomConfig` method (lines 120-150 in RoomController.java) does not handle this field. The controller only processes `title` and `config` fields, ignoring `privacyMode` completely.

*   **Missing Service Method:** The `RoomService` class does not have a method to update the privacy mode of a room. The service only provides `updateRoomConfig()` and `updateRoomTitle()` methods, but no `updatePrivacyMode()` method.

---

## Best Approach to Fix

You MUST modify the `RoomController.java` file to handle the `privacyMode` field from `UpdateRoomConfigRequest`:

1. In the `updateRoomConfig` method (starting at line 120), after checking for `request.title` and `request.config`, add a conditional check for `request.privacyMode`.

2. Since `RoomService` doesn't have an `updatePrivacyMode` method yet, you have two options:
   - **Option A (Recommended):** Add a new method `updatePrivacyMode(String roomId, PrivacyMode privacyMode)` to `RoomService.java` and use it in the controller.
   - **Option B:** For now, add a TODO comment in the controller indicating that privacyMode updates are not yet supported, and throw an appropriate exception (e.g., `new UnsupportedOperationException("Privacy mode updates not yet implemented")`) if the field is provided.

3. Choose **Option A** if you want a complete implementation. This means:
   - Add a method `public Uni<Room> updatePrivacyMode(String roomId, PrivacyMode privacyMode)` to `RoomService.java` that follows the same pattern as `updateRoomTitle()` and `updateRoomConfig()`.
   - In the controller's `updateRoomConfig` method, add:
     ```java
     if (request.privacyMode != null && !request.privacyMode.isEmpty()) {
         PrivacyMode newPrivacyMode = PrivacyMode.valueOf(request.privacyMode.toUpperCase());
         updateChain = updateChain.flatMap(room ->
             roomService.updatePrivacyMode(roomId, newPrivacyMode)
         );
     }
     ```

4. If you choose Option B (minimal fix), add this after line 143:
   ```java
   // TODO: Privacy mode updates not yet implemented in RoomService
   if (request.privacyMode != null && !request.privacyMode.isEmpty()) {
       throw new UnsupportedOperationException("Privacy mode updates will be supported in a future iteration");
   }
   ```

**Recommendation:** Implement **Option A** to provide a complete solution that matches the DTO contract.
