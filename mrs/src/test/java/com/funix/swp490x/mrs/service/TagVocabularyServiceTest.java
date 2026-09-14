package com.funix.swp490x.mrs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.funix.swp490x.mrs.domain.Tag;
import com.funix.swp490x.mrs.domain.TagType;
import com.funix.swp490x.mrs.repository.TagRepository;
import com.funix.swp490x.mrs.service.TagVocabularyService.TagRow;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TagVocabularyServiceTest {

    @Mock
    private TagRepository tagRepository;

    private TagVocabularyService service;

    @BeforeEach
    void setUp() {
        service = new TagVocabularyService(tagRepository);
    }

    @Test
    void listIncludesUnusedDictionaryRows() {
        Tag unused = tag(2L, TagType.GENRE, "Jazz");
        Tag used = tag(1L, TagType.GENRE, "Pop");
        given(tagRepository.findAll(any(Sort.class))).willReturn(List.of(unused, used));
        given(tagRepository.countSongsGroupedByTagId())
                .willReturn(List.<Object[]>of(new Object[] {1L, 3L}));

        assertThat(service.list()).containsExactly(
                new TagRow(2L, TagType.GENRE, "Jazz", 0L),
                new TagRow(1L, TagType.GENRE, "Pop", 3L));
    }

    @Test
    void createCanonicalisesAListedGenre() {
        given(tagRepository.findByTypeAndName(TagType.GENRE, "Pop")).willReturn(Optional.empty());
        given(tagRepository.save(any(Tag.class))).willAnswer(invocation -> invocation.getArgument(0));

        Tag created = service.create(TagType.GENRE, "  pop  ");

        assertThat(created.getType()).isEqualTo(TagType.GENRE);
        assertThat(created.getName()).isEqualTo("Pop");
    }

    @Test
    void createRejectsAnUnknownGenre() {
        assertThatThrownBy(() -> service.create(TagType.GENRE, "Not A Real Genre"))
                .isInstanceOf(TagVocabularyException.class)
                .hasMessageContaining("MusicBrainz");
        then(tagRepository).should(never()).save(any());
    }

    @Test
    void renameIsRefusedWhenTheTagIsOnASong() {
        Tag pop = tag(1L, TagType.GENRE, "Pop");
        given(tagRepository.findById(1L)).willReturn(Optional.of(pop));
        given(tagRepository.countSongsWithTag(1L)).willReturn(4L);

        assertThatThrownBy(() -> service.rename(1L, "Jazz"))
                .isInstanceOf(TagVocabularyException.class)
                .hasMessageContaining("staged JSON");
        then(tagRepository).should(never()).save(any());
    }

    @Test
    void deleteRemovesAnUnusedRow() {
        Tag unused = tag(2L, TagType.TAGS, "Female Vocals");
        given(tagRepository.findById(2L)).willReturn(Optional.of(unused));
        given(tagRepository.countSongsWithTag(2L)).willReturn(0L);

        service.delete(2L);

        then(tagRepository).should().delete(unused);
    }

    private static Tag tag(Long id, TagType type, String name) {
        Tag tag = new Tag(type, name);
        ReflectionTestUtils.setField(tag, "id", id);
        return tag;
    }
}
