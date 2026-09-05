package com.funix.swp490x.mrs.service;

/** The user is already a collaborator on this playlist (DC-09). */
public class DuplicateCollaboratorException extends RuntimeException {

    public DuplicateCollaboratorException(Long playlistId, Long userId) {
        super("User " + userId + " is already a collaborator on playlist " + playlistId);
    }
}
