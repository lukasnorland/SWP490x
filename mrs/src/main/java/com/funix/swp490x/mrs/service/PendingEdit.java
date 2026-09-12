package com.funix.swp490x.mrs.service;

import java.util.List;

/**
 * The change a requester was making when the save was rejected as stale. It
 * rides through the conflict screen so "Clone as New Playlist" can reapply it
 * on the copy instead of asking the user to retype it (UC-19 A2, BR-11).
 */
public record PendingEdit(Kind kind, List<Long> songIds, boolean up, String name) {

    /**
     * Only a content edit can be cloned. BR-11 is about carrying the
     * requester's own in-progress work somewhere safe; publishing, deleting or
     * resharing a playlist someone else has already changed has nothing to
     * carry into a copy, so those offer refresh alone.
     */
    public enum Kind {
        ADD_SONG(true),
        REMOVE_SONG(true),
        MOVE_SONG(true),
        RENAME(true),
        PUBLISH(false),
        UNPUBLISH(false),
        DELETE(false),
        GRANT(false),
        REVOKE(false);

        private final boolean cloneable;

        Kind(boolean cloneable) {
            this.cloneable = cloneable;
        }

        public boolean isCloneable() {
            return cloneable;
        }
    }

    public PendingEdit {
        songIds = songIds == null ? List.of() : songIds.stream().filter(id -> id != null).toList();
    }

    public static PendingEdit addSongs(List<Long> songIds) {
        return new PendingEdit(Kind.ADD_SONG, songIds, false, null);
    }

    public static PendingEdit removeSong(Long songId) {
        return new PendingEdit(Kind.REMOVE_SONG, List.of(songId), false, null);
    }

    public static PendingEdit moveSong(Long songId, boolean up) {
        return new PendingEdit(Kind.MOVE_SONG, List.of(songId), up, null);
    }

    public static PendingEdit rename(String name) {
        return new PendingEdit(Kind.RENAME, List.of(), false, name);
    }

    /** Publish, unpublish, delete and the collaborator grants: refresh only. */
    public static PendingEdit of(Kind kind) {
        return new PendingEdit(kind, List.of(), false, null);
    }

    public boolean isCloneable() {
        return kind.isCloneable();
    }

    /** The one song REMOVE_SONG and MOVE_SONG act on. */
    public Long songId() {
        return songIds.isEmpty() ? null : songIds.get(0);
    }
}
