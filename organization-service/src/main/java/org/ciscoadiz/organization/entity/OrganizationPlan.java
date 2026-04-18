package org.ciscoadiz.organization.entity;

public enum OrganizationPlan {
    FREE(1),
    BASIC(5),
    PRO(-1);

    public final int maxMembers;

    OrganizationPlan(int maxMembers) {
        this.maxMembers = maxMembers;
    }
}
