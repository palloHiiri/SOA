import type {
    Passenger,
    PassengerCreateRequest
} from "../types/passenger";

const PASSENGERS_URL =
    "/api/v1/clients-srv/client/passengers";


async function readError(
    response: Response
): Promise<string> {

    try {
        const data = await response.json();

        return data.message
            ?? `Passenger request failed: ${response.status}`;

    } catch {
        return `Passenger request failed: ${response.status}`;
    }
}


export async function fetchPassengers():
Promise<Passenger[]> {

    const response = await fetch(
        PASSENGERS_URL,
        {
            credentials: "include"
        }
    );

    if (!response.ok) {
        throw new Error(
            await readError(response)
        );
    }

    return response.json();
}


export async function createPassenger(
    request: PassengerCreateRequest
): Promise<Passenger> {

    const response = await fetch(
        PASSENGERS_URL,
        {
            method: "POST",

            headers: {
                "Content-Type": "application/json"
            },

            credentials: "include",

            body: JSON.stringify(request)
        }
    );

    if (!response.ok) {
        throw new Error(
            await readError(response)
        );
    }

    return response.json();
}