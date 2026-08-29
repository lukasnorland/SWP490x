package com.funix.swp490x.mrs.catalog;

import com.funix.swp490x.mrs.domain.Tag;
import com.funix.swp490x.mrs.domain.TagType;
import com.funix.swp490x.mrs.repository.TagRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

/**
 * Typeahead for P-06b genre / mood / tag fields. Genres come from the cached
 * MusicBrainz list, moods from the product vocabulary, tags from names already
 * on songs.
 */
@Service
public class TagSuggestionService {

    private final CatalogTaxonomy taxonomy = new CatalogTaxonomy();
    private final TagRepository tagRepository;

    public TagSuggestionService(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    public List<String> suggest(TagType type, String query, int limit) {
        TagType resolved = type == null ? TagType.TAGS : type;
        int cap = Math.min(Math.max(limit, 1), 50);
        if (resolved == TagType.GENRE || resolved == TagType.MOOD) {
            return taxonomy.suggest(resolved, query, cap);
        }
        return suggestExistingTags(resolved, query, cap);
    }

    private List<String> suggestExistingTags(TagType type, String query, int limit) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Tag> existing = tagRepository.findByTypeOrderByName(type);
        List<String> prefix = new ArrayList<>();
        List<String> rest = new ArrayList<>();
        for (Tag tag : existing) {
            if (tag.getName() == null) {
                continue;
            }
            String lower = tag.getName().toLowerCase(Locale.ROOT);
            if (q.isEmpty() || lower.startsWith(q)) {
                prefix.add(tag.getName());
            } else if (lower.contains(q)) {
                rest.add(tag.getName());
            }
            if (prefix.size() >= limit) {
                break;
            }
        }
        List<String> out = new ArrayList<>(prefix);
        for (String name : rest) {
            if (out.size() >= limit) {
                break;
            }
            out.add(name);
        }
        return List.copyOf(out);
    }
}
