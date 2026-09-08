import type { Ticket } from "../types/ticket";

export const mockTickets: Ticket[] = [
    {
        id: 1,
        name: "Concert Ticket",
        coordinates: { x: 10, y: 20 },
        creationDate: "2023-01-01T12:00:00Z",
        price: 100,
        discount: 10,
        refundable: true,
        type: "VIP",
        venue: { id: 1, name: "Stadium", capacity: 5000 }
    },
    {
        id: 2,
        name: "Movie Ticket",
        coordinates: { x: 30, y: 40 },
        creationDate: "2023-02-01T12:00:00Z",
        price: 50,
        discount: 5,
        refundable: false,
        type: "USUAL",
        venue: { id: 2, name: "Cinema", capacity: 200 }
    },
    {
        id: 3,
        name: " Theater Ticket",
        coordinates: { x: 50, y: 60 },
        creationDate: "2023-03-01T12:00:00Z",
        price: 30,
        discount: 0,
        refundable: true,
        type: "CHEAP",
        venue: { id: 3, name: "Theater", capacity: 300 }
    }
];  