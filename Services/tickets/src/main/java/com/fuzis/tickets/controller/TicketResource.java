package com.fuzis.tickets.controller;

import com.fuzis.tickets.dto.PageResponse;
import com.fuzis.tickets.dto.TicketCreateRequest;
import com.fuzis.tickets.dto.TicketResponse;
import com.fuzis.tickets.dto.TicketType;
import com.fuzis.tickets.dto.TicketUpdateRequest;
import com.fuzis.tickets.service.TicketService;
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

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;

@NoArgsConstructor(force = true)
@Path("/tickets")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TicketResource {
    private final TicketService service;

    @Inject
    public TicketResource(TicketService service) {
        this.service = service;
    }

    @GET
    public PageResponse<TicketResponse> list(
    @DefaultValue("1")
    @QueryParam("page")
    int page,
    @DefaultValue("20")
    @QueryParam("size")
    int size,
    @QueryParam("sort") List<String> sort,
    @QueryParam("id") Long id,
    @QueryParam("name") String name,
    @QueryParam("creationDate") String creationDate,
    @QueryParam("venueId") Long venueId,
    @QueryParam("trainSetId") Integer trainSetId,
    @QueryParam("carriageNumber") String carriageNumber,
    @QueryParam("seatNumber") String seatNumber,
    @QueryParam("refundable") Boolean refundable,
    @QueryParam("type") TicketType type,
    @QueryParam("basePrice") BigDecimal basePrice,
    @QueryParam("discount") Integer discount) {

        return service.list(
        page,
        size,
        sort,
        id,
        name,
        parseCreationDate(creationDate),
        venueId,
        trainSetId,
        carriageNumber,
        seatNumber,
        refundable,
        type,
        basePrice,
        discount
        );
    }
    private LocalDate parseCreationDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return LocalDate.parse(value);
        }
        catch (java.time.format.DateTimeParseException e) {
            throw new jakarta.ws.rs.BadRequestException(
            "Query parameter 'creationDate' must be a valid ISO-8601 date"
            );
        }
    }

    @POST
    public Response create(@Valid TicketCreateRequest request, @Context UriInfo uriInfo) {
        TicketResponse created = service.create(request);
        URI location = uriInfo.getAbsolutePathBuilder().path(Long.toString(created.getId())).build();
        return Response.created(location).entity(created).build();
    }

    @GET
    @Path("latest")
    public TicketResponse latest() {
        return service.latest();
    }

    @GET
    @Path("discount/average")
    public BigDecimal averageDiscount() {
        return service.averageDiscount();
    }

    @GET
    @Path("by-discount-below/{discount}")
    public PageResponse<TicketResponse> belowDiscount(
    @PathParam("discount") int discount,
    @DefaultValue("1")
    @QueryParam("page")
    int page,
    @DefaultValue("20")
    @QueryParam("size")
    int size,
    @QueryParam("sort") List<String> sort,
    @QueryParam("venueId") Long venueId,
    @QueryParam("trainSetId") Integer trainSetId,
    @QueryParam("carriageNumber") String carriageNumber,
    @QueryParam("seatNumber") String seatNumber,
    @QueryParam("refundable") Boolean refundable,
    @QueryParam("type") TicketType type) {
        return service.belowDiscount(discount, page, size, sort, venueId, trainSetId, carriageNumber, seatNumber, refundable, type);
    }

    @GET
    @Path("{id}")
    public TicketResponse get(@PathParam("id") long id) {
        return service.get(id);
    }

    @PUT
    @Path("{id}")
    public TicketResponse update(@PathParam("id") long id, @Valid TicketUpdateRequest request) {
        return service.update(id, request);
    }

    @DELETE
    @Path("{id}")
    public Response delete(@PathParam("id") long id) {
        service.delete(id);
        return Response.noContent().build();
    }
}
