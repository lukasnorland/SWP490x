package com.funix.swp490x.mrs.web;

import com.funix.swp490x.mrs.domain.Playlist;
import com.funix.swp490x.mrs.domain.PlaylistSong;
import com.funix.swp490x.mrs.domain.PlaylistStatus;
import com.funix.swp490x.mrs.security.MrsUserDetails;
import com.funix.swp490x.mrs.service.DuplicateCollaboratorException;
import com.funix.swp490x.mrs.service.DuplicatePlaylistNameException;
import com.funix.swp490x.mrs.service.DuplicatePlaylistSongException;
import com.funix.swp490x.mrs.service.InvalidCollaboratorException;
import com.funix.swp490x.mrs.service.InvalidPlaylistStateException;
import com.funix.swp490x.mrs.service.PlaylistLockedException;
import com.funix.swp490x.mrs.service.PlaylistNotFoundException;
import com.funix.swp490x.mrs.service.PlaylistService;
import com.funix.swp490x.mrs.service.PlaylistSummary;
import com.funix.swp490x.mrs.service.SongNotFoundException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * P-03a My Playlists and P-03b Playlist Detail / Editor (FT-06 – FT-08).
 *
 * <p>Every mutation redirects with a flash rather than re-rendering, so a
 * refresh cannot repeat it. The Add-to-playlist dialog is opened from the Songs
 * table, so add and create-and-add carry a {@code returnTo} and send the user
 * back to the row they came from.
 */
@Controller
public class PlaylistController {

    /**
     * Screens the Add-to-playlist dialog can be opened from. A redirect target
     * is only honoured when it starts with one of these, so a crafted
     * {@code returnTo} cannot turn a POST into an open redirect.
     */
    private static final List<String> RETURN_ALLOWLIST =
            List.of(Routes.SONGS, Routes.ADMIN_CATALOG, Routes.SEARCH, Routes.PLAYLISTS,
                    Routes.WORKSPACE, Routes.ADMIN_PLAYLISTS);

    private final PlaylistService playlistService;

    public PlaylistController(PlaylistService playlistService) {
        this.playlistService = playlistService;
    }

    @GetMapping(Routes.PLAYLISTS)
    public String list(@AuthenticationPrincipal MrsUserDetails user,
            @RequestParam(required = false) PlaylistStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            Model model) {

        Page<PlaylistSummary> playlists = playlistService.search(userId(user), status, q, page);

        model.addAttribute("pageTitle", "My Playlists");
        model.addAttribute("activeNav", "playlists");
        model.addAttribute("playlists", playlists);
        // Echoed back so the filter form and the pager keep the current query.
        model.addAttribute("filterStatus", status == null ? null : status.name());
        model.addAttribute("filterQuery", q == null ? "" : q);
        return "playlist/list";
    }

    @GetMapping(Routes.PLAYLIST)
    public String detail(@PathVariable Long id,
            @AuthenticationPrincipal MrsUserDetails user,
            Model model) {

        Playlist playlist = playlistService.view(id, userId(user));
        List<PlaylistSong> entries = playlistService.songs(id, userId(user));

        model.addAttribute("pageTitle", playlist.getName());
        model.addAttribute("activeNav", "playlists");
        model.addAttribute("breadcrumbParent", "My Playlists");
        model.addAttribute("breadcrumbParentUrl", Routes.PLAYLISTS);
        model.addAttribute("playlist", playlist);
        model.addAttribute("entries", entries);
        model.addAttribute("totalDuration", playlistService.totalDuration(id));
        boolean owner = user != null && playlist.getOwnerId().equals(user.getId());
        boolean admin = user != null && user.isAdmin();
        model.addAttribute("canEdit", !playlist.isPublished()
                && user != null && user.isCurator());
        model.addAttribute("canPublish", !playlist.isPublished() && owner);
        model.addAttribute("canUnpublish", playlist.isPublished() && owner);
        model.addAttribute("canDelete", !playlist.isPublished() && owner);
        model.addAttribute("canManageCollaborators", owner || admin);
        model.addAttribute("collaborators", playlistService.collaborators(id));
        model.addAttribute("inviteCandidates",
                owner || admin ? playlistService.inviteCandidates(id) : List.of());
        model.addAttribute("collaboratorReturnTo", Routes.PLAYLISTS + "/" + id);
        return "playlist/detail";
    }

