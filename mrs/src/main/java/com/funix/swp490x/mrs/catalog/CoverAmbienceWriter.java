package com.funix.swp490x.mrs.catalog;

import com.funix.swp490x.mrs.repository.SongRepository;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes sampled colors in one transaction after network reads finish.
 * Derived color updates leave tags and the optimistic-lock version unchanged (BR-06).
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
