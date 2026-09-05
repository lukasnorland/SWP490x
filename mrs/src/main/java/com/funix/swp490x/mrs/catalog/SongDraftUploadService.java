package com.funix.swp490x.mrs.catalog;

import com.funix.swp490x.mrs.catalog.ImportSummary.SkippedRow;
import com.funix.swp490x.mrs.domain.ImportTrigger;
import com.funix.swp490x.mrs.repository.SongRepository;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * Stages audio, cover art and a generated song-data JSON from the P-06b Add
 * Song form, then queues the ETag sync so the songs land in MySQL the same
 * way a CLI dump of JSON would. The object store is written first; MySQL is
 * never inserted here — import is the only path that creates catalog rows.
 *
 * <p>The whole batch is validated before anything is written, so a rejected
 * row never leaves a half-staged song. A failure partway through the writes
 * themselves is reported rather than rolled back; {@code DeleteObject} is
 * available, but this path does not attempt a compensating delete.
 */
@Service
public class SongDraftUploadService {

    private static final Logger log = LoggerFactory.getLogger(SongDraftUploadService.class);

    /**
     * Soft cap so one request stays inside
     * {@code spring.servlet.multipart.max-request-size} (10 × 100 MB audio +
     * 10 × 5 MB covers).
     */
    static final int MAX_DRAFTS = 10;

    private static final Map<String, String> AUDIO_EXTENSIONS = Map.of(
            "mp3", "audio/mpeg",
            "wav", "audio/wav",
            "flac", "audio/flac",
            "m4a", "audio/mp4",
            "mp4", "audio/mp4",
            "ogg", "audio/ogg",
            "aac", "audio/aac");

    private static final Map<String, String> COVER_EXTENSIONS = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp");

    private static final Set<String> AUDIO_TYPE_ALIASES = Set.of(
            "audio/mpeg", "audio/mp3", "audio/wav", "audio/x-wav", "audio/wave",
            "audio/flac", "audio/mp4", "audio/x-m4a", "audio/m4a", "audio/ogg",
            "audio/aac", "audio/x-aac");

    private static final Set<String> COVER_TYPE_ALIASES = Set.of(
            "image/jpeg", "image/jpg", "image/pjpeg", "image/png", "image/webp");

    private final CatalogObjectStore store;
    private final SongJsonMapper mapper;
    private final CatalogProperties properties;
    private final CatalogImportService importService;
    private final SongRepository songRepository;
    private final Supplier<UUID> ids;

    @Autowired
    public SongDraftUploadService(CatalogObjectStore store,
            SongJsonMapper mapper,
            CatalogProperties properties,
            CatalogImportService importService,
            SongRepository songRepository) {
        this(store, mapper, properties, importService, songRepository, UUID::randomUUID);
    }

    SongDraftUploadService(CatalogObjectStore store,
            SongJsonMapper mapper,
            CatalogProperties properties,
            CatalogImportService importService,
            SongRepository songRepository,
            Supplier<UUID> ids) {
        this.store = store;
        this.mapper = mapper;
        this.properties = properties;
        this.importService = importService;
        this.songRepository = songRepository;
        this.ids = ids;
    }

    public List<String> registeredProviders() {
        return properties.getProviders();
    }

    /**
     * @param drafts sections from the form; null, empty, or fully blank
     *     sections are an empty selection rather than an error
     * @param actorId ADMIN who pressed upload; passed through to the sync so
     *     BR-10 gets an audit row
     */
    public MediaUploadResult upload(List<SongDraftForm> drafts, Long actorId) {
        List<SongDraftForm> batch = drafts == null ? List.of() : drafts.stream()
                .filter(d -> d != null && !isBlank(d))
                .toList();

        if (batch.isEmpty()) {
            return MediaUploadResult.emptySelection();
        }

        if (batch.size() > MAX_DRAFTS) {
            return MediaUploadResult.tooMany(new SkippedRow("(batch)",
                    "too many songs — at most " + MAX_DRAFTS + " per upload"));
        }

        List<SkippedRow> rejected = new ArrayList<>();
        Set<String> seenIsrcs = new HashSet<>();
        for (int i = 0; i < batch.size(); i++) {
            SongDraftForm draft = batch.get(i);
            String reason = validate(draft);
            if (reason == null) {
                String isrc = draft.getIsrc().trim();
                String key = isrc.toUpperCase(Locale.ROOT);
                if (seenIsrcs.contains(key) || songRepository.existsByIsrcIgnoreCase(isrc)) {
                    reason = "duplicate ISRC";
                }
                seenIsrcs.add(key);
            }
            if (reason != null) {
                rejected.add(new SkippedRow(labelOf(draft, i), reason));
            }
        }
        if (!rejected.isEmpty()) {
            return new MediaUploadResult(0, List.copyOf(rejected), null, false);
        }

        int uploaded = 0;
        for (int i = 0; i < batch.size(); i++) {
            SongDraftForm draft = batch.get(i);
            String label = labelOf(draft, i);
            try {
                stage(draft);
                uploaded++;
            } catch (IOException e) {
                log.warn("Could not read uploaded media for {}", label, e);
                rejected.add(new SkippedRow(label, "could not be read"));
            } catch (RuntimeException e) {
                log.error("Could not stage uploaded media for {}", label, e);
                rejected.add(new SkippedRow(label, "could not be written to staging"));
            }
        }

        ImportSummary sync = null;
        if (uploaded > 0) {
            boolean started = importService.startAsync(ImportTrigger.MANUAL, actorId, false);
            sync = started ? null : ImportSummary.refused();
        }

        return new MediaUploadResult(uploaded, List.copyOf(rejected), sync, false);
    }

