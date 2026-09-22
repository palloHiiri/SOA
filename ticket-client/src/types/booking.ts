export interface BookResponse {
    ticketId: number;
    passengerId: string;
    price: number;
}

export interface BookingError {
    code: string;
    message: string;
}