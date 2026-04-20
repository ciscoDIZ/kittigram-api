package es.kitti.organization.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import es.kitti.organization.dto.*;
import es.kitti.organization.entity.Organization;
import es.kitti.organization.entity.OrganizationMember;

@ApplicationScoped
public class OrganizationMapper {

    public Organization toEntity(CreateOrganizationRequest request) {
        Organization org = new Organization();
        org.name = request.name();
        org.description = request.description();
        org.address = request.address();
        org.city = request.city();
        org.region = request.region();
        org.country = request.country();
        org.phone = request.phone();
        org.email = request.email();
        org.logoUrl = request.logoUrl();
        return org;
    }

    public OrganizationResponse toResponse(Organization org) {
        return new OrganizationResponse(
                org.id,
                org.name,
                org.description,
                org.address,
                org.city,
                org.region,
                org.country,
                org.phone,
                org.email,
                org.logoUrl,
                org.status,
                org.plan,
                org.maxMembers,
                org.createdAt,
                org.updatedAt
        );
    }

    public MemberResponse toResponse(OrganizationMember member) {
        return new MemberResponse(
                member.id,
                member.organizationId,
                member.userId,
                member.role,
                member.status,
                member.joinedAt
        );
    }
}
