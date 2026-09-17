package com.funix.swp490x.mrs;

import com.funix.swp490x.mrs.llm.InterpretedFilters;
import com.funix.swp490x.mrs.llm.LlmInterpreter;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Browser-test launcher on the test classpath only; never included in the release jar. */
public class Report52FixtureApplication {
    public static void main(String[] args) {
        SpringApplication.from(MrsApplication::main).with(Fixtures.class).run(args);
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Fixtures {
        @Bean
        @Primary
        LlmInterpreter report52Interpreter() {
            return (query, vocabulary) -> switch (query.trim()) {
                case "R52 Pop Happy" -> Optional.of(filters(List.of("Pop"), List.of("Happy"), List.of()));
                case "R52 Pop Happy Summer" -> Optional.of(filters(List.of("Pop"), List.of("Happy"), List.of("Summer")));
                case "R52 unused tag" -> Optional.of(filters(List.of(), List.of(), List.of("R52Unused")));
                case "R52 twenty one" -> Optional.of(filters(List.of(), List.of(), List.of("R52Page")));
                case "R52 eight matches" -> Optional.of(filters(List.of(), List.of(), List.of("R52Eight")));
                case "qzx wvu plmk" -> Optional.empty();
                default -> throw new IllegalArgumentException("Unknown Report 5.2 fixture prompt");
            };
        }

        private static InterpretedFilters filters(List<String> genres, List<String> moods, List<String> tags) {
            return new InterpretedFilters(genres, moods, List.of(), tags, null);
        }
    }
}
