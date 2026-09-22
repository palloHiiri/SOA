package com.fuzis.tickets.controller;

import com.fuzis.tickets.dto.CarriageResponse;
import com.fuzis.tickets.dto.PageResponse;
import com.fuzis.tickets.dto.SeatResponse;
import com.fuzis.tickets.dto.TrainSetResponse;
import com.fuzis.tickets.service.InventoryProjectionService;
import lombok.NoArgsConstructor;

import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@NoArgsConstructor(force = true)
@Path("/train-sets")
@Produces(MediaType.APPLICATION_JSON)
public class InventoryResource {
    private final InventoryProjectionService service;
    @Inject
    public InventoryResource(InventoryProjectionService service) {
        this.service = service;
    }

    @GET
    public PageResponse<TrainSetResponse> trainSets(
    @DefaultValue("1")@QueryParam("page") int page,
    @DefaultValue("20")@QueryParam("size") int size,
    @QueryParam("sort") List<String>sort,
    @QueryParam("id") Integer id,
    @QueryParam("code") String code,
    @QueryParam("name") String name,
    @QueryParam("buildNumber") Integer buildNumber,
    @QueryParam("technicalName") String technicalName
    ) {
        return service.trainSets(page, size, sort, id, code, name, buildNumber, technicalName);
    }
    @GET
    @Path("{trainSetId}")
    public TrainSetResponse trainSet(@PathParam("trainSetId") int trainSetId) {
        return service.trainSet(trainSetId);
    }
    @GET
    @Path("{trainSetId}/carriages")
    public PageResponse<CarriageResponse> carriages(
    @PathParam("trainSetId") int trainSetId,
    @DefaultValue("1")@QueryParam("page") int page,
    @DefaultValue("20")@QueryParam("size") int size,
    @QueryParam("sort") List<String>sort,
    @QueryParam("id") Integer id,
    @QueryParam("carriageNumber") String carriageNumber,
    @QueryParam("position") Integer position,
    @QueryParam("inventoryNumber") String inventoryNumber,
    @QueryParam("serialNumber") String serialNumber,
    @QueryParam("carriageTypeCode") String carriageTypeCode
    ) {
        return service.carriages(trainSetId, page, size, sort, carriageNumber, id, position, inventoryNumber, serialNumber, carriageTypeCode);
    }
    @GET
    @Path("{trainSetId}/carriages/{carriageNumber}/seats")
    public PageResponse<SeatResponse> seats(
    @PathParam("trainSetId") int trainSetId,
    @PathParam("carriageNumber") String carriageNumber,
    @DefaultValue("1")@QueryParam("page") int page,
    @DefaultValue("20")@QueryParam("size") int size,
    @QueryParam("sort") List<String>sort,
    @QueryParam("seatNumber") String seatNumber,
    @QueryParam("x") Double x,
    @QueryParam("y") Double y,
    @QueryParam("rotation") Double rotation
    ) {
        return service.seats(trainSetId, carriageNumber, page, size, sort, seatNumber, x, y, rotation);
    }
}
