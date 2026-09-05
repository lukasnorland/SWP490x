package com.funix.swp490x.mrs.service;

/**
 * A collaborator grant or revoke was refused: the actor cannot manage grants,
 * or the target is not an eligible Content Designer.
 */
public class InvalidCollaboratorException extends RuntimeException {

    public InvalidCollaboratorException(String message) {
        super(message);
    }
}
