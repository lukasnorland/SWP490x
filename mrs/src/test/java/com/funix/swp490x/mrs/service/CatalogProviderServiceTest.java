package com.funix.swp490x.mrs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.funix.swp490x.mrs.catalog.CatalogObjectStore;
import com.funix.swp490x.mrs.catalog.CatalogProperties;
import com.funix.swp490x.mrs.domain.AuditLog;
import com.funix.swp490x.mrs.domain.CatalogProvider;
import com.funix.swp490x.mrs.domain.Song;
import com.funix.swp490x.mrs.repository.AuditLogRepository;
import com.funix.swp490x.mrs.repository.CatalogProviderRepository;
import com.funix.swp490x.mrs.repository.PlaylistRepository;
import com.funix.swp490x.mrs.repository.SongRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CatalogProviderServiceTest {

    @Mock
    private CatalogProviderRepository providerRepository;
    @Mock
    private SongRepository songRepository;
    @Mock
    private PlaylistRepository playlistRepository;
    @Mock
    private SongCatalogService songCatalogService;
    @Mock
    private CatalogObjectStore store;
    @Mock
    private AuditLogRepository auditLogRepository;

    private CatalogProviderService service;

    @BeforeEach
    void setUp() {
        service = new CatalogProviderService(providerRepository, songRepository,
                playlistRepository, songCatalogService, store, new CatalogProperties(),
                auditLogRepository);
    }

    @Test
    void createPersistsUniqueNameAndSlugAndAudits() {
        given(providerRepository.existsByNameIgnoreCase("Artlist")).willReturn(false);
        given(providerRepository.existsBySlugIgnoreCase("artlist")).willReturn(false);
        given(providerRepository.save(any(CatalogProvider.class))).willAnswer(invocation -> {
            CatalogProvider saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 9L);
            return saved;
        });

        CatalogProvider created = service.create("Artlist", "Artlist", 1L);

        assertThat(created.getName()).isEqualTo("Artlist");
        assertThat(created.getSlug()).isEqualTo("artlist");
        ArgumentCaptor<AuditLog> audit = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(audit.capture());
        assertThat(audit.getValue().getAction()).isEqualTo(AuditLog.ACTION_PROVIDER_CREATE);
        assertThat(audit.getValue().getEntityId()).isEqualTo(9L);
        assertThat(audit.getValue().getDetails()).contains("\"name\":\"Artlist\"")
                .contains("\"slug\":\"artlist\"");
    }

    @Test
    void createRejectsAMalformedSlug() {
        assertThatThrownBy(() -> service.create("Artlist", "Art List", 1L))
                .isInstanceOf(InvalidCatalogProviderException.class);
        verify(providerRepository, never()).save(any());
    }

    @Test
    void deleteRequiresTheTypedName() {
        CatalogProvider ncs = provider(3L, "NCS", "ncs");
        given(providerRepository.findById(3L)).willReturn(Optional.of(ncs));

        assertThatThrownBy(() -> service.delete(3L, "ncs", 1L))
                .isInstanceOf(ProviderDeleteNotConfirmedException.class);

        verify(songCatalogService, never()).delete(any());
        verify(providerRepository, never()).delete(any());
        verify(auditLogRepository, never()).save(any());
    }

    @Test
    void deleteCascadesSongsThenOrphanMediaThenTheRow() {
        CatalogProvider ncs = provider(3L, "NCS", "ncs");
        Song song = new Song();
        song.setId(11L);
        song.setTitle("Shine");
        song.setSourceProvider("NCS");
        given(providerRepository.findById(3L)).willReturn(Optional.of(ncs));
        given(songRepository.countBySourceProviderIgnoreCase("NCS")).willReturn(1L);
        given(playlistRepository.countPlaylistsContainingProvider("NCS")).willReturn(2L);
        given(songRepository.findBySourceProviderIgnoreCase("NCS")).willReturn(List.of(song));
        given(store.listKeys("song-data/audio/ncs/"))
                .willReturn(List.of("song-data/audio/ncs/orphan.mp3"));
        given(store.listKeys("song-data/artwork/ncs/")).willReturn(List.of());

        CatalogProviderService.ProviderImpact impact = service.delete(3L, "NCS", 1L);

        assertThat(impact.songCount()).isEqualTo(1L);
        assertThat(impact.playlistCount()).isEqualTo(2L);
        verify(songCatalogService).delete(11L);
        verify(store).deleteBinary("song-data/audio/ncs/orphan.mp3");
        verify(providerRepository).delete(ncs);
        ArgumentCaptor<AuditLog> audit = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(audit.capture());
        assertThat(audit.getValue().getAction()).isEqualTo(AuditLog.ACTION_PROVIDER_DELETE);
        assertThat(audit.getValue().getDetails())
                .contains("\"name\":\"NCS\"")
                .contains("\"songsRemoved\":1")
                .contains("\"playlistsAffected\":2");
    }

    private static CatalogProvider provider(long id, String name, String slug) {
        CatalogProvider provider = new CatalogProvider(name, slug);
        ReflectionTestUtils.setField(provider, "id", id);
        return provider;
    }
}
