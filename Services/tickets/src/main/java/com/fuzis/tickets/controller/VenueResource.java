package com.fuzis.tickets.controller;

import com.fuzis.tickets.dto.PageResponse;
import com.fuzis.tickets.dto.VenueCreateRequest;
import com.fuzis.tickets.dto.VenueResponse;
import com.fuzis.tickets.dto.VenueUpdateRequest;
import com.fuzis.tickets.service.VenueService;
import lombok.NoArgsConstructor;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import java.net.URI;
import java.util.List;

@NoArgsConstructor(force = true)
@Path("/venues")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class VenueResource {
    private final VenueService service;
    @Inject public VenueResource(VenueService service){this.service=service;}

    @GET
    public PageResponse<VenueResponse> list(@DefaultValue("1")@QueryParam("page")int page,@DefaultValue("20")@QueryParam("size")int size,@QueryParam("sort")List<String>sort,@QueryParam("id")Long id,@QueryParam("name")String name,@QueryParam("trainSetId")Integer trainSetId){return service.list(page,size,sort,id,name,trainSetId);}

    @POST
    public Response create(@Valid VenueCreateRequest request,@Context UriInfo uriInfo){VenueResponse v=service.create(request);URI location=uriInfo.getAbsolutePathBuilder().path(Long.toString(v.getId())).build();return Response.created(location).entity(v).build();}
    @GET @Path("{id}") public VenueResponse get(@PathParam("id")long id){return service.get(id);}
    @PUT @Path("{id}") public VenueResponse update(@PathParam("id")long id,@Valid VenueUpdateRequest request){return service.update(id,request);}
    @DELETE @Path("{id}") public Response delete(@PathParam("id")long id){service.delete(id);return Response.noContent().build();}
}
