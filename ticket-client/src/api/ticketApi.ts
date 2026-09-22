import type {
    TicketRequest,
    TicketResponse
} from "../types/ticket";

const API_BASE_URL =
    "/api/v1/tickets";

export async function fetchTickets(
    request: TicketRequest = {}
): Promise<TicketResponse> {

    const params = new URLSearchParams();

    params.set(
        "page",
        String(request.page ?? 1)
    );

    params.set(
        "size",
        String(request.size ?? 20)
    );

    if (request.sort) {
        params.set("sort", request.sort);
    }

    if (request.id !== undefined) {
        params.set("id", String(request.id));
    }

    if (request.name) {
        params.set("name", request.name);
    }

    if (request.creationDate) {
        params.set(
            "creationDate",
            request.creationDate
        );
    }

    if (request.venueId !== undefined) {
        params.set(
            "venueId",
            String(request.venueId)
        );
    }

    if (request.trainSetId !== undefined) {
        params.set(
            "trainSetId",
            String(request.trainSetId)
        );
    }

    if (request.carriageNumber) {
        params.set(
            "carriageNumber",
            request.carriageNumber
        );
    }

    if (request.seatNumber) {
        params.set(
            "seatNumber",
            request.seatNumber
        );
    }

    if (request.basePrice !== undefined) {
        params.set(
            "basePrice",
            String(request.basePrice)
        );
    }

    if (request.discount !== undefined) {
        params.set(
            "discount",
            String(request.discount)
        );
    }

    if (request.refundable !== undefined) {
        params.set(
            "refundable",
            String(request.refundable)
        );
    }

    if (request.type) {
        params.set("type", request.type);
    }

    const response = await fetch(`${API_BASE_URL}/tickets?${params.toString()}`);

    if (!response.ok) {
        throw new Error(`Failed to load tickets: ${response.status}` );
    }

    return response.json();
}