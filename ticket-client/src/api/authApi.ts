import type {
    AuthError,
    LoginRequest,
    LoginResponse,
    MeResponse,
    RegisterRequest,
    RegisterResponse
} from "../types/auth";

const AUTH_BASE_URL = "/auth-api";


async function readError(
    response: Response
): Promise<string> {

    try {
        const error: AuthError =
            await response.json();

        return error.message;
    } catch {
        return `Authentication error: ${response.status}`;
    }
}

export async function login(
    request: LoginRequest
): Promise<LoginResponse> {

    const response = await fetch(
        `${AUTH_BASE_URL}/auth/login`,
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

export async function getCurrentUser():
Promise<MeResponse | null> {

    const response = await fetch(
        `${AUTH_BASE_URL}/me`,
        {
            credentials: "include"
        }
    );

    if (response.status === 401) {
        return null;
    }

    if (!response.ok) {
        throw new Error(
            `Failed to check session: ${response.status}`
        );
    }

    return response.json();
}

export async function logout():
Promise<void> {

    const response = await fetch(
        `${AUTH_BASE_URL}/auth/logout`,
        {
            method: "POST",
            credentials: "include"
        }
    );

    if (!response.ok && response.status !== 401) {
        throw new Error(
            `Logout failed: ${response.status}`
        );
    }
}

export async function register(
    request: RegisterRequest
): Promise<RegisterResponse> {

    const response = await fetch(
        `${AUTH_BASE_URL}/auth/register`,
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
            await readError(response)
        );
    }

    return response.json();
}