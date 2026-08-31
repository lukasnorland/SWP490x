package com.funix.swp490x.mrs.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.funix.swp490x.mrs.domain.Tag;
import com.funix.swp490x.mrs.domain.TagType;
import com.funix.swp490x.mrs.repository.TagRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FilterMapperTest {

    @Mock
    private TagRepository tagRepository;

    private FilterMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new FilterMapper(tagRepository);
    }

    @Test
    void mapsKnownNamesAndDropsUnknowns() {
        given(tagRepository.findAllUsedOrderByTypeAscNameAsc()).willReturn(List.of(
                tag(1L, TagType.MOOD, "Energetic"),
                tag(2L, TagType.GENRE, "Pop")));

        FilterMapper.MappedFilters mapped = mapper.map(new InterpretedFilters(
                List.of("Pop", "Made Up Genre"),
                List.of("energetic"),
                List.of("Nobody"),
                List.of(),
                0.9));

        assertThat(mapped.genreIds()).containsExactly(2L);
        assertThat(mapped.moodIds()).containsExactly(1L);
        assertThat(mapped.artistIds()).isEmpty();
        assertThat(mapped.tagIds()).isEmpty();
    }

    private static Tag tag(Long id, TagType type, String name) {
        Tag tag = new Tag(type, name);
        ReflectionTestUtils.setField(tag, "id", id);
        return tag;
    }
}
