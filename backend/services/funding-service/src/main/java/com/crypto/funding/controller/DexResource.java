package com.crypto.funding.controller;

import com.crypto.funding.business.DexBusiness;
import com.crypto.funding.dto.DexCreateRequest;
import com.crypto.funding.dto.DexDTO;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * JAX-RS Resource for DEX management.
 * Provides CRUD operations for DEX entities.
 */
@Component
@Path("/dex")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class DexResource {

    private final DexBusiness dexBusiness;

    /**
     * Get all DEXes
     */
    @GET
    public List<DexDTO> getAllDexes() {
        return dexBusiness.getAllDexes();
    }

    /**
     * Get only active DEXes
     */
    @GET
    @Path("/active")
    public List<DexDTO> getActiveDexes() {
        return dexBusiness.getActiveDexes();
    }

    /**
     * Get DEX by ID
     */
    @GET
    @Path("/{id}")
    public DexDTO getDexById(@PathParam("id") Long id) {
        return dexBusiness.getDexById(id);
    }

    /**
     * Get DEX by name
     */
    @GET
    @Path("/name/{name}")
    public DexDTO getDexByName(@PathParam("name") String name) {
        return dexBusiness.getDexByName(name);
    }

    /**
     * Search DEXes by name
     */
    @GET
    @Path("/search")
    public List<DexDTO> searchDexes(@QueryParam("q") String query) {
        return dexBusiness.searchByName(query);
    }

    /**
     * Create a new DEX
     */
    @POST
    public Response createDex(@Valid DexCreateRequest request) {
        DexDTO created = dexBusiness.createDex(request);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    /**
     * Update an existing DEX
     */
    @PUT
    @Path("/{id}")
    public DexDTO updateDex(@PathParam("id") Long id, @Valid DexCreateRequest request) {
        return dexBusiness.updateDex(id, request);
    }

    /**
     * Delete a DEX
     */
    @DELETE
    @Path("/{id}")
    public Response deleteDex(@PathParam("id") Long id) {
        dexBusiness.deleteDex(id);
        return Response.noContent().build();
    }
}
