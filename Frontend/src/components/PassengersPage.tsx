import { type FormEvent, useState } from "react";

import type { Passenger, PassengerCreateRequest } from "../types/passenger";

import type { BookResponse } from "../types/booking";

import { createPassenger } from "../api/passengerApi";

import { fetchPassengerTickets } from "../api/bookingApi";

interface PassengersPageProps {
  passengers: Passenger[];
  loading: boolean;
  error: string | null;

  onPassengerCreated: (passenger: Passenger) => void;
}

const DOCUMENT_TYPES = [
  {
    id: 1,
    name: "Паспорт гражданина РФ",
  },
  {
    id: 2,
    name: "Свидетельство о рождении",
  },
  {
    id: 3,
    name: "Заграничный паспорт",
  },
  {
    id: 4,
    name: "Документ иностранного гражданина",
  },
];

export function PassengersPage({
  passengers,
  loading,
  error,
  onPassengerCreated,
}: PassengersPageProps) {
  const [formOpened, setFormOpened] = useState(false);

  const [firstName, setFirstName] = useState("");

  const [middleName, setMiddleName] = useState("");

  const [lastName, setLastName] = useState("");

  const [documentTypeId, setDocumentTypeId] = useState(1);

  const [documentSeriesNumber, setDocumentSeriesNumber] = useState("");

  const [documentNumber, setDocumentNumber] = useState("");

  const [birthDate, setBirthDate] = useState("");

  const [email, setEmail] = useState("");

  const [phoneNumber, setPhoneNumber] = useState("");

  const [formError, setFormError] = useState<string | null>(null);

  const [creating, setCreating] = useState(false);

  const [openedPassengerId, setOpenedPassengerId] = useState<string | null>(
    null,
  );

  const [passengerTickets, setPassengerTickets] = useState<
    Record<string, BookResponse[]>
  >({});

  const [ticketsLoadingFor, setTicketsLoadingFor] = useState<string | null>(
    null,
  );

  const [ticketsErrors, setTicketsErrors] = useState<Record<string, string>>(
    {},
  );

  function resetForm() {
    setFirstName("");
    setMiddleName("");
    setLastName("");

    setDocumentTypeId(1);

    setDocumentSeriesNumber("");
    setDocumentNumber("");

    setBirthDate("");
    setEmail("");
    setPhoneNumber("");

    setFormError(null);
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();

    if (
      !firstName.trim() ||
      !lastName.trim() ||
      !documentSeriesNumber.trim() ||
      !documentNumber.trim() ||
      !birthDate ||
      !email.trim()
    ) {
      setFormError("Fill in all required fields");

      return;
    }

    const request: PassengerCreateRequest = {
      firstName: firstName.trim(),

      middleName: middleName.trim(),

      lastName: lastName.trim(),

      documentTypeId,

      documentSeriesNumber: documentSeriesNumber.trim(),

      documentNumber: documentNumber.trim(),

      birthDate,

      email: email.trim(),

      phoneNumber: phoneNumber.trim() || null,
    };

    try {
      setCreating(true);
      setFormError(null);

      const passenger = await createPassenger(request);

      onPassengerCreated(passenger);

      resetForm();

      setFormOpened(false);
    } catch (err) {
      setFormError(
        err instanceof Error ? err.message : "Failed to create passenger",
      );
    } finally {
      setCreating(false);
    }
  }

  async function handleTicketsClick(passengerId: string) {
    if (openedPassengerId === passengerId) {
      setOpenedPassengerId(null);

      return;
    }
    setOpenedPassengerId(passengerId);

    if (passengerTickets[passengerId] !== undefined) {
      return;
    }

    try {
      setTicketsLoadingFor(passengerId);

      setTicketsErrors((current) => {
        const copy = {
          ...current,
        };

        delete copy[passengerId];

        return copy;
      });

      const tickets = await fetchPassengerTickets(passengerId);

      setPassengerTickets((current) => ({
        ...current,

        [passengerId]: tickets,
      }));
    } catch (err) {
      setTicketsErrors((current) => ({
        ...current,

        [passengerId]:
          err instanceof Error ? err.message : "Failed to load tickets",
      }));
    } finally {
      setTicketsLoadingFor((current) =>
        current === passengerId ? null : current,
      );
    }
  }

  return (
    <section>
      <div className="section-header">
        <div>
          <h2>Passengers</h2>
        </div>

        <button
          type="button"
          className="add-ticket-button"

          onClick={() => setFormOpened(true)}
        >
          Add Passenger
        </button>
      </div>

      {loading && <p className="message">Loading passengers...</p>}

      {error && <p className="message error">{error}</p>}

      {!loading && !error && passengers.length === 0 && (
        <p className="message">No passengers yet.</p>
      )}

      {!loading && !error && passengers.length > 0 && (
        <div className="passenger-grid">
          {passengers.map((passenger) => {
            const tickets = passengerTickets[passenger.id];

            const ticketsOpened = openedPassengerId === passenger.id;

            const ticketsLoading = ticketsLoadingFor === passenger.id;

            const ticketsError = ticketsErrors[passenger.id];

            return (
              <article className="passenger-card" key={passenger.id}>
                <div className="passenger-card-header">
                  <div>
                    <h3>
                      {passenger.firstName} {passenger.middleName}{" "}
                      {passenger.lastName}
                    </h3>

                    <span>{passenger.documentTypeName}</span>
                  </div>
                </div>

                <dl className="passenger-info">
                  <div>
                    <dt>Document</dt>

                    <dd>
                      {passenger.documentSeriesNumber}{" "}
                      {passenger.documentNumber}
                    </dd>
                  </div>

                  <div>
                    <dt>Birth date</dt>

                    <dd>{passenger.birthDate}</dd>
                  </div>

                  <div>
                    <dt>Email</dt>

                    <dd>{passenger.email}</dd>
                  </div>

                  <div>
                    <dt>Phone</dt>

                    <dd>{passenger.phoneNumber ?? "—"}</dd>
                  </div>
                </dl>

                <div className="passenger-id">ID: {passenger.id}</div>

                <button
                  type="button"

                  className="passenger-tickets-button"

                  onClick={() => void handleTicketsClick(passenger.id)}
                >
                  {ticketsOpened ? "Hide tickets" : "Show tickets"}
                </button>

                {ticketsOpened && (
                  <div className="passenger-tickets">
                    <div className="passenger-tickets-header">
                      <strong>Purchased tickets</strong>
                    </div>

                    {ticketsLoading && (
                      <div className="passenger-tickets-message">
                        Loading tickets...
                      </div>
                    )}

                    {ticketsError && (
                      <div className="booking-alert error">{ticketsError}</div>
                    )}

                    {!ticketsLoading &&
                      !ticketsError &&
                      tickets &&
                      tickets.length === 0 && (
                        <div className="passenger-tickets-message">
                          No purchased tickets.
                        </div>
                      )}

                    {!ticketsLoading &&
                      !ticketsError &&
                      tickets &&
                      tickets.length > 0 && (
                        <div className="passenger-ticket-list">
                          {tickets.map((ticket) => (
                            <div
                              className="passenger-ticket-item"

                              key={ticket.ticketId}
                            >
                              <div>
                                <span>Ticket</span>

                                <strong>#{ticket.ticketId}</strong>
                              </div>

                              <div>
                                <span>Sale price</span>

                                <strong>{ticket.price}</strong>
                              </div>
                            </div>
                          ))}
                        </div>
                      )}
                  </div>
                )}
              </article>
            );
          })}
        </div>
      )}

      {formOpened && (
        <div
          className="modal-backdrop"

          onMouseDown={() => !creating && setFormOpened(false)}
        >
          <form
            className="booking-modal passenger-modal"

            onSubmit={handleSubmit}

            onMouseDown={(event) => event.stopPropagation()}
          >
            <div className="modal-header">
              <div>
                <span className="modal-eyebrow">Passenger</span>

                <h2>Add passenger</h2>
              </div>

              <button
                type="button"
                className="close-button"

                onClick={() => setFormOpened(false)}
              >
                ×
              </button>
            </div>

            <div className="form-row">
              <label className="form-field">
                <span>First name *</span>

                <input
                  value={firstName}

                  onChange={(event) => setFirstName(event.target.value)}
                />
              </label>

              <label className="form-field">
                <span>Last name *</span>

                <input
                  value={lastName}

                  onChange={(event) => setLastName(event.target.value)}
                />
              </label>
            </div>

            <label className="form-field">
              <span>Middle name</span>

              <input
                value={middleName}

                onChange={(event) => setMiddleName(event.target.value)}
              />
            </label>

            <label className="form-field">
              <span>Document type *</span>

              <select
                value={documentTypeId}

                onChange={(event) =>
                  setDocumentTypeId(Number(event.target.value))
                }
              >
                {DOCUMENT_TYPES.map((type) => (
                  <option key={type.id} value={type.id}>
                    {type.name}
                  </option>
                ))}
              </select>
            </label>

            <div className="form-row">
              <label className="form-field">
                <span>Document series *</span>

                <input
                  value={documentSeriesNumber}

                  onChange={(event) =>
                    setDocumentSeriesNumber(event.target.value)
                  }
                />
              </label>

              <label className="form-field">
                <span>Document number *</span>

                <input
                  value={documentNumber}

                  onChange={(event) => setDocumentNumber(event.target.value)}
                />
              </label>
            </div>

            <label className="form-field">
              <span>Birth date *</span>

              <input
                type="date"

                value={birthDate}

                onChange={(event) => setBirthDate(event.target.value)}
              />
            </label>

            <label className="form-field">
              <span>Email *</span>

              <input
                type="email"

                value={email}

                onChange={(event) => setEmail(event.target.value)}
              />
            </label>

            <label className="form-field">
              <span>Phone</span>

              <input
                type="tel"

                value={phoneNumber}

                placeholder="+79991234567"

                onChange={(event) => setPhoneNumber(event.target.value)}
              />
            </label>

            {formError && (
              <div className="booking-alert error">{formError}</div>
            )}

            <div className="modal-actions">
              <button
                type="button"
                className="cancel-button"

                disabled={creating}

                onClick={() => setFormOpened(false)}
              >
                Cancel
              </button>

              <button
                type="submit"
                className="submit-booking-button"

                disabled={creating}
              >
                {creating ? "Creating..." : "Create passenger"}
              </button>
            </div>
          </form>
        </div>
      )}
    </section>
  );
}