    /**
     * New playlist, optionally with the song the dialog was opened from so the
     * whole loop stays inside one action (NFR-U01).
     */
    @PostMapping(Routes.PLAYLISTS)
    public String create(@AuthenticationPrincipal MrsUserDetails user,
            @RequestParam String name,
            @RequestParam(required = false) List<Long> songId,
            @RequestParam(required = false) String returnTo,
            RedirectAttributes redirectAttributes) {

        try {
            if (songId == null || songId.isEmpty()) {
                Playlist created = playlistService.create(userId(user), name);
                flash(redirectAttributes, "success", Messages.PLAYLIST_CREATED);
                return "redirect:" + Routes.PLAYLISTS + "/" + created.getId();
            }
            if (songId.size() == 1) {
                playlistService.createWithSong(userId(user), name, songId.get(0));
                flash(redirectAttributes, "success", Messages.PLAYLIST_CREATED_WITH_SONG);
            } else {
                playlistService.createWithSongs(userId(user), name, songId);
                flash(redirectAttributes, "success", Messages.SONGS_ADDED_TO_PLAYLIST);
            }
        } catch (InvalidPlaylistStateException e) {
            flash(redirectAttributes, "warning", Messages.PLAYLIST_NAME_REQUIRED);
        } catch (DuplicatePlaylistNameException e) {
            flash(redirectAttributes, "warning", Messages.PLAYLIST_NAME_TAKEN);
        } catch (SongNotFoundException e) {
            flash(redirectAttributes, "danger", Messages.SONG_NOT_FOUND);
        }
        return "redirect:" + safeReturn(returnTo);
    }

    @PostMapping(Routes.PLAYLIST_RENAME)
    public String rename(@PathVariable Long id,
            @AuthenticationPrincipal MrsUserDetails user,
            @RequestParam String name,
            @RequestParam(required = false) String returnTo,
            RedirectAttributes redirectAttributes) {

        try {
            playlistService.rename(id, name, userId(user));
            flash(redirectAttributes, "success", Messages.PLAYLIST_RENAMED);
        } catch (InvalidPlaylistStateException e) {
            flash(redirectAttributes, "warning", Messages.PLAYLIST_NAME_REQUIRED);
        } catch (DuplicatePlaylistNameException e) {
            flash(redirectAttributes, "warning", Messages.PLAYLIST_NAME_TAKEN);
        } catch (PlaylistLockedException e) {
            flash(redirectAttributes, "warning", Messages.PLAYLIST_LOCKED);
        } catch (PlaylistNotFoundException e) {
            flash(redirectAttributes, "danger", Messages.PLAYLIST_NOT_FOUND);
            return "redirect:" + Routes.PLAYLISTS;
        }
        return "redirect:" + (returnTo == null || returnTo.isBlank()
                ? Routes.PLAYLISTS + "/" + id
                : safeReturn(returnTo));
    }

    @PostMapping(Routes.PLAYLIST_DUPLICATE)
    public String duplicate(@PathVariable Long id,
            @AuthenticationPrincipal MrsUserDetails user,
            @RequestParam String name,
            @RequestParam(required = false) String returnTo,
            RedirectAttributes redirectAttributes) {

        try {
            Playlist created = playlistService.duplicate(id, name, userId(user));
            flash(redirectAttributes, "success", Messages.PLAYLIST_DUPLICATED);
            return "redirect:" + Routes.PLAYLISTS + "/" + created.getId();
        } catch (InvalidPlaylistStateException e) {
            flash(redirectAttributes, "warning", Messages.PLAYLIST_NAME_REQUIRED);
        } catch (DuplicatePlaylistNameException e) {
            flash(redirectAttributes, "warning", Messages.PLAYLIST_NAME_TAKEN);
        } catch (PlaylistNotFoundException e) {
            flash(redirectAttributes, "danger", Messages.PLAYLIST_NOT_FOUND);
            return "redirect:" + Routes.PLAYLISTS;
        }
        return "redirect:" + (returnTo == null || returnTo.isBlank()
                ? Routes.PLAYLISTS + "/" + id
                : safeReturn(returnTo));
    }