    private void stage(SongDraftForm draft) throws IOException {
        String id = ids.get().toString();
        String provider = draft.getSourceProvider().trim();
        String slug = properties.getMedia().slugFor(provider);

        MultipartFile audio = draft.getAudio();
        String audioExt = extensionOf(audio.getOriginalFilename());
        String audioKey = store.mediaKey(MediaKind.AUDIO, slug, id, audioExt);
        try (InputStream in = audio.getInputStream()) {
            store.putBinary(audioKey, resolvedAudioType(audio, audioExt), in, audio.getSize());
        }

        String coverUrl = null;
        MultipartFile cover = draft.getCover();
        if (!isEmpty(cover)) {
            String coverExt = extensionOf(cover.getOriginalFilename());
            String coverKey = store.mediaKey(MediaKind.ARTWORK, slug, id, coverExt);
            try (InputStream in = cover.getInputStream()) {
                store.putBinary(coverKey, resolvedCoverType(cover, coverExt), in, cover.getSize());
            }
            coverUrl = properties.getMedia().publicUrl(coverKey);
        }

        StagedSong staged = new StagedSong(
                id,
                provider,
                draft.getTitle().trim(),
                trimToNull(draft.getArtist()),
                positiveOrNull(draft.getDuration()),
                positiveOrNull(draft.getBpm()),
                draft.getIsExplicit() != null && draft.getIsExplicit(),
                trimToNull(draft.getIsrc()),
                properties.getMedia().publicUrl(audioKey),
                coverUrl,
                splitCsv(draft.getGenres()),
                splitCsv(draft.getMoods()),
                splitCsv(draft.getTags()));

        store.putJson(store.stagingKey(id), mapper.write(staged));
    }

    /**
     * @return a reason, or {@code null} when the draft is ready to stage
     */
    private String validate(SongDraftForm draft) {
        if (!StringUtils.hasText(draft.getTitle())) {
            return "missing title";
        }
        if (!StringUtils.hasText(draft.getSourceProvider())) {
            return "missing sourceProvider";
        }
        String provider = draft.getSourceProvider().trim();
        boolean registered = properties.getProviders().stream()
                .anyMatch(p -> p.equalsIgnoreCase(provider));
        if (!registered) {
            return "unregistered provider '" + provider + "'";
        }
        if (properties.getMedia().slugFor(provider) == null) {
            return "no folder mapping for provider '" + provider + "'";
        }
        if (!StringUtils.hasText(draft.getArtist())) {
            return "missing artist";
        }
        if (!StringUtils.hasText(draft.getIsrc())) {
            return "missing ISRC";
        }

        List<String> genres = splitCsv(draft.getGenres());
        List<String> moods = splitCsv(draft.getMoods());
        if (genres.isEmpty()) {
            return "missing genres";
        }
        if (moods.isEmpty()) {
            return "missing moods";
        }
        try {
            mapper.requireAllowlisted(genres, moods);
        } catch (InvalidClassificationException e) {
            return e.getMessage();
        }

        MultipartFile audio = draft.getAudio();
        if (isEmpty(audio)) {
            return "missing audio file";
        }
        if (audio.getSize() > properties.getMedia().getMaxAudioBytes()) {
            return "audio larger than " + megabytes(properties.getMedia().getMaxAudioBytes());
        }
        String audioExt = extensionOf(audio.getOriginalFilename());
        if (!AUDIO_EXTENSIONS.containsKey(audioExt)) {
            return "not an audio file";
        }
        if (!typeAllowed(contentTypeOf(audio), properties.getMedia().getAudioTypes(),
                AUDIO_TYPE_ALIASES, AUDIO_EXTENSIONS.get(audioExt))) {
            return "content type '" + contentTypeOf(audio) + "' is not an allowed audio type";
        }

        MultipartFile cover = draft.getCover();
        if (isEmpty(cover)) {
            return "missing cover art";
        }
        if (cover.getSize() > properties.getMedia().getMaxCoverBytes()) {
            return "cover larger than " + megabytes(properties.getMedia().getMaxCoverBytes());
        }
        String coverExt = extensionOf(cover.getOriginalFilename());
        if (!COVER_EXTENSIONS.containsKey(coverExt)) {
            return "cover is not a JPEG, PNG or WebP image";
        }
        if (!typeAllowed(contentTypeOf(cover), properties.getMedia().getCoverTypes(),
                COVER_TYPE_ALIASES, COVER_EXTENSIONS.get(coverExt))) {
            return "content type '" + contentTypeOf(cover) + "' is not an allowed image type";
        }
        return null;
    }

