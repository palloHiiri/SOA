package com.fuzis.tickets.controller;

import com.fuzis.tickets.dto.PageResponse;
import com.fuzis.tickets.dto.TicketResponse;
import com.fuzis.tickets.dto.TicketSearchRequest;
import com.fuzis.tickets.service.TicketService;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import lombok.NoArgsConstructor;

@NoArgsConstructor(force = true)
@Path("/search")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TicketSearchResource {
    private final TicketService service;

    @Inject
    public TicketSearchResource(TicketService service) {
        this.service = service;
    }

    @POST
    public PageResponse<TicketResponse> search(@Valid TicketSearchRequest request) {
        return service.search(request);
    }
}
