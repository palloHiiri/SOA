export interface Passenger {
  id: string;
  clientId: string;

  firstName: string;
  middleName: string;
  lastName: string;

  documentTypeId: number;
  documentTypeCode: string;
  documentTypeName: string;

  documentSeriesNumber: string;
  documentNumber: string;

  birthDate: string;

  email: string;
  phoneNumber: string | null;
}

export interface PassengerCreateRequest {
  firstName: string;
  middleName: string;
  lastName: string;

  documentTypeId: number;

  documentSeriesNumber: string;
  documentNumber: string;

  birthDate: string;

  email: string;
  phoneNumber?: string | null;
}
