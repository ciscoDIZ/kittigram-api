package es.kitti.organization.resource;

import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import es.kitti.organization.dto.*;
import es.kitti.organization.service.OrganizationService;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;

@Path("/organizations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
public class OrganizationResource {

    @Inject
    OrganizationService organizationService;

    @Inject
    JsonWebToken jwt;

    @POST
    @RolesAllowed({"Organization", "Admin"})
    public Uni<Response> create(@Valid CreateOrganizationRequest request) {
        Long userId = Long.parseLong(jwt.getSubject());
        return organizationService.create(request, userId)
                .onItem().transform(r -> Response.status(Response.Status.CREATED).entity(r).build());
    }

    @GET
    @Path("/mine")
    public Uni<OrganizationResponse> mine() {
        Long userId = Long.parseLong(jwt.getSubject());
        return organizationService.findByCurrentUser(userId);
    }

    @GET
    @Path("/{id}")
    public Uni<OrganizationResponse> findById(@PathParam("id") Long id) {
        return organizationService.findById(id);
    }

    @PUT
    @Path("/{id}")
    public Uni<OrganizationResponse> update(@PathParam("id") Long id,
                                            @Valid UpdateOrganizationRequest request) {
        Long userId = Long.parseLong(jwt.getSubject());
        return organizationService.update(id, userId, request);
    }

    @GET
    @Path("/{id}/members")
    public Uni<List<MemberResponse>> listMembers(@PathParam("id") Long id) {
        Long userId = Long.parseLong(jwt.getSubject());
        return organizationService.listMembers(id, userId);
    }

    @POST
    @Path("/{id}/members")
    public Uni<Response> inviteMember(@PathParam("id") Long id,
                                      @Valid InviteMemberRequest request) {
        Long userId = Long.parseLong(jwt.getSubject());
        return organizationService.inviteMember(id, userId, request)
                .onItem().transform(r -> Response.status(Response.Status.CREATED).entity(r).build());
    }

    @PATCH
    @Path("/{id}/members/{targetUserId}/role")
    public Uni<MemberResponse> changeMemberRole(@PathParam("id") Long id,
                                                @PathParam("targetUserId") Long targetUserId,
                                                @Valid ChangeMemberRoleRequest request) {
        Long userId = Long.parseLong(jwt.getSubject());
        return organizationService.changeMemberRole(id, targetUserId, userId, request);
    }

    @DELETE
    @Path("/{id}/members/{targetUserId}")
    public Uni<Response> removeMember(@PathParam("id") Long id,
                                      @PathParam("targetUserId") Long targetUserId) {
        Long userId = Long.parseLong(jwt.getSubject());
        return organizationService.removeMember(id, targetUserId, userId)
                .onItem().transform(v -> Response.noContent().build());
    }
}
