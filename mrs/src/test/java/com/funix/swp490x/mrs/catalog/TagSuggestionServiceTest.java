package com.funix.swp490x.mrs.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.funix.swp490x.mrs.domain.Tag;
import com.funix.swp490x.mrs.domain.TagType;
import com.funix.swp490x.mrs.repository.TagRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TagSuggestionServiceTest {

    @Mock
    private TagRepository tagRepository;

    @Test
    void genresComeFromMusicBrainzAndTagsFromMysql() {
        given(tagRepository.findByTypeOrderByName(TagType.TAGS))
                .willReturn(List.of(new Tag(TagType.TAGS, "Female Vocals"),
                        new Tag(TagType.TAGS, "Lead Vocals")));

        TagSuggestionService service = new TagSuggestionService(tagRepository);

        assertThat(service.suggest(TagType.GENRE, "dub", 8)).isNotEmpty()
                .allMatch(name -> name.toLowerCase().contains("dub"));
        assertThat(service.suggest(TagType.TAGS, "voc", 8))
                .containsExactly("Female Vocals", "Lead Vocals");
    }
}
