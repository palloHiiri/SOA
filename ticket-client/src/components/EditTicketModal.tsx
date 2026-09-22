import { type FormEvent, useEffect, useState } from "react";

import type {
  Carriage,
  Seat,
  Ticket,
  TicketType,
  Venue,
} from "../types/ticket";

import {
  fetchCarriages,
  fetchSeats,
  fetchVenues,
  updateTicket,
} from "../api/ticketApi";

interface EditTicketModalProps {
  ticket: Ticket;

  onClose: () => void;

  onUpdated: (ticket: Ticket) => void;
}

export function EditTicketModal({
  ticket,
  onClose,
  onUpdated,
}: EditTicketModalProps) {
  const [venues, setVenues] = useState<Venue[]>([]);

  const [carriages, setCarriages] = useState<Carriage[]>([]);

  const [seats, setSeats] = useState<Seat[]>([]);

  const [name, setName] = useState(ticket.name);

  const [venueId, setVenueId] = useState(ticket.venue.id);

  const [carriageNumber, setCarriageNumber] = useState(ticket.carriageNumber);

  const [seatNumber, setSeatNumber] = useState(ticket.seatNumber);

  const [refundable, setRefundable] = useState<"true" | "false" | "null">(
    ticket.refundable === null ? "null" : ticket.refundable ? "true" : "false",
  );

  const [type, setType] = useState<TicketType | "">(ticket.type ?? "");

  const [loading, setLoading] = useState(false);

  const [error, setError] = useState<string | null>(null);

  // Загрузка площадок.
  useEffect(() => {
    async function load() {
      try {
        const response = await fetchVenues();

        setVenues(response.content);
      } catch (err) {
        setError(err instanceof Error ? err.message : "Failed to load venues");
      }
    }

    load();
  }, []);

  // Загрузка вагонов выбранной площадки.
  useEffect(() => {
    if (venues.length === 0) {
      return;
    }

    const venue = venues.find((value) => value.id === venueId);

    if (!venue) {
      return;
    }

    async function load() {
      try {
        const response = await fetchCarriages(venue!.trainSetId);

        const valid = response.content.filter(
          (value) => value.carriageNumber !== null,
        );

        setCarriages(valid);

        const preferred =
          venueId === ticket.venue.id ? ticket.carriageNumber : null;

        const exists = valid.some(
          (value) => value.carriageNumber === preferred,
        );

        setCarriageNumber(
          exists && preferred ? preferred : (valid[0]?.carriageNumber ?? ""),
        );
      } catch (err) {
        setError(
          err instanceof Error ? err.message : "Failed to load carriages",
        );
      }
    }

    load();
  }, [venueId, venues, ticket.carriageNumber, ticket.venue.id]);

  // Загрузка мест выбранного вагона.
  useEffect(() => {
    if (!carriageNumber) {
      return;
    }

    const venue = venues.find((value) => value.id === venueId);

    if (!venue) {
      return;
    }

    async function load() {
      try {
        const response = await fetchSeats(venue!.trainSetId, carriageNumber);

        const valid = response.content.filter(
          (value) => value.seatNumber !== null,
        );

        setSeats(valid);

        const preferred =
          venueId === ticket.venue.id &&
          carriageNumber === ticket.carriageNumber
            ? ticket.seatNumber
            : null;

        const exists = valid.some((value) => value.seatNumber === preferred);

        setSeatNumber(
          exists && preferred ? preferred : (valid[0]?.seatNumber ?? ""),
        );
      } catch (err) {
        setError(err instanceof Error ? err.message : "Failed to load seats");
      }
    }

    load();
  }, [
    carriageNumber,
    venueId,
    venues,
    ticket.carriageNumber,
    ticket.seatNumber,
    ticket.venue.id,
  ]);

  // Обработка сохранения билета.
  async function handleSubmit(event: FormEvent) {
    event.preventDefault();

    if (!carriageNumber || !seatNumber) {
      setError("Select carriage and seat");

      return;
    }

    try {
      setLoading(true);
      setError(null);

      const updated = await updateTicket(ticket.id, {
        name: name.trim() || null,

        venueId,

        carriageNumber,
        seatNumber,

        refundable: refundable === "null" ? null : refundable === "true",

        type: type || null,
      });

      onUpdated(updated);
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to update ticket");
    } finally {
      setLoading(false);
    }
  }

  // Форма редактирования билета.
  return (
    <div className="modal-backdrop" onMouseDown={() => !loading && onClose()}>
      <form
        className="booking-modal"

        onSubmit={handleSubmit}

        onMouseDown={(event) => event.stopPropagation()}
      >
        <div className="modal-header">
          <div>
            <span className="modal-eyebrow">Ticket #{ticket.id}</span>

            <h2>Edit ticket</h2>
          </div>

          <button type="button" className="close-button" onClick={onClose}>
            ×
          </button>
        </div>

        <label className="form-field">
          <span>Name</span>

          <input value={name} onChange={(e) => setName(e.target.value)} />
        </label>

        <label className="form-field">
          <span>Venue</span>

          <select
            value={venueId}

            onChange={(e) => setVenueId(Number(e.target.value))}
          >
            {venues.map((venue) => (
              <option key={venue.id} value={venue.id}>
                {venue.name}
              </option>
            ))}
          </select>
        </label>

        <div className="form-row">
          <label className="form-field">
            <span>Carriage</span>

            <select
              value={carriageNumber}

              onChange={(e) => setCarriageNumber(e.target.value)}
            >
              {carriages.map((carriage) => (
                <option
                  key={carriage.id ?? carriage.carriageNumber}

                  value={carriage.carriageNumber ?? ""}
                >
                  {carriage.carriageNumber}
                </option>
              ))}
            </select>
          </label>

          <label className="form-field">
            <span>Seat</span>

            <select
              value={seatNumber}

              onChange={(e) => setSeatNumber(e.target.value)}
            >
              {seats.map((seat) => (
                <option
                  key={seat.id ?? seat.seatNumber}

                  value={seat.seatNumber ?? ""}
                >
                  {seat.seatNumber}
                </option>
              ))}
            </select>
          </label>
        </div>

        <div className="form-row">
          <label className="form-field">
            <span>Type</span>

            <select
              value={type}

              onChange={(e) => {
                const value = e.target.value;

                if (
                  value === "" ||
                  value === "VIP" ||
                  value === "USUAL" ||
                  value === "CHEAP"
                ) {
                  setType(value);
                }
              }}
            >
              <option value="">None</option>

              <option value="VIP">VIP</option>

              <option value="USUAL">USUAL</option>

              <option value="CHEAP">CHEAP</option>
            </select>
          </label>

          <label className="form-field">
            <span>Refundable</span>

            <select
              value={refundable}

              onChange={(e) => {
                const value = e.target.value;

                if (value === "true" || value === "false" || value === "null") {
                  setRefundable(value);
                }
              }}
            >
              <option value="true">Yes</option>

              <option value="false">No</option>

              <option value="null">Not specified</option>
            </select>
          </label>
        </div>

        <div className="selected-ticket">
          <span>Price and discount are changed through Price History.</span>

          <strong>
            Current price: {ticket.basePrice ?? "—"}, discount:{" "}
            {ticket.discount}%
          </strong>
        </div>

        {error && <div className="booking-alert error">{error}</div>}

        <div className="modal-actions">
          <button
            type="button"
            className="cancel-button"
            disabled={loading}
            onClick={onClose}
          >
            Cancel
          </button>

          <button
            type="submit"
            className="submit-booking-button"
            disabled={loading}
          >
            {loading ? "Saving..." : "Save"}
          </button>
        </div>
      </form>
    </div>
  );
}
