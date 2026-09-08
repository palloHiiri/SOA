export type TicketType = "VIP" | "USUAL" | "CHEAP";

export interface Coordinates {
    x: number;
    y: number;
}

export interface Venue {
    id?: number;
    name: string;
    capacity: number;
}

export interface Ticket {
    id?: number;
    name: string;
    coordinates: Coordinates;
    creationDate?: string;
    price?: number;
    discount?: number;
    refundable?: boolean;
    type?: TicketType;
    venue?: Venue;
}