    private static boolean isBlank(SongDraftForm draft) {
        return isEmpty(draft.getAudio())
                && isEmpty(draft.getCover())
                && !StringUtils.hasText(draft.getTitle())
                && !StringUtils.hasText(draft.getSourceProvider());
    }

    private static boolean isEmpty(MultipartFile file) {
        return file == null || file.isEmpty();
    }

    private static String labelOf(SongDraftForm draft, int index) {
        if (StringUtils.hasText(draft.getTitle())) {
            return draft.getTitle().trim();
        }
        if (draft.getAudio() != null && StringUtils.hasText(draft.getAudio().getOriginalFilename())) {
            return draft.getAudio().getOriginalFilename();
        }
        return "song " + (index + 1);
    }

    private static String extensionOf(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String contentTypeOf(MultipartFile file) {
        String type = file.getContentType();
        return type == null ? "" : type.toLowerCase(Locale.ROOT).trim();
    }

    /**
     * A specific content type must be on the allowlist (or an alias of one).
     * Browsers often send an empty type or {@code application/octet-stream}
     * for a dropped file; those fall back to the type implied by the
     * extension, which has already been checked.
     */
    private static boolean typeAllowed(String contentType, List<String> declared,
            Set<String> aliases, String impliedByExtension) {
        if (!StringUtils.hasText(contentType) || "application/octet-stream".equals(contentType)) {
            return true;
        }
        for (String allowed : declared) {
            if (contentType.equalsIgnoreCase(allowed)) {
                return true;
            }
        }
        return aliases.contains(contentType) && impliedByExtension != null;
    }

    private static String resolvedAudioType(MultipartFile file, String ext) {
        String type = contentTypeOf(file);
        if (StringUtils.hasText(type) && !"application/octet-stream".equals(type)) {
            return type;
        }
        return AUDIO_EXTENSIONS.getOrDefault(ext, "application/octet-stream");
    }

    private static String resolvedCoverType(MultipartFile file, String ext) {
        String type = contentTypeOf(file);
        if (StringUtils.hasText(type) && !"application/octet-stream".equals(type)) {
            return type;
        }
        return COVER_EXTENSIONS.getOrDefault(ext, "application/octet-stream");
    }

    private static List<String> splitCsv(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        return Arrays.stream(raw.split("[,;]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static Integer positiveOrNull(Integer value) {
        return value != null && value > 0 ? value : null;
    }

    private static String megabytes(long bytes) {
        return (bytes / (1024 * 1024)) + " MB";
    }

    /**
     * @param uploaded songs that were written to staging
     * @param rejected songs that never left the browser/server (with reasons)
     * @param sync outcome of the auto-sync when it was refused, or null when
     *     the sync was queued in the background (or nothing was staged)
     * @param tooMany true when the whole batch was refused for size
     */
    public record MediaUploadResult(
            int uploaded,
            List<SkippedRow> rejected,
            ImportSummary sync,
            boolean tooMany) {

        public static MediaUploadResult emptySelection() {
            return new MediaUploadResult(0, List.of(), null, false);
        }

        public static MediaUploadResult tooMany(SkippedRow reason) {
            return new MediaUploadResult(0, List.of(reason), null, true);
        }

        public boolean isEmptySelection() {
            return uploaded == 0 && rejected.isEmpty() && sync == null && !tooMany;
        }
    }
}
