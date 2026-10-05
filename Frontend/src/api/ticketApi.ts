import type {
  CarriageResponse,
  SeatResponse,
  Ticket,
  TicketCreateRequest,
  TicketRequest,
  TicketResponse,
  TicketUpdateRequest,
  TrainSetResponse,
  Venue,
  VenueCreateRequest,
  VenueResponse,
  VenueUpdateRequest,
} from "../types/ticket";

const API_BASE_URL = "/api/v1/tickets";

export async function fetchTickets(
  request: TicketRequest = {},
): Promise<TicketResponse> {
  const response = await fetch(`${API_BASE_URL}/search`, {
    method: "POST",

    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
    },

    body: JSON.stringify({
      ...request,
      sort: request.sort ? [request.sort] : [],
    }),
  });

  if (!response.ok) {
    throw new Error(await readTicketError(response));
  }

  return response.json();
}

async function readTicketError(response: Response): Promise<string> {
  try {
    const data = await response.json();

    return data.message ?? `Request failed: ${response.status}`;
  } catch {
    return `Request failed: ${response.status}`;
  }
}

export async function fetchVenues(): Promise<VenueResponse> {
  const response = await fetch(
    `${API_BASE_URL}/venues?page=1&size=100&sort=id,asc`,
  );

  if (!response.ok) {
    throw new Error(await readTicketError(response));
  }

  return response.json();
}

export async function fetchCarriages(
  trainSetId: number,
): Promise<CarriageResponse> {
  const response = await fetch(
    `${API_BASE_URL}/train-sets/${trainSetId}/carriages?page=1&size=100`,
  );

  if (!response.ok) {
    throw new Error(await readTicketError(response));
  }

  return response.json();
}

export async function fetchSeats(
  trainSetId: number,
  carriageNumber: string,
): Promise<SeatResponse> {
  const response = await fetch(
    `${API_BASE_URL}/train-sets/${trainSetId}/carriages/${encodeURIComponent(
      carriageNumber,
    )}/seats?page=1&size=100`,
  );

  if (!response.ok) {
    throw new Error(await readTicketError(response));
  }

  return response.json();
}

export async function createTicket(
  request: TicketCreateRequest,
): Promise<Ticket> {
  const response = await fetch(`${API_BASE_URL}/tickets`, {
    method: "POST",

    headers: {
      "Content-Type": "application/json",
    },

    body: JSON.stringify(request),
  });

  if (!response.ok) {
    throw new Error(await readTicketError(response));
  }

  return response.json();
}

export async function updateTicket(
  ticketId: number,
  request: TicketUpdateRequest,
): Promise<Ticket> {
  const response = await fetch(`${API_BASE_URL}/tickets/${ticketId}`, {
    method: "PUT",

    headers: {
      "Content-Type": "application/json",
    },

    body: JSON.stringify(request),
  });

  if (!response.ok) {
    throw new Error(await readTicketError(response));
  }

  return response.json();
}

export async function deleteTicket(ticketId: number): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/tickets/${ticketId}`, {
    method: "DELETE",
  });

  if (!response.ok) {
    throw new Error(await readTicketError(response));
  }
}

export async function fetchLatestTicket(): Promise<Ticket> {
  const response = await fetch(`${API_BASE_URL}/tickets/latest`);

  if (!response.ok) {
    throw new Error(await readTicketError(response));
  }

  return response.json();
}

export async function fetchAverageDiscount(): Promise<number> {
  const response = await fetch(`${API_BASE_URL}/tickets/discount/average`);

  if (!response.ok) {
    throw new Error(await readTicketError(response));
  }

  return response.json();
}

export async function fetchTicketsBelowDiscount(
  discount: number,
): Promise<TicketResponse> {
  const response = await fetch(
    `${API_BASE_URL}/tickets/by-discount-below/${discount}?page=1&size=100&sort=id,asc`,
  );

  if (!response.ok) {
    throw new Error(await readTicketError(response));
  }

  return response.json();
}

export async function fetchTrainSets():
Promise<TrainSetResponse> {

  const response = await fetch(
    `${API_BASE_URL}/train-sets?page=1&size=100&sort=id,asc`
  );

  if (!response.ok) {
    throw new Error(
      await readTicketError(response)
    );
  }

  return response.json();
}


export async function createVenue(
  request: VenueCreateRequest
): Promise<Venue> {

  const response = await fetch(
    `${API_BASE_URL}/venues`,
    {
      method: "POST",

      headers: {
        "Content-Type": "application/json"
      },

      body: JSON.stringify(request)
    }
  );

  if (!response.ok) {
    throw new Error(
      await readTicketError(response)
    );
  }

  return response.json();
}


export async function updateVenue(
  venueId: number,
  request: VenueUpdateRequest
): Promise<Venue> {

  const response = await fetch(
    `${API_BASE_URL}/venues/${venueId}`,
    {
      method: "PUT",

      headers: {
        "Content-Type": "application/json"
      },

      body: JSON.stringify(request)
    }
  );

  if (!response.ok) {
    throw new Error(
      await readTicketError(response)
    );
  }

  return response.json();
}


export async function deleteVenue(
  venueId: number
): Promise<void> {

  const response = await fetch(
    `${API_BASE_URL}/venues/${venueId}`,
    {
      method: "DELETE"
    }
  );

  if (!response.ok) {
    throw new Error(
      await readTicketError(response)
    );
  }
}