    @PostMapping(Routes.PLAYLIST_SONGS)
    public String addSong(@PathVariable Long id,
            @AuthenticationPrincipal MrsUserDetails user,
            @RequestParam List<Long> songId,
            @RequestParam(required = false) String returnTo,
            RedirectAttributes redirectAttributes) {

        try {
            int added = 0;
            boolean duplicate = false;
            for (Long one : songId) {
                if (one == null) {
                    continue;
                }
                try {
                    playlistService.addSong(id, one, userId(user));
                    added++;
                } catch (DuplicatePlaylistSongException e) {
                    duplicate = true;
                }
            }
            if (added == 0 && duplicate) {
                flash(redirectAttributes, "warning", Messages.SONG_ALREADY_IN_PLAYLIST);
            } else if (added == 1 && !duplicate) {
                flash(redirectAttributes, "success", Messages.SONG_ADDED_TO_PLAYLIST);
            } else if (added > 0) {
                flash(redirectAttributes, "success", Messages.SONGS_ADDED_TO_PLAYLIST);
            } else {
                flash(redirectAttributes, "danger", Messages.SONG_NOT_FOUND);
            }
        } catch (PlaylistLockedException e) {
            flash(redirectAttributes, "warning", Messages.PLAYLIST_LOCKED);
        } catch (PlaylistNotFoundException e) {
            flash(redirectAttributes, "danger", Messages.PLAYLIST_NOT_FOUND);
        } catch (SongNotFoundException e) {
            flash(redirectAttributes, "danger", Messages.SONG_NOT_FOUND);
        }
        return "redirect:" + safeReturn(returnTo);
    }

    @PostMapping(Routes.PLAYLIST_SONG_REMOVE)
    public String removeSong(@PathVariable Long id,
            @PathVariable Long songId,
            @AuthenticationPrincipal MrsUserDetails user,
            RedirectAttributes redirectAttributes) {

        try {
            playlistService.removeSong(id, songId, userId(user));
            flash(redirectAttributes, "success", Messages.SONG_REMOVED_FROM_PLAYLIST);
        } catch (PlaylistLockedException e) {
            flash(redirectAttributes, "warning", Messages.PLAYLIST_LOCKED);
        } catch (PlaylistNotFoundException e) {
            flash(redirectAttributes, "danger", Messages.PLAYLIST_NOT_FOUND);
            return "redirect:" + Routes.PLAYLISTS;
        } catch (SongNotFoundException e) {
            flash(redirectAttributes, "danger", Messages.SONG_NOT_FOUND);
        }
        return "redirect:" + Routes.PLAYLISTS + "/" + id;
    }

    @PostMapping(Routes.PLAYLIST_SONG_MOVE)
    public String moveSong(@PathVariable Long id,
            @PathVariable Long songId,
            @AuthenticationPrincipal MrsUserDetails user,
            @RequestParam String direction,
            RedirectAttributes redirectAttributes) {

        try {
            playlistService.move(id, songId, "up".equalsIgnoreCase(direction), userId(user));
        } catch (PlaylistLockedException e) {
            flash(redirectAttributes, "warning", Messages.PLAYLIST_LOCKED);
        } catch (PlaylistNotFoundException e) {
            flash(redirectAttributes, "danger", Messages.PLAYLIST_NOT_FOUND);
            return "redirect:" + Routes.PLAYLISTS;
        } catch (SongNotFoundException e) {
            flash(redirectAttributes, "danger", Messages.SONG_NOT_FOUND);
        }
        return "redirect:" + Routes.PLAYLISTS + "/" + id;
    }

