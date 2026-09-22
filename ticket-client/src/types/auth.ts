export interface LoginRequest {
    login: string;
    password: string;
}

export interface LoginResponse {
    status: "AUTHENTICATED" | "MFA_REQUIRED";
    userId?: string;
    challengeId?: string;
}

export interface MeResponse {
    userId: string;
    subject: string;
    status: string;

    attributes: Record<string, string>;
}

export interface AuthError {
    code: string;
    message: string;
}

export interface RegisterRequest {
    email: string;
    username?: string;
    password: string;
    firstName?: string;
    lastName?: string;
}

export interface RegisterResponse {
    userId: string;
    status: "ACTIVE" | "PENDING";
}