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