    @PostMapping(Routes.PLAYLIST_DELETE)
    public String delete(@PathVariable Long id,
            @AuthenticationPrincipal MrsUserDetails user,
            RedirectAttributes redirectAttributes) {

        try {
            playlistService.delete(id, userId(user));
            flash(redirectAttributes, "success", Messages.PLAYLIST_DELETED);
        } catch (InvalidCollaboratorException e) {
            flash(redirectAttributes, "warning", Messages.PLAYLIST_DELETE_NOT_OWNER);
            return "redirect:" + Routes.PLAYLISTS + "/" + id;
        } catch (InvalidPlaylistStateException e) {
            flash(redirectAttributes, "warning", Messages.PLAYLIST_DELETE_PUBLISHED);
        } catch (PlaylistNotFoundException e) {
            flash(redirectAttributes, "danger", Messages.PLAYLIST_NOT_FOUND);
        }
        return "redirect:" + Routes.PLAYLISTS;
    }

    @PostMapping(Routes.PLAYLIST_PUBLISH)
    public String publish(@PathVariable Long id,
            @AuthenticationPrincipal MrsUserDetails user,
            RedirectAttributes redirectAttributes) {

        try {
            playlistService.publish(id, userId(user));
            flash(redirectAttributes, "success", Messages.PLAYLIST_PUBLISHED);
        } catch (InvalidCollaboratorException e) {
            flash(redirectAttributes, "warning", Messages.PLAYLIST_PUBLISH_NOT_OWNER);
        } catch (InvalidPlaylistStateException e) {
            flash(redirectAttributes, "warning", Messages.PLAYLIST_PUBLISH_EMPTY);
        } catch (PlaylistLockedException e) {
            flash(redirectAttributes, "warning", Messages.PLAYLIST_LOCKED);
        } catch (PlaylistNotFoundException e) {
            flash(redirectAttributes, "danger", Messages.PLAYLIST_NOT_FOUND);
            return "redirect:" + Routes.PLAYLISTS;
        }
        return "redirect:" + Routes.PLAYLISTS + "/" + id;
    }

    @PostMapping(Routes.PLAYLIST_UNPUBLISH)
    public String unpublish(@PathVariable Long id,
            @AuthenticationPrincipal MrsUserDetails user,
            @RequestParam(required = false) String returnTo,
            RedirectAttributes redirectAttributes) {

        try {
            playlistService.unpublish(id, userId(user));
            flash(redirectAttributes, "success", Messages.PLAYLIST_UNPUBLISHED);
        } catch (InvalidCollaboratorException e) {
            flash(redirectAttributes, "warning", Messages.PLAYLIST_UNPUBLISH_NOT_OWNER);
            return "redirect:" + Routes.PLAYLISTS + "/" + id;
        } catch (PlaylistNotFoundException e) {
            flash(redirectAttributes, "danger", Messages.PLAYLIST_NOT_FOUND);
            return "redirect:" + Routes.PLAYLISTS;
        }
        if (returnTo != null && !returnTo.isBlank()) {
            return "redirect:" + safeReturn(returnTo);
        }
        return "redirect:" + Routes.PLAYLISTS + "/" + id;
    }

