package com.fuzis.tickets.service;

import com.fuzis.tickets.dto.PageResponse;
import com.fuzis.tickets.dto.VenueCreateRequest;
import com.fuzis.tickets.dto.VenueResponse;
import com.fuzis.tickets.dto.VenueUpdateRequest;
import com.fuzis.tickets.exception.ApiException;
import com.fuzis.tickets.repository.CdcRepository;
import com.fuzis.tickets.repository.TicketRepository;
import com.fuzis.tickets.repository.VenueRepository;
import com.fuzis.tickets.repository.VenueRepository.VenueRecord;
import com.fuzis.tickets.util.Pagination;
import com.fuzis.tickets.util.SortParser;
import com.fuzis.tickets.util.Sorts;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;

@Dependent
@Transactional
public class VenueService {

    private final VenueRepository venueRepository;
    private final TicketRepository ticketRepository;
    private final CdcRepository cdcRepository;

    @Inject
    public VenueService(
            VenueRepository venueRepository,
            TicketRepository ticketRepository,
            CdcRepository cdcRepository) {
        this.venueRepository = venueRepository;
        this.ticketRepository = ticketRepository;
        this.cdcRepository = cdcRepository;
    }

    public PageResponse<VenueResponse> list(
            int page, int size, List<String> sort, Long id, String name, Integer trainSetId) {

        Pagination pagination = Pagination.of(page, size, maxSize());
        var sorts = SortParser.parse(sort, Sorts.VENUES, "id");

        long total = venueRepository.count(name, trainSetId, id);
        List<VenueResponse> content =
                venueRepository.findPage(
                        page, size, pagination.offset(), name, trainSetId, id, sorts);

        return new PageResponse<>(content, page, size, total);
    }

    public VenueResponse get(long id) {
        validate(id);

        VenueRecord record = venueRepository.findById(id);
        if (record == null) {
            throw ApiException.notFound("Venue " + id + " not found");
        }
        return response(record);
    }

    public VenueResponse create(VenueCreateRequest request) {

        if (cdcRepository.findLatest(request.getTrainSetId()).isEmpty()) {
            throw ApiException.notFound(
                    "Inventory snapshot for train set "
                            + request.getTrainSetId()
                            + " is not available");
        }

        long id = venueRepository.insert(request.getName().trim(), request.getTrainSetId());

        VenueRecord record = venueRepository.findById(id, false);
        return response(record);
    }

    public VenueResponse update(long id, VenueUpdateRequest request) {
        validate(id);

        VenueRecord current = venueRepository.findForUpdate(id);
        if (current == null) {
            throw ApiException.notFound("Venue " + id + " not found");
        }

        if (cdcRepository.findLatest(request.getTrainSetId()).isEmpty()) {
            throw ApiException.notFound(
                    "Inventory snapshot for train set "
                            + request.getTrainSetId()
                            + " is not available");
        }

        if (current.trainSetId() != request.getTrainSetId()
                && ticketRepository.countByVenue(id) > 0) {
            throw ApiException.conflict("Venue trainSetId cannot be changed while tickets exist");
        }

        venueRepository.update(id, request.getName().trim(), request.getTrainSetId());

        VenueRecord record = venueRepository.findById(id, false);
        return response(record);
    }

    public void delete(long id) {
        validate(id);

        VenueRecord current = venueRepository.findForUpdate(id);
        if (current == null) {
            throw ApiException.notFound("Venue " + id + " not found");
        }

        if (ticketRepository.countByVenue(id) > 0) {
            throw ApiException.conflict("Venue has tickets and cannot be deleted");
        }

        venueRepository.delete(id);
    }

    private static VenueResponse response(VenueRecord record) {
        VenueResponse response = new VenueResponse();
        response.setId(record.id());
        response.setName(record.name());
        response.setTrainSetId(record.trainSetId());
        return response;
    }

    private static void validate(long id) {
        if (id < 1) {
            throw ApiException.badRequest("id must be greater than or equal to 1");
        }
    }

    private static int maxSize() {
        try {
            String value = System.getenv("TICKETS_MAX_PAGE_SIZE");
            return value == null || value.isBlank() ? 100 : Integer.parseInt(value);
        } catch (Exception e) {
            return 100;
        }
    }
}
