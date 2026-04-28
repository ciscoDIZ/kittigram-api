package es.kitti.adoption.intake.dto;

import es.kitti.adoption.intake.client.OrganizationPublicMinimal;

import java.util.List;

public record IntakeRejectionResponse(
        IntakeRequestResponse intake,
        List<OrganizationPublicMinimal> alternatives
) {}
