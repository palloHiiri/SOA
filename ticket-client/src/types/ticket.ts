export type TicketType = "VIP" | "USUAL" | "CHEAP";

export interface Venue {
  id: number;
  name: string;
  trainSetId: number;
}

export interface Ticket {
  id: number;
  name: string;
  creationDate: string;

  basePrice: number | null;
  discount: number;

  refundable: boolean | null;
  type: TicketType | null;

  venue: Venue;

  carriageNumber: string;
  seatNumber: string;
}

export interface TicketResponse {
  content: Ticket[];

  page: number;
  size: number;

  totalElements: number;
  totalPages: number;

  hasNext: boolean;
  hasPrevious: boolean;
}

export interface TicketRequest {
  page?: number;
  size?: number;
  sort?: string;

  id?: number;
  name?: string;
  creationDate?: string;

  venueId?: number;
  trainSetId?: number;

  carriageNumber?: string;
  seatNumber?: string;

  basePrice?: number;
  discount?: number;

  refundable?: boolean;
  type?: TicketType;
}

export interface TicketCreateRequest {
  name?: string | null;

  venueId: number;

  carriageNumber: string;
  seatNumber: string;

  basePrice?: number | null;
  discount: number;

  refundable?: boolean | null;
  type?: TicketType | null;
}

export interface VenueResponse {
  content: Venue[];

  page: number;
  size: number;

  totalElements: number;
  totalPages: number;

  hasNext: boolean;
  hasPrevious: boolean;
}

export interface Carriage {
  id: number | null;
  carriageNumber: string | null;
  position: number | null;

  serialNumber: string | null;
  inventoryNumber: string | null;
}

export interface CarriageResponse {
  content: Carriage[];

  page: number;
  size: number;

  totalElements: number;
  totalPages: number;

  hasNext: boolean;
  hasPrevious: boolean;
}

export interface Seat {
  id: number | null;
  seatNumber: string | null;

  x: number;
  y: number;
  rotation: number;
}

export interface SeatResponse {
  content: Seat[];

  page: number;
  size: number;

  totalElements: number;
  totalPages: number;

  hasNext: boolean;
  hasPrevious: boolean;
}

export interface TicketUpdateRequest {
  name?: string | null;

  venueId: number;

  carriageNumber: string;
  seatNumber: string;

  refundable?: boolean | null;
  type?: TicketType | null;
}
