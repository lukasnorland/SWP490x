package com.funix.swp490x.mrs.service;

/** A playlist name is unique across the catalogue; this one is already taken. */
public class DuplicatePlaylistNameException extends RuntimeException {

    private final String name;

    public DuplicatePlaylistNameException(String name) {
        super("A playlist named \"" + name + "\" already exists");
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
