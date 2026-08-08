package com.funix.swp490x.mrs.service;

/** The submitted role is not one P-06a may assign (ADMIN stays off the dialog). */
public class InvalidRoleAssignmentException extends RuntimeException {

    public InvalidRoleAssignmentException(String message) {
        super(message);
    }
}
