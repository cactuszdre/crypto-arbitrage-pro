package com.crypto.auth.controller;

import com.crypto.auth.business.UserService;
import com.crypto.auth.model.Plan;
import com.crypto.auth.model.Role;
import com.crypto.auth.model.User;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class UserResource {

    private final UserService userService;

    @GET
    public List<User> getAllUsers() {
        return userService.getAllUsers();
    }

    @GET
    @Path("/admins")
    public List<User> getAdmins() {
        return userService.getAdmins();
    }

    @GET
    @Path("/wallet/{address}")
    public User getUserByWallet(@PathParam("address") String address) {
        return userService.getUserByWallet(address);
    }

    @POST
    @Path("/admins")
    public User addAdmin(Map<String, String> body) {
        String walletAddress = body.get("walletAddress");
        if (walletAddress == null || walletAddress.isBlank()) {
            throw new BadRequestException("walletAddress is required");
        }
        return userService.addAdmin(walletAddress);
    }

    @DELETE
    @Path("/admins/{walletAddress}")
    public Response removeAdmin(@PathParam("walletAddress") String walletAddress) {
        userService.removeAdmin(walletAddress);
        return Response.noContent().build();
    }

    @PATCH
    @Path("/{id}/role")
    public User updateRole(@PathParam("id") Long id, @QueryParam("role") String roleStr) {
        Role role = Role.valueOf(roleStr.toUpperCase());
        return userService.updateRole(id, role);
    }

    @PATCH
    @Path("/{id}/plan")
    public User updatePlan(@PathParam("id") Long id, @QueryParam("plan") String planStr) {
        Plan plan = Plan.valueOf(planStr.toUpperCase());
        return userService.updatePlan(id, plan);
    }

    @POST
    @Path("/wallet/{address}/login")
    public Response updateLastLogin(@PathParam("address") String address) {
        userService.updateLastLogin(address);
        return Response.ok().build();
    }
}
