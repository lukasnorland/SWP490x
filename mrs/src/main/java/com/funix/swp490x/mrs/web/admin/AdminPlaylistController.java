package com.funix.swp490x.mrs.web.admin;

import com.funix.swp490x.mrs.domain.Playlist;
import com.funix.swp490x.mrs.domain.PlaylistSong;
import com.funix.swp490x.mrs.domain.PlaylistStatus;
import com.funix.swp490x.mrs.service.PlaylistOwner;
import com.funix.swp490x.mrs.service.PlaylistService;
import com.funix.swp490x.mrs.service.PlaylistSummary;
import com.funix.swp490x.mrs.web.Routes;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * P-06f — All Playlists. Read-only oversight of every playlist in the system,
 * Draft or Published, whoever owns it.
 *
 * <p>Access is granted by SecurityConfig on {@code /admin/**}. List and inspect
 * do not mutate here; ADMIN grants collaborators from inspect through the
 * shared playlist routes.
 */
@Controller
public class AdminPlaylistController {

    private final PlaylistService playlistService;

    public AdminPlaylistController(PlaylistService playlistService) {
        this.playlistService = playlistService;
    }

    @GetMapping(Routes.ADMIN_PLAYLISTS)
    public String list(@RequestParam(required = false) Long ownerId,
            @RequestParam(required = false) PlaylistStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            Model model) {

        Page<PlaylistSummary> playlists = playlistService.searchAll(ownerId, status, q, page);
        List<PlaylistOwner> owners = playlistService.playlistOwners();

        model.addAttribute("pageTitle", "All Playlists");
        model.addAttribute("activeNav", "admin-playlists");
        model.addAttribute("playlists", playlists);
        model.addAttribute("owners", owners);
        model.addAttribute("filterOwnerId", ownerId);
        model.addAttribute("filterStatus", status == null ? null : status.name());
        model.addAttribute("filterQuery", q == null ? "" : q);
        return "admin/playlists";
    }

    /** The P-03b detail rendered in inspection mode: no editor, no export, owner shown. */
    @GetMapping(Routes.ADMIN_PLAYLIST)
    public String detail(@PathVariable Long id, Model model) {
        Playlist playlist = playlistService.inspect(id);
        List<PlaylistSong> entries = playlistService.inspectSongs(id);

        model.addAttribute("pageTitle", playlist.getName());
        model.addAttribute("activeNav", "admin-playlists");
        model.addAttribute("breadcrumbParent", "All Playlists");
        model.addAttribute("breadcrumbParentUrl", Routes.ADMIN_PLAYLISTS);
        model.addAttribute("playlist", playlist);
        model.addAttribute("entries", entries);
        model.addAttribute("totalDuration", playlistService.totalDuration(id));
        model.addAttribute("ownerName", playlistService.ownerName(playlist.getOwnerId()));
        model.addAttribute("canEdit", false);
        model.addAttribute("canDelete", false);
        model.addAttribute("canManageCollaborators", true);
        model.addAttribute("collaborators", playlistService.collaborators(id));
        model.addAttribute("inviteCandidates", playlistService.inviteCandidates(id));
        model.addAttribute("collaboratorReturnTo", Routes.ADMIN_PLAYLISTS + "/" + id);
        model.addAttribute("inspecting", true);
        return "playlist/detail";
    }
}
