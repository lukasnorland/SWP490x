package com.funix.swp490x.mrs.service;

import com.funix.swp490x.mrs.catalog.CatalogTaxonomy;
import com.funix.swp490x.mrs.domain.Tag;
import com.funix.swp490x.mrs.domain.TagType;
import com.funix.swp490x.mrs.repository.TagRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * ADMIN vocabulary on P-06b. Staged song JSON remains source of truth, so
 * names already on songs cannot be renamed or deleted here — that would strip
 * {@code song_tag} without patching S3. Unused rows are MySQL-only dictionary
 * entries (typeahead / the next song edit). Catalog sync still drops unused
 * names after an import, which is the same cleanup as orphan JSON detach.
 */
@Service
public class TagVocabularyService {

    public static final int NAME_MAX_LENGTH = 100;

    private final TagRepository tagRepository;
    private final CatalogTaxonomy taxonomy = new CatalogTaxonomy();

    public TagVocabularyService(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    @Transactional(readOnly = true)
    public List<TagRow> list() {
        Map<Long, Long> usage = new HashMap<>();
        for (Object[] row : tagRepository.countSongsGroupedByTagId()) {
            usage.put((Long) row[0], (Long) row[1]);
        }
        return tagRepository.findAll(Sort.by(Sort.Order.asc("type"), Sort.Order.asc("name")))
                .stream()
                .map(tag -> new TagRow(tag.getId(), tag.getType(), tag.getName(),
                        usage.getOrDefault(tag.getId(), 0L)))
                .toList();
    }

    @Transactional
    public Tag create(TagType type, String rawName) {
        if (type == null) {
            throw new TagVocabularyException("Choose a vocabulary.");
        }
        String name = normalize(type, rawName);
        if (tagRepository.findByTypeAndName(type, name).isPresent()) {
            throw new TagVocabularyException("That name is already in this vocabulary.");
        }
        return tagRepository.save(new Tag(type, name));
    }

    @Transactional
    public Tag rename(Long id, String rawName) {
        Tag tag = tagRepository.findById(id)
                .orElseThrow(() -> new TagVocabularyException("That tag is no longer in the dictionary."));
        refuseIfInUse(tag);
        String name = normalize(tag.getType(), rawName);
        if (name.equals(tag.getName())) {
            return tag;
        }
        if (tagRepository.findByTypeAndName(tag.getType(), name).isPresent()) {
            throw new TagVocabularyException("That name is already in this vocabulary.");
        }
        tag.setName(name);
        return tagRepository.save(tag);
    }

    @Transactional
    public void delete(Long id) {
        Tag tag = tagRepository.findById(id)
                .orElseThrow(() -> new TagVocabularyException("That tag is no longer in the dictionary."));
        refuseIfInUse(tag);
        tagRepository.delete(tag);
    }

    private void refuseIfInUse(Tag tag) {
        if (tagRepository.countSongsWithTag(tag.getId()) > 0) {
            throw new TagVocabularyException(
                    "That name is on at least one song. Change it from the song, so the staged JSON stays the source of truth.");
        }
    }

    private String normalize(TagType type, String rawName) {
        if (!StringUtils.hasText(rawName)) {
            throw new TagVocabularyException("Enter a name.");
        }
        // resolveGenre / resolveMood return null for anything off the allowlist,
        // which is the same gate ADMIN save/upload applies via requireAllowlisted.
        String name = switch (type) {
            case GENRE -> required(taxonomy.resolveGenre(rawName),
                    "Pick a listed MusicBrainz genre. Unknown names cannot be saved as Genre.");
            case MOOD -> required(taxonomy.resolveMood(rawName),
                    "Pick a listed mood. Unknown names cannot be saved as Mood.");
            default -> required(taxonomy.canonicalName(rawName), "Enter a name.");
        };
        if (name.length() > NAME_MAX_LENGTH) {
            throw new TagVocabularyException(
                    "Use at most %d characters.".formatted(NAME_MAX_LENGTH));
        }
        return name;
    }

    private static String required(String resolved, String message) {
        if (resolved == null) {
            throw new TagVocabularyException(message);
        }
        return resolved;
    }

    public record TagRow(Long id, TagType type, String name, long usageCount) {

        public boolean unused() {
            return usageCount == 0;
        }
    }
}
