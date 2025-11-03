# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

Create background worker consuming export jobs from Redis Stream. `ExportJobProcessor` listens to `jobs:reports` stream, processes job (query session data, generate CSV or PDF), upload to S3 bucket, update job status in database (JobStatus: PENDING → PROCESSING → COMPLETED/FAILED), generate time-limited signed URL for download. Use Apache Commons CSV for CSV generation, iText or Apache PDFBox for PDF. Handle errors (mark job FAILED, store error message). Implement exponential backoff retry for transient failures.

---

## Issues Detected

*   **Compilation Error:** The file `backend/src/main/java/com/scrumpoker/worker/PdfExporter.java` has a compilation error on line 17. The class `org.apache.pdfbox.pdmodel.font.Standard14Fonts` is not public in PDFBox 2.0.30 and cannot be accessed from outside the package.
*   **API Incompatibility:** The code is using an incorrect API for PDFBox 2.0.30. The `Standard14Fonts` class is internal in PDFBox 2.x and should not be used directly.

---

## Best Approach to Fix

You MUST modify the `PdfExporter.java` file at `backend/src/main/java/com/scrumpoker/worker/PdfExporter.java`.

**Fix the PDFBox font usage:**

1. Replace all instances of `new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)` with `PDType1Font.HELVETICA_BOLD` (PDType1Font constants are directly accessible in PDFBox 2.x)
2. Replace all instances of `new PDType1Font(Standard14Fonts.FontName.HELVETICA)` with `PDType1Font.HELVETICA`
3. Replace all instances of `new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE)` with `PDType1Font.HELVETICA_OBLIQUE`
4. Remove the import statement: `import org.apache.pdfbox.pdmodel.font.Standard14Fonts;`

**The correct PDFBox 2.x API for standard fonts is:**
```java
import org.apache.pdfbox.pdmodel.font.PDType1Font;

// Then use directly:
PDType1Font.HELVETICA_BOLD
PDType1Font.HELVETICA
PDType1Font.HELVETICA_OBLIQUE
```

This is the standard way to use built-in fonts in PDFBox 2.0.x. The `Standard14Fonts` class was made internal because the font constants are already available directly on `PDType1Font`.
