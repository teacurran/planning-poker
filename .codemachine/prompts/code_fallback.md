# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

Create background worker consuming export jobs from Redis Stream. `ExportJobProcessor` listens to `jobs:reports` stream, processes job (query session data, generate CSV or PDF), upload to S3 bucket, update job status in database (JobStatus: PENDING → PROCESSING → COMPLETED/FAILED), generate time-limited signed URL for download. Use Apache Commons CSV for CSV generation, iText or Apache PDFBox for PDF. Handle errors (mark job FAILED, store error message). Implement exponential backoff retry for transient failures.

---

## Issues Detected

*   **Missing Dependency:** The AWS S3 SDK requires an HTTP client implementation. Tests are failing with error: `Missing 'software.amazon.awssdk:url-connection-client' dependency on the classpath`. This is a required dependency for the Quarkus Amazon S3 extension to function properly.

*   **Deprecation Warnings:** The code uses deprecated `combinedWith()` method in both `CsvExporter.java` (line 137) and `PdfExporter.java` (line 168). While this still works, it should be updated to use the non-deprecated API for future compatibility.

---

## Best Approach to Fix

### 1. Add Missing AWS SDK HTTP Client Dependency

You MUST add the AWS SDK URL Connection HTTP client dependency to `backend/pom.xml`. Add this dependency in the dependencies section, right after the existing `quarkus-amazon-s3` dependency (around line 169):

```xml
<!-- AWS SDK URL Connection HTTP Client (required by quarkus-amazon-s3) -->
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>url-connection-client</artifactId>
</dependency>
```

### 2. Fix Deprecation Warnings (Optional but Recommended)

Replace the deprecated `combinedWith()` method calls with the recommended `with()` method:

**In `CsvExporter.java` (line 136-139):**

Change FROM:
```java
return Uni.combine().all().unis(voteFetches)
        .combinedWith(results -> results.stream()
                .map(obj -> (Map.Entry<UUID, List<Vote>>) obj)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
```

TO:
```java
return Uni.combine().all().unis(voteFetches)
        .with(results -> results.stream()
                .map(obj -> (Map.Entry<UUID, List<Vote>>) obj)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
```

**In `PdfExporter.java` (line 167-170):**

Change FROM:
```java
return Uni.combine().all().unis(voteFetches)
        .combinedWith(results -> results.stream()
                .map(obj -> (Map.Entry<UUID, List<Vote>>) obj)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
```

TO:
```java
return Uni.combine().all().unis(voteFetches)
        .with(results -> results.stream()
                .map(obj -> (Map.Entry<UUID, List<Vote>>) obj)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
```

### 3. Verify the Fix

After making these changes:

1. **Compile the project:** Run `mvn clean compile` to ensure no compilation errors
2. **Run tests:** Run `mvn test` to verify all tests pass
3. **Verify S3 integration:** Ensure the S3Adapter can be initialized without the missing dependency error

---

## Critical Notes

- The **missing AWS SDK HTTP client dependency is the BLOCKER** preventing tests from running. This MUST be fixed first.
- The deprecation warnings are less critical but should be addressed for code quality and future compatibility.
- All other code (ExportJobProcessor, CsvExporter, PdfExporter, ExportJob entity, JobStatus enum, S3Adapter) is complete and correct.
