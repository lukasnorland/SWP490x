package com.funix.swp490x.mrs.web;

import com.funix.swp490x.mrs.domain.Playlist;
import com.funix.swp490x.mrs.domain.PlaylistSong;
import com.funix.swp490x.mrs.domain.PlaylistStatus;
import com.funix.swp490x.mrs.security.MrsUserDetails;
import com.funix.swp490x.mrs.service.DuplicateCollaboratorException;
import com.funix.swp490x.mrs.service.DuplicatePlaylistNameException;
import com.funix.swp490x.mrs.service.InvalidCollaboratorException;
import com.funix.swp490x.mrs.service.InvalidPlaylistStateException;
import com.funix.swp490x.mrs.service.PendingEdit;
import com.funix.swp490x.mrs.service.PlaylistLockedException;
import com.funix.swp490x.mrs.service.PlaylistNotFoundException;
import com.funix.swp490x.mrs.service.PlaylistService;
import com.funix.swp490x.mrs.service.PlaylistSummary;
import com.funix.swp490x.mrs.service.SongNotFoundException;
import com.funix.swp490x.mrs.service.StalePlaylistException;
import jakarta.servlet.http.HttpServletResponse;
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
 *
 * <p>The exception is a version conflict: every mutation submits the
 * {@code expectedVersion} its screen was rendered at, and a stale one renders
 * the conflict screen with HTTP 409 instead of redirecting, so the status
 * survives and the rejected change is still in hand to clone (UC-19, BR-11).
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
        model.addAttribute("canPublish", !playlist.isPublished() && (owner || admin));
        model.addAttribute("canUnpublish", playlist.isPublished() && (owner || admin));
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
            @RequestParam int expectedVersion,
            @RequestParam String name,
            @RequestParam(required = false) String returnTo,
            Model model,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes) {

        try {
            playlistService.rename(id, expectedVersion, name, userId(user));
            flash(redirectAttributes, "success", Messages.PLAYLIST_RENAMED);
        } catch (StalePlaylistException e) {
            return conflict(model, response, e, PendingEdit.rename(name), returnTo);
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
            @RequestParam int expectedVersion,
            @RequestParam List<Long> songId,
            @RequestParam(required = false) String returnTo,
            Model model,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes) {

        try {
            int requested = (int) songId.stream().filter(one -> one != null).count();
            int added = playlistService.addSongs(id, expectedVersion, songId, userId(user));
            if (requested == 0) {
                flash(redirectAttributes, "danger", Messages.SONG_NOT_FOUND);
            } else if (added == 0) {
                flash(redirectAttributes, "warning", Messages.SONG_ALREADY_IN_PLAYLIST);
            } else if (added == 1 && requested == 1) {
                flash(redirectAttributes, "success", Messages.SONG_ADDED_TO_PLAYLIST);
            } else {
                flash(redirectAttributes, "success", Messages.SONGS_ADDED_TO_PLAYLIST);
            }
        } catch (StalePlaylistException e) {
            return conflict(model, response, e, PendingEdit.addSongs(songId), returnTo);
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
            @RequestParam int expectedVersion,
            Model model,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes) {

        try {
            playlistService.removeSong(id, expectedVersion, songId, userId(user));
            flash(redirectAttributes, "success", Messages.SONG_REMOVED_FROM_PLAYLIST);
        } catch (StalePlaylistException e) {
            return conflict(model, response, e, PendingEdit.removeSong(songId),
                    Routes.PLAYLISTS + "/" + id);
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
            @RequestParam int expectedVersion,
            @RequestParam String direction,
            Model model,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes) {

        boolean up = "up".equalsIgnoreCase(direction);
        try {
            playlistService.move(id, expectedVersion, songId, up, userId(user));
        } catch (StalePlaylistException e) {
            return conflict(model, response, e, PendingEdit.moveSong(songId, up),
                    Routes.PLAYLISTS + "/" + id);
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
            @RequestParam int expectedVersion,
            Model model,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes) {

        try {
            playlistService.delete(id, expectedVersion, userId(user));
            flash(redirectAttributes, "success", Messages.PLAYLIST_DELETED);
        } catch (StalePlaylistException e) {
            return conflict(model, response, e, PendingEdit.of(PendingEdit.Kind.DELETE),
                    Routes.PLAYLISTS + "/" + id);
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
            @RequestParam int expectedVersion,
            @RequestParam(required = false) String returnTo,
            Model model,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes) {

        try {
            playlistService.publish(id, expectedVersion, userId(user));
            flash(redirectAttributes, "success", Messages.PLAYLIST_PUBLISHED);
        } catch (StalePlaylistException e) {
            return conflict(model, response, e, PendingEdit.of(PendingEdit.Kind.PUBLISH), returnTo);
        } catch (InvalidCollaboratorException e) {
            flash(redirectAttributes, "warning", Messages.PLAYLIST_PUBLISH_NOT_OWNER);
            return "redirect:" + Routes.PLAYLISTS + "/" + id;
        } catch (InvalidPlaylistStateException e) {
            flash(redirectAttributes, "warning", Messages.PLAYLIST_PUBLISH_EMPTY);
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

    @PostMapping(Routes.PLAYLIST_UNPUBLISH)
    public String unpublish(@PathVariable Long id,
            @AuthenticationPrincipal MrsUserDetails user,
            @RequestParam int expectedVersion,
            @RequestParam(required = false) String returnTo,
            Model model,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes) {

        try {
            playlistService.unpublish(id, expectedVersion, userId(user));
            flash(redirectAttributes, "success", Messages.PLAYLIST_UNPUBLISHED);
        } catch (StalePlaylistException e) {
            return conflict(model, response, e,
                    PendingEdit.of(PendingEdit.Kind.UNPUBLISH), returnTo);
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
            @RequestParam int expectedVersion,
            @RequestParam Long userId,
            @RequestParam(required = false) String returnTo,
            Model model,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes) {

        try {
            playlistService.grant(id, expectedVersion, userId, userId(user));
            flash(redirectAttributes, "success", Messages.COLLABORATOR_ADDED);
        } catch (StalePlaylistException e) {
            return conflict(model, response, e, PendingEdit.of(PendingEdit.Kind.GRANT), returnTo);
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
            @RequestParam int expectedVersion,
            @RequestParam(required = false) String returnTo,
            Model model,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes) {

        try {
            playlistService.revoke(id, expectedVersion, userId, userId(user));
            flash(redirectAttributes, "success", Messages.COLLABORATOR_REMOVED);
        } catch (StalePlaylistException e) {
            return conflict(model, response, e, PendingEdit.of(PendingEdit.Kind.REVOKE), returnTo);
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

    /**
     * The clone half of the conflict screen (UC-19 A2): the change that was
     * just rejected is reapplied on a copy the requester owns, and the playlist
     * they collided with is left alone.
     */
    @PostMapping(Routes.PLAYLIST_CONFLICT_CLONE)
    public String cloneOnConflict(@PathVariable Long id,
            @AuthenticationPrincipal MrsUserDetails user,
            @RequestParam String name,
            @RequestParam PendingEdit.Kind kind,
            @RequestParam(required = false) List<Long> songId,
            @RequestParam(defaultValue = "false") boolean up,
            @RequestParam(required = false) String returnTo,
            Model model,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes) {

        PendingEdit pending = new PendingEdit(kind, songId, up, null);
        if (!pending.isCloneable()) {
            // Only the form for a content edit renders a clone button; a hand
            // built post for publish or delete has nothing to carry over.
            throw new PlaylistNotFoundException(id);
        }
        try {
            Playlist created = playlistService.cloneOnConflict(id, name, userId(user), pending);
            flash(redirectAttributes, "success", Messages.PLAYLIST_CLONED_FROM_CONFLICT);
            return "redirect:" + Routes.PLAYLISTS + "/" + created.getId();
        } catch (InvalidPlaylistStateException e) {
            return conflictAgain(model, response, id, pending, returnTo, name,
                    Messages.PLAYLIST_NAME_REQUIRED);
        } catch (DuplicatePlaylistNameException e) {
            return conflictAgain(model, response, id, pending, returnTo, name,
                    Messages.PLAYLIST_NAME_TAKEN);
        } catch (SongNotFoundException e) {
            // The song the change was about has left the catalog since. Nothing
            // was created, so the choice is still open.
            return conflictAgain(model, response, id, pending, returnTo, name,
                    Messages.SONG_NOT_FOUND);
        }
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

    /**
     * Renders the conflict screen instead of redirecting, because a redirect
     * would drop both the 409 and the rejected change the clone button needs
     * (UC-19 normal flow, MSG_014).
     *
     * <p>Reads the playlist with {@link PlaylistService#inspect}: the service
     * settles who may touch it before it compares versions, so anything that
     * reaches here is already allowed to see it.
     */
    private String conflict(Model model, HttpServletResponse response,
            StalePlaylistException e, PendingEdit pending, String returnTo) {

        response.setStatus(HttpStatus.CONFLICT.value());
        Playlist playlist = playlistService.inspect(e.getPlaylistId());

        model.addAttribute("pageTitle", "Playlist changed");
        model.addAttribute("activeNav", "playlists");
        model.addAttribute("breadcrumbParent", "My Playlists");
        model.addAttribute("breadcrumbParentUrl", Routes.PLAYLISTS);
        model.addAttribute("flash", Messages.PLAYLIST_STALE);
        model.addAttribute("flashVariant", "warning");
        model.addAttribute("playlist", playlist);
        model.addAttribute("expectedVersion", e.getExpectedVersion());
        model.addAttribute("pending", pending);
        model.addAttribute("suggestedName", suggestedCloneName(playlist, pending));
        model.addAttribute("returnTo", safeReturn(returnTo));
        return "playlist/conflict";
    }

    /** Back to the conflict screen when the name the clone asked for is unusable. */
    private String conflictAgain(Model model, HttpServletResponse response, Long id,
            PendingEdit pending, String returnTo, String name, String message) {

        response.setStatus(HttpStatus.CONFLICT.value());
        Playlist playlist = playlistService.inspect(id);

        model.addAttribute("pageTitle", "Playlist changed");
        model.addAttribute("activeNav", "playlists");
        model.addAttribute("breadcrumbParent", "My Playlists");
        model.addAttribute("breadcrumbParentUrl", Routes.PLAYLISTS);
        model.addAttribute("flash", message);
        model.addAttribute("flashVariant", "warning");
        model.addAttribute("playlist", playlist);
        model.addAttribute("expectedVersion", playlist.getVersion());
        model.addAttribute("pending", pending);
        model.addAttribute("suggestedName", name);
        model.addAttribute("returnTo", safeReturn(returnTo));
        return "playlist/conflict";
    }

    /**
     * A rename carries its own name; everything else copies under "(copy)".
     * Playlist names are unique system-wide, so this is a starting point the
     * user can edit rather than a name that is guaranteed to be free.
     */
    private static String suggestedCloneName(Playlist playlist, PendingEdit pending) {
        if (pending.kind() == PendingEdit.Kind.RENAME && pending.name() != null
                && !pending.name().isBlank()) {
            return pending.name().trim();
        }
        String suffix = " (copy)";
        String base = playlist.getName();
        return base.length() + suffix.length() <= 200
                ? base + suffix
                : base.substring(0, 200 - suffix.length()) + suffix;
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
