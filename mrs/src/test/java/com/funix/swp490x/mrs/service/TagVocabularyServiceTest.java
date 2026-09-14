package com.funix.swp490x.mrs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.funix.swp490x.mrs.domain.AuditLog;
import com.funix.swp490x.mrs.domain.Tag;
import com.funix.swp490x.mrs.domain.TagType;
import com.funix.swp490x.mrs.repository.AuditLogRepository;
import com.funix.swp490x.mrs.repository.TagRepository;
import com.funix.swp490x.mrs.service.TagVocabularyService.TagRow;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TagVocabularyServiceTest {

    @Mock
    private TagRepository tagRepository;
    @Mock
    private AuditLogRepository auditLogRepository;

    private TagVocabularyService service;

    @BeforeEach
    void setUp() {
        service = new TagVocabularyService(tagRepository, auditLogRepository);
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

        Tag created = service.create(TagType.GENRE, "  pop  ", 9L);

        assertThat(created.getType()).isEqualTo(TagType.GENRE);
        assertThat(created.getName()).isEqualTo("Pop");
    }

    @Test
    void createRejectsAnUnknownGenre() {
        assertThatThrownBy(() -> service.create(TagType.GENRE, "Not A Real Genre", 9L))
                .isInstanceOf(TagVocabularyException.class)
                .hasMessageContaining("MusicBrainz");
        then(tagRepository).should(never()).save(any());
        then(auditLogRepository).should(never()).save(any());
    }

    @Test
    void createWritesAnAuditRow() {
        given(tagRepository.findByTypeAndName(TagType.TAGS, "Female Vocals"))
                .willReturn(Optional.empty());
        given(tagRepository.save(any(Tag.class))).willReturn(tag(7L, TagType.TAGS, "Female Vocals"));

        service.create(TagType.TAGS, "female vocals", 9L);

        AuditLog row = savedAudit();
        assertThat(row.getActorId()).isEqualTo(9L);
        assertThat(row.getAction()).isEqualTo(AuditLog.ACTION_TAG_CREATE);
        assertThat(row.getEntityType()).isEqualTo(AuditLog.ENTITY_TAG);
        assertThat(row.getEntityId()).isEqualTo(7L);
        assertThat(row.getDetails())
                .contains("\"type\":\"TAGS\"")
                .contains("\"name\":\"Female Vocals\"");
    }

    @Test
    void renameAuditsTheOldAndNewName() {
        Tag jazz = tag(1L, TagType.GENRE, "Jazz");
        given(tagRepository.findById(1L)).willReturn(Optional.of(jazz));
        given(tagRepository.countSongsWithTag(1L)).willReturn(0L);
        given(tagRepository.findByTypeAndName(TagType.GENRE, "Pop")).willReturn(Optional.empty());
        given(tagRepository.save(jazz)).willReturn(jazz);

        service.rename(1L, "pop", 9L);

        AuditLog row = savedAudit();
        assertThat(row.getAction()).isEqualTo(AuditLog.ACTION_TAG_RENAME);
        assertThat(row.getDetails())
                .contains("\"from\":\"Jazz\"")
                .contains("\"to\":\"Pop\"");
    }

    @Test
    void renameIsRefusedWhenTheTagIsOnASong() {
        Tag pop = tag(1L, TagType.GENRE, "Pop");
        given(tagRepository.findById(1L)).willReturn(Optional.of(pop));
        given(tagRepository.countSongsWithTag(1L)).willReturn(4L);

        assertThatThrownBy(() -> service.rename(1L, "Jazz", 9L))
                .isInstanceOf(TagVocabularyException.class)
                .hasMessageContaining("staged JSON");
        then(tagRepository).should(never()).save(any());
        then(auditLogRepository).should(never()).save(any());
    }

    @Test
    void deleteRemovesAnUnusedRowAndAudits() {
        Tag unused = tag(2L, TagType.TAGS, "Female Vocals");
        given(tagRepository.findById(2L)).willReturn(Optional.of(unused));
        given(tagRepository.countSongsWithTag(2L)).willReturn(0L);

        service.delete(2L, 9L);

        then(tagRepository).should().delete(unused);
        AuditLog row = savedAudit();
        assertThat(row.getAction()).isEqualTo(AuditLog.ACTION_TAG_DELETE);
        assertThat(row.getEntityId()).isEqualTo(2L);
        assertThat(row.getDetails()).contains("\"name\":\"Female Vocals\"");
    }

    private AuditLog savedAudit() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        then(auditLogRepository).should().save(captor.capture());
        return captor.getValue();
    }

    private static Tag tag(Long id, TagType type, String name) {
        Tag tag = new Tag(type, name);
        ReflectionTestUtils.setField(tag, "id", id);
        return tag;
    }
}
