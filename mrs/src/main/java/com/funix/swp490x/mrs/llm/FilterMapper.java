package com.funix.swp490x.mrs.llm;

import com.funix.swp490x.mrs.domain.Tag;
import com.funix.swp490x.mrs.domain.TagType;
import com.funix.swp490x.mrs.repository.TagRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Resolves interpreted names to tag ids already on songs. Unknown names are
 * dropped rather than created.
 */
@Component
public class FilterMapper {

    private final TagRepository tagRepository;

    public FilterMapper(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    public MappedFilters map(InterpretedFilters interpreted) {
        if (interpreted == null || interpreted.isEmpty()) {
            return MappedFilters.empty();
        }
        Map<String, Long> byTypeAndName = indexUsed();
        return new MappedFilters(
                ids(interpreted.genres(), TagType.GENRE, byTypeAndName),
                ids(interpreted.moods(), TagType.MOOD, byTypeAndName),
                ids(interpreted.artists(), TagType.ARTIST, byTypeAndName),
                ids(interpreted.tags(), TagType.TAGS, byTypeAndName));
    }

    private Map<String, Long> indexUsed() {
        Map<String, Long> index = new LinkedHashMap<>();
        for (Tag tag : tagRepository.findAllUsedOrderByTypeAscNameAsc()) {
            if (tag.getId() == null || tag.getType() == null || tag.getName() == null) {
                continue;
            }
            index.putIfAbsent(key(tag.getType(), tag.getName()), tag.getId());
        }
        return index;
    }

    private static List<Long> ids(List<String> names, TagType type, Map<String, Long> index) {
        List<Long> ids = new ArrayList<>();
        if (names == null) {
            return ids;
        }
        for (String name : names) {
            Long id = index.get(key(type, name));
            if (id != null && !ids.contains(id)) {
                ids.add(id);
            }
        }
        return List.copyOf(ids);
    }

    private static String key(TagType type, String name) {
        return type.name() + ":" + name.trim().toLowerCase(Locale.ROOT);
    }

    public record MappedFilters(
            List<Long> genreIds,
            List<Long> moodIds,
            List<Long> artistIds,
            List<Long> tagIds) {

        public MappedFilters {
            genreIds = genreIds == null ? List.of() : List.copyOf(genreIds);
            moodIds = moodIds == null ? List.of() : List.copyOf(moodIds);
            artistIds = artistIds == null ? List.of() : List.copyOf(artistIds);
            tagIds = tagIds == null ? List.of() : List.copyOf(tagIds);
        }

        public static MappedFilters empty() {
            return new MappedFilters(List.of(), List.of(), List.of(), List.of());
        }

        public boolean isEmpty() {
            return genreIds.isEmpty() && moodIds.isEmpty() && artistIds.isEmpty()
                    && tagIds.isEmpty();
        }

        public List<Long> relevanceIds() {
            List<Long> ids = new ArrayList<>();
            ids.addAll(genreIds);
            ids.addAll(moodIds);
            ids.addAll(artistIds);
            ids.addAll(tagIds);
            return List.copyOf(ids);
        }
    }
}
