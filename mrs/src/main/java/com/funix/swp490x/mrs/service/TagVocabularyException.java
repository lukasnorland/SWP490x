package com.funix.swp490x.mrs.service;

/**
 * A Tag dictionary create/rename/delete that P-06b cannot apply. The message
 * is safe to flash.
 */
public class TagVocabularyException extends RuntimeException {

    public TagVocabularyException(String message) {
        super(message);
    }
}
