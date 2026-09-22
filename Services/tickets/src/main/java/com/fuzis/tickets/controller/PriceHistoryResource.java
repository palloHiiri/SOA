package com.fuzis.tickets.controller;

import com.fuzis.tickets.dto.PageResponse;
import com.fuzis.tickets.dto.PriceHistoryCreateRequest;
import com.fuzis.tickets.dto.PriceHistoryResponse;
import com.fuzis.tickets.service.PriceHistoryService;
import lombok.NoArgsConstructor;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.BadRequestException;

import java.math.BigDecimal;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

@NoArgsConstructor(force = true)
@Path("/price-histories")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PriceHistoryResource {
    private final PriceHistoryService service;
    @Inject
    public PriceHistoryResource(PriceHistoryService service) {
        this.service = service;
    }

    @GET
    public PageResponse<PriceHistoryResponse> list(
    @DefaultValue("1")
    @QueryParam("page")
    int page,
    @DefaultValue("20")
    @QueryParam("size")
    int size,
    @QueryParam("sort") List<String> sort,
    @QueryParam("id") Long id,
    @QueryParam("ticketId") Long ticketId,
    @QueryParam("changedDateFrom") String changedDateFrom,
    @QueryParam("changedDateTo") String changedDateTo,
    @QueryParam("basePrice") BigDecimal basePrice,
    @QueryParam("discount") Integer discount) {

        OffsetDateTime from = parseOffsetDateTime(changedDateFrom, "changedDateFrom");
        OffsetDateTime to = parseOffsetDateTime(changedDateTo, "changedDateTo");

        return service.list(
        page,
        size,
        sort,
        id,
        ticketId,
        from,
        to,
        basePrice,
        discount
        );
    }
    private OffsetDateTime parseOffsetDateTime(String value, String parameterName) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return OffsetDateTime.parse(value);
        }
        catch (DateTimeParseException e) {
            throw new BadRequestException(
            "Query parameter '" + parameterName
            + "' must be a valid ISO-8601 date-time"
            );
        }
    }
    @POST
    public Response create(@Valid PriceHistoryCreateRequest request, @Context UriInfo uriInfo) {
        PriceHistoryResponse r = service.create(request);
        URI location = uriInfo.getAbsolutePathBuilder().path(Long.toString(r.getId())).build();
        return Response.created(location).entity(r).build();
    }
    @GET
    @Path("{id}")
    public PriceHistoryResponse get(@PathParam("id") long id) {
        return service.get(id);
    }
}
