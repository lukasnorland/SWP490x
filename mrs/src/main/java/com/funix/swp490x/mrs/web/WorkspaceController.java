package com.funix.swp490x.mrs.web;

import com.funix.swp490x.mrs.domain.Playlist;
import com.funix.swp490x.mrs.domain.PlaylistSong;
import com.funix.swp490x.mrs.security.MrsUserDetails;
import com.funix.swp490x.mrs.service.PlaylistNotFoundException;
import com.funix.swp490x.mrs.service.PlaylistService;
import com.funix.swp490x.mrs.service.PublishedOwner;
import com.funix.swp490x.mrs.service.PublishedPlaylistCard;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * P-04a Shared Workspace and P-04b Published Playlist View (FT-07, FT-08).
 *
 * <p>Publishing is what puts a playlist here. Drafts stay on My Playlists.
 */
@Controller
public class WorkspaceController {

    private final PlaylistService playlistService;

    public WorkspaceController(PlaylistService playlistService) {
        this.playlistService = playlistService;
    }

    @GetMapping(Routes.WORKSPACE)
    public String list(@RequestParam(required = false) String q,
            @RequestParam(required = false) Long ownerId,
            @RequestParam(defaultValue = "0") int page,
            Model model) {

        Page<PublishedPlaylistCard> playlists =
                playlistService.searchPublished(ownerId, q, page);
        List<PublishedOwner> owners = playlistService.publishedOwners();

        model.addAttribute("pageTitle", "Shared Workspace");
        model.addAttribute("activeNav", "workspace");
        model.addAttribute("playlists", playlists);
        model.addAttribute("owners", owners);
        model.addAttribute("filterQuery", q == null ? "" : q);
        model.addAttribute("filterOwnerId", ownerId);
        return "workspace/list";
    }

    @GetMapping(Routes.WORKSPACE_PLAYLIST)
    public String detail(@PathVariable Long id,
            @AuthenticationPrincipal MrsUserDetails user,
            Model model) {

        Playlist playlist = playlistService.viewPublished(id);
        List<PlaylistSong> entries = playlistService.publishedSongs(id);

        model.addAttribute("pageTitle", playlist.getName());
        model.addAttribute("activeNav", "workspace");
        model.addAttribute("breadcrumbParent", "Shared Workspace");
        model.addAttribute("breadcrumbParentUrl", Routes.WORKSPACE);
        model.addAttribute("playlist", playlist);
        model.addAttribute("ownerName", playlistService.ownerName(playlist.getOwnerId()));
        model.addAttribute("entries", entries);
        model.addAttribute("totalDuration", playlistService.totalDuration(id));
        model.addAttribute("canExport", user != null && user.isCurator());
        model.addAttribute("canUnpublish", user != null && user.isCurator()
                && (user.isAdmin() || playlist.getOwnerId().equals(user.getId())));
        return "workspace/detail";
    }

    /**
     * Spec 4.7: an unpublished or unknown id is a generic 403, not a 404 that
     * would confirm a Draft exists.
     */
    @ExceptionHandler(PlaylistNotFoundException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String forbidden() {
        return "error/403";
    }
}