    @PostMapping(Routes.PLAYLIST_COLLABORATORS)
    public String grant(@PathVariable Long id,
            @AuthenticationPrincipal MrsUserDetails user,
            @RequestParam Long userId,
            @RequestParam(required = false) String returnTo,
            RedirectAttributes redirectAttributes) {

        try {
            playlistService.grant(id, userId, userId(user));
            flash(redirectAttributes, "success", Messages.COLLABORATOR_ADDED);
        } catch (DuplicateCollaboratorException e) {
            flash(redirectAttributes, "warning", Messages.COLLABORATOR_ALREADY);
        } catch (InvalidCollaboratorException e) {
            flash(redirectAttributes, "warning",
                    e.getMessage() != null && e.getMessage().contains("Only the owner")
                            ? Messages.COLLABORATOR_MANAGE_OWNER_ONLY
                            : Messages.COLLABORATOR_INVALID);
        } catch (PlaylistNotFoundException e) {
            flash(redirectAttributes, "danger", Messages.PLAYLIST_NOT_FOUND);
            return "redirect:" + Routes.PLAYLISTS;
        }
        return "redirect:" + (returnTo == null || returnTo.isBlank()
                ? Routes.PLAYLISTS + "/" + id
                : safeReturn(returnTo));
    }

    @PostMapping(Routes.PLAYLIST_COLLABORATOR_REMOVE)
    public String revoke(@PathVariable Long id,
            @PathVariable Long userId,
            @AuthenticationPrincipal MrsUserDetails user,
            @RequestParam(required = false) String returnTo,
            RedirectAttributes redirectAttributes) {

        try {
            playlistService.revoke(id, userId, userId(user));
            flash(redirectAttributes, "success", Messages.COLLABORATOR_REMOVED);
        } catch (InvalidCollaboratorException e) {
            flash(redirectAttributes, "warning",
                    e.getMessage() != null && e.getMessage().contains("Only the owner")
                            ? Messages.COLLABORATOR_MANAGE_OWNER_ONLY
                            : Messages.COLLABORATOR_INVALID);
        } catch (PlaylistNotFoundException e) {
            flash(redirectAttributes, "danger", Messages.PLAYLIST_NOT_FOUND);
            return "redirect:" + Routes.PLAYLISTS;
        }
        return "redirect:" + (returnTo == null || returnTo.isBlank()
                ? Routes.PLAYLISTS + "/" + id
                : safeReturn(returnTo));
    }

    @GetMapping(Routes.PLAYLIST_EXPORT)
    public ResponseEntity<byte[]> export(@PathVariable Long id,
            @AuthenticationPrincipal MrsUserDetails user) {

        if (user == null || !user.isCurator()) {
            throw new PlaylistNotFoundException(id);
        }
        Playlist playlist = playlistService.viewExportable(id, userId(user));
        byte[] csv = playlistService.exportCsv(id, userId(user)).getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileName(playlist) + "\"")
                .body(csv);
    }

    /**
     * A playlist the caller may not see reads as missing rather than forbidden,
     * so a stranger cannot probe for which ids exist.
     */
    @ExceptionHandler(PlaylistNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound() {
        return "error/404";
    }

    private static Long userId(MrsUserDetails user) {
        return user == null ? null : user.getId();
    }

    /**
     * The dialog is opened from more than one screen, so the caller names where
     * to go back to. Anything outside {@link #RETURN_ALLOWLIST} falls back to
     * My Playlists rather than being followed.
     */
    private static String safeReturn(String returnTo) {
        if (returnTo == null || returnTo.isBlank() || returnTo.contains("//")) {
            return Routes.PLAYLISTS;
        }
        for (String allowed : RETURN_ALLOWLIST) {
            if (returnTo.equals(allowed) || returnTo.startsWith(allowed + "?")
                    || returnTo.startsWith(allowed + "/")) {
                return returnTo;
            }
        }
        return Routes.PLAYLISTS;
    }

    /** ASCII-safe, so no Content-Disposition encoding dance is needed. */
    private static String fileName(Playlist playlist) {
        String slug = playlist.getName().replaceAll("[^A-Za-z0-9._-]+", "-")
                .replaceAll("^-+|-+$", "");
        return (slug.isEmpty() ? "playlist-" + playlist.getId() : slug) + ".csv";
    }

    private void flash(RedirectAttributes redirectAttributes, String variant, String message) {
        redirectAttributes.addFlashAttribute("flash", message);
        redirectAttributes.addFlashAttribute("flashVariant", variant);
    }
}
