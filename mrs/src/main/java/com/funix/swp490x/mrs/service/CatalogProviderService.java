package com.funix.swp490x.mrs.service;

import com.funix.swp490x.mrs.catalog.CatalogObjectStore;
import com.funix.swp490x.mrs.catalog.CatalogProperties;
import com.funix.swp490x.mrs.domain.AuditLog;
import com.funix.swp490x.mrs.domain.CatalogProvider;
import com.funix.swp490x.mrs.domain.Song;
import com.funix.swp490x.mrs.repository.AuditLogRepository;
import com.funix.swp490x.mrs.repository.CatalogProviderRepository;
import com.funix.swp490x.mrs.repository.PlaylistRepository;
import com.funix.swp490x.mrs.repository.SongRepository;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * The registered catalog sources P-06d maintains and Add Song / import consult.
 *
 * <p>Replaces {@code mrs.catalog.providers} plus {@code vendor-slugs}: a
 * provider is one row with a display name and an S3 folder slug.
 */
@Service
public class CatalogProviderService {

    private static final Pattern SLUG = Pattern.compile("^[a-z0-9-]{2,40}$");

    private final CatalogProviderRepository providerRepository;
    private final SongRepository songRepository;
    private final PlaylistRepository playlistRepository;
    private final SongCatalogService songCatalogService;
    private final CatalogObjectStore store;
    private final CatalogProperties properties;
    private final AuditLogRepository auditLogRepository;

    public CatalogProviderService(CatalogProviderRepository providerRepository,
            SongRepository songRepository,
            PlaylistRepository playlistRepository,
            SongCatalogService songCatalogService,
            CatalogObjectStore store,
            CatalogProperties properties,
            AuditLogRepository auditLogRepository) {
        this.providerRepository = providerRepository;
        this.songRepository = songRepository;
        this.playlistRepository = playlistRepository;
        this.songCatalogService = songCatalogService;
        this.store = store;
        this.properties = properties;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public List<CatalogProvider> list() {
        return providerRepository.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public List<String> registeredNames() {
        return list().stream().map(CatalogProvider::getName).toList();
    }

    @Transactional(readOnly = true)
    public String slugFor(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        return providerRepository.findByNameIgnoreCase(name.trim())
                .map(CatalogProvider::getSlug)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean isRegistered(String name) {
        return StringUtils.hasText(name)
                && providerRepository.findByNameIgnoreCase(name.trim()).isPresent();
    }

    @Transactional(readOnly = true)
    public ProviderImpact impact(CatalogProvider provider) {
        return new ProviderImpact(
                provider,
                songRepository.countBySourceProviderIgnoreCase(provider.getName()),
                playlistRepository.countPlaylistsContainingProvider(provider.getName()));
    }

    @Transactional(readOnly = true)
    public List<ProviderImpact> listWithImpact() {
        return list().stream().map(this::impact).toList();
    }

    @Transactional
    public CatalogProvider create(String rawName, String rawSlug, Long actorId) {
        String name = rawName == null ? "" : rawName.trim();
        String slug = rawSlug == null ? "" : rawSlug.trim().toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(name) || name.length() > 100) {
            throw new InvalidCatalogProviderException(
                    "Provider name is required and must be at most 100 characters.");
        }
        if (!SLUG.matcher(slug).matches()) {
            throw new InvalidCatalogProviderException(
                    "Slug must be 2–40 characters of lowercase letters, digits and hyphens.");
        }
        if (providerRepository.existsByNameIgnoreCase(name)) {
            throw new DuplicateCatalogProviderException(
                    "A provider named \"" + name + "\" is already registered.");
        }
        if (providerRepository.existsBySlugIgnoreCase(slug)) {
            throw new DuplicateCatalogProviderException(
                    "The slug \"" + slug + "\" is already used by another provider.");
        }
        CatalogProvider saved = providerRepository.save(new CatalogProvider(name, slug));
        auditLogRepository.save(new AuditLog(actorId,
                AuditLog.ACTION_PROVIDER_CREATE,
                AuditLog.ENTITY_CATALOG_PROVIDER,
                saved.getId(),
                "{\"name\":%s,\"slug\":%s}".formatted(jsonString(name), jsonString(slug))));
        return saved;
    }

    /**
     * Removes the provider, every song it owns (hosted media then staged JSON
     * then the MySQL row), leftover objects under its slug, and the registry
     * row. The typed name must match exactly.
     */
    @Transactional
    public ProviderImpact delete(Long id, String confirmName, Long actorId) {
        CatalogProvider provider = providerRepository.findById(id)
                .orElseThrow(() -> new CatalogProviderNotFoundException(id));
        if (confirmName == null || !provider.getName().equals(confirmName.trim())) {
            throw new ProviderDeleteNotConfirmedException();
        }
        ProviderImpact before = impact(provider);
        List<Song> songs = songRepository.findBySourceProviderIgnoreCase(provider.getName());
        for (Song song : songs) {
            songCatalogService.delete(song.getId(), actorId);
        }
        deleteOrphanMedia(provider.getSlug());
        providerRepository.delete(provider);
        auditLogRepository.save(new AuditLog(actorId,
                AuditLog.ACTION_PROVIDER_DELETE,
                AuditLog.ENTITY_CATALOG_PROVIDER,
                id,
                "{\"name\":%s,\"slug\":%s,\"songsRemoved\":%d,\"playlistsAffected\":%d}"
                        .formatted(jsonString(provider.getName()), jsonString(provider.getSlug()),
                                before.songCount(), before.playlistCount())));
        return before;
    }

    private void deleteOrphanMedia(String slug) {
        String audio = properties.getMedia().getAudioPrefix() + slug + "/";
        String artwork = properties.getMedia().getArtworkPrefix() + slug + "/";
        for (String key : store.listKeys(audio)) {
            store.deleteBinary(key);
        }
        for (String key : store.listKeys(artwork)) {
            store.deleteBinary(key);
        }
    }

    private static String jsonString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    public record ProviderImpact(CatalogProvider provider, long songCount, long playlistCount) {
    }
}
