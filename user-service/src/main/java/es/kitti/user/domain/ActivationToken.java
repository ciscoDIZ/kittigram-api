package es.kitti.user.domain;

import java.util.Objects;
import java.util.UUID;

public final class ActivationToken {

    private final String value;

    private ActivationToken(String value) {
        this.value = value;
    }

    public static ActivationToken of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("ActivationToken cannot be null or blank");
        }
        return new ActivationToken(raw);
    }

    public static ActivationToken generate() {
        return new ActivationToken(UUID.randomUUID().toString());
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ActivationToken other)) return false;
        return Objects.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}