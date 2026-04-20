package es.kitti.organization.entity;

public enum OrganizationPlan {
    Free(1),
    Basic(5),
    Pro(-1);

    public final int maxMembers;

    OrganizationPlan(int maxMembers) {
        this.maxMembers = maxMembers;
    }
}
