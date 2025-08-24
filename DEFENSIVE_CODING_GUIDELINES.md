# Defensive Coding Guidelines for Planning Poker

## Common Error Patterns and How to Avoid Them

### 1. Session/Transaction Management in Reactive Contexts

#### Problem Pattern
```java
// DON'T: Mix transaction and non-transaction operations
return someService.doTransactionWork()
    .flatMap(result -> {
        // This runs within the transaction context
        return someService.doMoreDatabaseWork(); // Can cause "Illegal pop()" errors
    });
```

#### Defensive Pattern
```java
// DO: Complete transactions before doing more database work
return someService.doTransactionWork()
    .flatMap(result -> {
        // Store what you need from the transaction
        String id = result.getId();
        return Uni.createFrom().item(id);
    })
    // Now do additional database work in a fresh context
    .flatMap(id -> someService.doMoreDatabaseWork(id));
```

#### Rules
1. **WebSocket handlers need explicit session management** - Unlike REST endpoints, WebSocket methods don't automatically have sessions
2. **Don't nest Panache.withSession/withTransaction calls** - This causes context conflicts
3. **Complete transactions before broadcasting** - Don't query the database from within a transaction to send updates
4. **Use separate methods for internal vs external calls** - If a method might be called from within a transaction, provide a version without session wrapping

### 2. Message Type Registration

#### Problem Pattern
```java
// DON'T: Forget to register new message types
@JsonSubTypes({
    @JsonSubTypes.Type(value = MessageA.class, name = "A"),
    @JsonSubTypes.Type(value = MessageB.class, name = "B")
    // Forgot to add MessageC!
})
```

#### Defensive Pattern
```java
// DO: Keep all message types registered and documented
@JsonSubTypes({
    @JsonSubTypes.Type(value = JoinRoomMessage.class, name = "JOIN_ROOM"),
    @JsonSubTypes.Type(value = VoteMessage.class, name = "VOTE"),
    // ... list ALL message types
})
public class BaseMessage {
    // Consider adding a test that verifies all message classes are registered
}
```

#### Rules
1. **Always update BaseMessage when adding new message types**
2. **Consider using an enum for message type constants** to avoid typos
3. **Add a unit test** that verifies all message types can be serialized/deserialized

### 3. Reactive Context Management

#### Problem Pattern
```java
// DON'T: Lose the reactive context
@Test
public void testSomething() {
    roomService.createRoom("test")
        .await().indefinitely(); // Blocks and loses context
}
```

#### Defensive Pattern
```java
// DO: Use proper reactive testing patterns
@Test
@RunOnVertxContext
public void testSomething(UniAsserter asserter) {
    asserter.assertThat(
        () -> roomService.createRoom("test"),
        room -> assertNotNull(room)
    );
}
```

#### Rules
1. **Use @RunOnVertxContext for tests** that need reactive context
2. **Avoid blocking operations** in reactive chains
3. **Use Uni.createFrom().item()** to wrap synchronous values into the reactive chain

### 4. Service Method Patterns

#### Defensive Service Method Structure
```java
@ApplicationScoped
public class SomeService {
    
    // Public method with session/transaction management
    public Uni<Result> publicMethod(String param) {
        return Panache.withTransaction(() -> 
            internalMethod(param)
        );
    }
    
    // Internal method without session wrapper for composition
    private Uni<Result> internalMethod(String param) {
        // Actual implementation
        return Entity.find("param", param).firstResult();
    }
    
    // Read-only operations should use withSession, not withTransaction
    public Uni<Data> readOnlyMethod(String id) {
        return Panache.withSession(() ->
            Entity.findById(id)
        );
    }
}
```

### 5. WebSocket Handler Patterns

#### Defensive WebSocket Pattern
```java
@WebSocket(path = "/ws/endpoint")
public class MyWebSocket {
    
    @OnTextMessage
    public Uni<Void> onMessage(WebSocketConnection connection, String message) {
        return handleMessage(message)
            .onFailure().recoverWithUni(error -> {
                // Always handle errors gracefully
                LOGGER.severe("Error: " + error.getMessage());
                return sendError(connection, "Operation failed");
            });
    }
    
    private Uni<Void> handleDatabaseOperation(String data) {
        // Database operations complete first
        return someService.modifyData(data)
            .flatMap(result -> {
                // Store what you need
                String roomId = result.getRoomId();
                return Uni.createFrom().item(roomId);
            })
            // Then broadcast in a fresh context
            .flatMap(roomId -> broadcastUpdate(roomId));
    }
}
```

### 6. Error Recovery Patterns

#### Always Provide Fallbacks
```java
// DO: Provide error recovery
return someOperation()
    .onFailure().recoverWithUni(error -> {
        LOGGER.warning("Operation failed, using fallback: " + error.getMessage());
        return fallbackOperation();
    })
    .onFailure().recoverWithItem(defaultValue); // Final fallback
```

### 7. Testing Patterns

#### Defensive Testing
```java
@QuarkusTest
public class ServiceTest {
    
    @Test
    @RunOnVertxContext  // Always use for reactive tests
    public void testWithContext(UniAsserter asserter) {
        asserter
            .assertThat(
                () -> service.operation(),
                result -> assertNotNull(result)
            )
            // Chain multiple operations properly
            .transformItem(result -> service.nextOperation(result))
            .assertThat(
                finalResult -> assertEquals(expected, finalResult)
            );
    }
}
```

## Checklist for New Features

- [ ] All new message types added to BaseMessage @JsonSubTypes
- [ ] WebSocket handlers have proper error recovery
- [ ] Database operations are properly wrapped in sessions/transactions
- [ ] No database queries within transaction lambdas that will be used for broadcasting
- [ ] Tests use @RunOnVertxContext when needed
- [ ] Service methods follow the public/private pattern for transaction management
- [ ] All Uni chains have .onFailure() handlers

## Common Fixes Reference

| Error | Likely Cause | Fix |
|-------|-------------|-----|
| "No current Mutiny.Session found" | WebSocket or background operation without session | Wrap in `Panache.withSession()` |
| "Illegal pop() with non-matching JdbcValuesSourceProcessingState" | Nested sessions or operations within transaction | Separate transaction and query phases |
| "Could not resolve type id" | Message type not registered | Add to BaseMessage @JsonSubTypes |
| "No current Vertx context found" | Test without context | Add @RunOnVertxContext |

## Architecture Decisions

1. **Transaction Boundaries**: Keep transactions small and focused. Don't do broadcasting or complex queries within transactions.

2. **Session Management**: 
   - REST endpoints: Automatic (handled by Quarkus)
   - WebSockets: Manual (must wrap in Panache.withSession)
   - Background tasks: Manual

3. **Broadcasting Pattern**: Always broadcast AFTER transactions complete, never during.

4. **Error Handling**: Every external-facing method should have error recovery to prevent cascading failures.