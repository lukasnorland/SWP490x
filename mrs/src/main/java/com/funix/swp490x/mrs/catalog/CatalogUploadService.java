package com.funix.swp490x.mrs.catalog;

import com.funix.swp490x.mrs.catalog.ImportSummary.SkippedRow;
import com.funix.swp490x.mrs.domain.ImportTrigger;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * Stages song JSON uploaded from P-06c, then runs the normal ETag sync so the
 * songs land in MySQL in the same request.
 *
 * <p>Validation reuses {@link SongJsonMapper}: a rejected file never reaches
 * the store. The staging key is always {@code <externalSourceId>.json} (with
 * the store's prefix when talking to S3), never the original filename.
 */
@Service
public class CatalogUploadService {

    private static final Logger log = LoggerFactory.getLogger(CatalogUploadService.class);

    /** Soft cap so one request cannot flood the bucket or the sync. */
    static final int MAX_FILES = 50;

    /** Matches {@code spring.servlet.multipart.max-file-size}. */
    static final long MAX_FILE_BYTES = 1_048_576L;

    private final CatalogObjectStore store;
    private final SongJsonMapper mapper;
    private final CatalogProperties properties;
    private final CatalogImportService importService;

    public CatalogUploadService(CatalogObjectStore store,
            SongJsonMapper mapper,
            CatalogProperties properties,
            CatalogImportService importService) {
        this.store = store;
        this.mapper = mapper;
        this.properties = properties;
        this.importService = importService;
    }

    /**
     * @param files multipart parts named {@code files}; null or empty is an
     *     empty selection, not an error thrown at the caller
     * @param actorId ADMIN who pressed upload; passed through to the sync so
     *     BR-10 gets an audit row
     */
    public UploadResult upload(List<MultipartFile> files, Long actorId) {
        List<MultipartFile> batch = files == null ? List.of() : files.stream()
                .filter(f -> f != null && !f.isEmpty())
                .toList();

        if (batch.isEmpty()) {
            return UploadResult.emptySelection();
        }

        List<SkippedRow> rejected = new ArrayList<>();
        int uploaded = 0;

        if (batch.size() > MAX_FILES) {
            rejected.add(new SkippedRow("(batch)",
                    "too many files — at most " + MAX_FILES + " per upload"));
            return new UploadResult(0, rejected, null, true);
        }

        for (MultipartFile file : batch) {
            String label = labelOf(file);
            try {
                if (file.getSize() > MAX_FILE_BYTES) {
                    rejected.add(new SkippedRow(label, "larger than 1 MB"));
                    continue;
                }
                if (!isJsonName(label)) {
                    rejected.add(new SkippedRow(label, "not a .json file"));
                    continue;
                }

                String json = new String(file.getBytes(), StandardCharsets.UTF_8);
                SongJsonMapper.Result mapped = mapper.map(json, properties.getProviders());
                if (mapped.isRejected()) {
                    rejected.add(new SkippedRow(label, mapped.rejection()));
                    continue;
                }

                String key = store.stagingKey(mapped.values().externalSourceId());
                store.putJson(key, json);
                uploaded++;
            } catch (IOException e) {
                log.warn("Could not read uploaded file {}", label, e);
                rejected.add(new SkippedRow(label, "could not be read"));
            } catch (CatalogStoreException e) {
                log.error("Could not stage uploaded file {}", label, e);
                rejected.add(new SkippedRow(label, "could not be written to staging"));
            }
        }

        ImportSummary sync = null;
        if (uploaded > 0) {
            sync = importService.sync(ImportTrigger.MANUAL, actorId, false);
        }

        return new UploadResult(uploaded, List.copyOf(rejected), sync, false);
    }

    private static String labelOf(MultipartFile file) {
        String name = file.getOriginalFilename();
        return StringUtils.hasText(name) ? name : "(unnamed)";
    }

    private static boolean isJsonName(String name) {
        return name.toLowerCase(Locale.ROOT).endsWith(".json");
    }

    /**
     * @param uploaded files that were written to staging
     * @param rejected files that never left the browser/server (with reasons)
     * @param sync outcome of the auto-sync, or null when nothing was staged
     * @param tooMany true when the whole batch was refused for size
     */
    public record UploadResult(
            int uploaded,
            List<SkippedRow> rejected,
            ImportSummary sync,
            boolean tooMany) {

        public static UploadResult emptySelection() {
            return new UploadResult(0, List.of(), null, false);
        }

        public boolean isEmptySelection() {
            return uploaded == 0 && rejected.isEmpty() && sync == null && !tooMany;
        }

        public int rejectedCount() {
            return rejected.size();
        }
    }
}
