package com.funix.swp490x.mrs.catalog;

import com.funix.swp490x.mrs.repository.SongRepository;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores one batch of sampled wash colours in a single transaction.
 *
 * <p>Separate from {@link CoverAmbienceService} so that reading covers, which
 * is minutes of network, happens with no transaction open. The caller decides
 * how much to hand over at a time, the same way the import chunks its upserts.
 *
 * <p>The write is a targeted update rather than saving the entity: colours are
 * derived data, so recomputing them should neither hydrate a song's tags nor
 * bump the version that BR-06 uses to protect a designer's edit.
 */
@Component
public class CoverAmbienceWriter {

    private final SongRepository songRepository;

    public CoverAmbienceWriter(SongRepository songRepository) {
        this.songRepository = songRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(List<Update> batch) {
        for (Update update : batch) {
            songRepository.recordAmbience(update.songId(), update.a(), update.b(),
                    update.sourceUrl());
        }
    }

    /**
     * One song's outcome. Null colours with a source url mean the cover was
     * looked at and gave nothing, which is what stops it being retried.
     */
    public record Update(Long songId, String a, String b, String sourceUrl) {
    }
}
