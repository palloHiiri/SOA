import type { Ticket } from "../types/ticket";
import { mockTickets } from "../mocks/tickets";

const API_BASE_URL = "http://localhost:8080/tickets";

export async function fetchTickets(): Promise<Ticket[]> {
    // const response = await fetch(API_BASE_URL);
    // if (!response.ok) {
    //     throw new Error(`Error fetching tickets: ${response.statusText}`);
    // }
    // return response.json();
    return new Promise((resolve) => {
        setTimeout(() => {
            resolve(mockTickets);
        }, 1000);
    });
}