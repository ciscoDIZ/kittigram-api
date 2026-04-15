package org.ciscoadiz.adoption.exception;

import org.ciscoadiz.adoption.entity.AdoptionStatus;

public class InvalidAdoptionStatusException extends RuntimeException {
    public InvalidAdoptionStatusException(AdoptionStatus current, AdoptionStatus required) {
        super("Invalid adoption status. Current: " + current + ", required: " + required);
    }
}