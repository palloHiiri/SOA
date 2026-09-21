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
    page: number;
    size: number;
    sort?: string;
    
    id?: number;
    name?: string;
    creationDate?: string;
    price?: number;
    discount?: number;
    refundable?: boolean;
    type?: TicketType;
    coordinates?: Partial<Coordinates>;
    venue?: {
        id?: number;
        name?: string;
        capacity?: number;
    }
}