import type { BookResponse, BookingError } from "../types/booking";

const BOOKING_BASE_URL = "/booking-api/api/v1/booking";

async function readResponse(response: Response): Promise<BookResponse> {
  if (response.ok) {
    return response.json();
  }

  let error: BookingError | null = null;

  try {
    error = await response.json();
  } catch {}

  throw new Error(error?.message ?? `Booking error: ${response.status}`);
}

export async function sellTicket(
  ticketId: number,
  passengerId: string,
): Promise<BookResponse> {
  const params = new URLSearchParams({
    passengerId,
  });

  const response = await fetch(
    `${BOOKING_BASE_URL}/tickets/${ticketId}/sell?${params}`,
    {
      method: "POST",
      credentials: "include",
    },
  );

  return readResponse(response);
}

export async function sellTicketWithDiscount(
  ticketId: number,
  passengerId: string,
  discount: number,
): Promise<BookResponse> {
  const params = new URLSearchParams({
    passengerId,
    discount: String(discount),
  });

  const response = await fetch(
    `${BOOKING_BASE_URL}/tickets/${ticketId}/sell-with-discount?${params}`,
    {
      method: "POST",
      credentials: "include",
    },
  );

  return readResponse(response);
}
