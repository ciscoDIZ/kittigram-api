package org.ciscoadiz.cat.exception;

public class CatNotFoundException extends RuntimeException {
    public CatNotFoundException(Long id) {
        super("Cat not found with id: " + id);
    }
}