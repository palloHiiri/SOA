import { type FormEvent, useEffect, useState } from "react";

import type {
  Carriage,
  Seat,
  Ticket,
  TicketType,
  Venue,
} from "../types/ticket";

import {
  createTicket,
  fetchCarriages,
  fetchSeats,
  fetchVenues,
} from "../api/ticketApi";

interface AddTicketModalProps {
  onClose: () => void;

  onCreated: (ticket: Ticket) => void;
}

export function AddTicketModal({ onClose, onCreated }: AddTicketModalProps) {
  // Состояние данных маршрута.
  const [venues, setVenues] = useState<Venue[]>([]);

  const [carriages, setCarriages] = useState<Carriage[]>([]);

  const [seats, setSeats] = useState<Seat[]>([]);

  // Состояние выбранного места.
  const [venueId, setVenueId] = useState<number | null>(null);

  const [carriageNumber, setCarriageNumber] = useState("");

  const [seatNumber, setSeatNumber] = useState("");

  // Состояние данных билета.
  const [name, setName] = useState("");

  const [basePrice, setBasePrice] = useState("");

  const [discount, setDiscount] = useState(1);

  const [refundable, setRefundable] = useState<"true" | "false" | "null">(
    "true",
  );

  const [type, setType] = useState<TicketType | "">("USUAL");

  // Состояние отправки формы.
  const [loading, setLoading] = useState(false);

  const [error, setError] = useState<string | null>(null);

  // Загрузка площадок.
  useEffect(() => {
    async function load() {
      try {
        const response = await fetchVenues();

        setVenues(response.content);

        if (response.content.length > 0) {
          setVenueId(response.content[0].id);
        }
      } catch (err) {
        setError(err instanceof Error ? err.message : "Failed to load venues");
      }
    }

    load();
  }, []);

  // Загрузка вагонов выбранной площадки.
  useEffect(() => {
    if (venueId === null) {
      return;
    }

    const venue = venues.find((value) => value.id === venueId);

    if (!venue) {
      return;
    }

    async function load() {
      try {
        setCarriageNumber("");
        setSeatNumber("");
        setSeats([]);

        const response = await fetchCarriages(venue!.trainSetId);

        const valid = response.content.filter(
          (carriage) => carriage.carriageNumber !== null,
        );

        setCarriages(valid);

        if (valid.length > 0 && valid[0].carriageNumber) {
          setCarriageNumber(valid[0].carriageNumber);
        }
      } catch (err) {
        setError(
          err instanceof Error ? err.message : "Failed to load carriages",
        );
      }
    }

    load();
  }, [venueId, venues]);

  // Загрузка мест выбранного вагона.
  useEffect(() => {
    if (venueId === null || !carriageNumber) {
      return;
    }

    const venue = venues.find((value) => value.id === venueId);

    if (!venue) {
      return;
    }

    async function load() {
      try {
        setSeatNumber("");

        const response = await fetchSeats(venue!.trainSetId, carriageNumber);

        const valid = response.content.filter(
          (seat) => seat.seatNumber !== null,
        );

        setSeats(valid);

        if (valid.length > 0 && valid[0].seatNumber) {
          setSeatNumber(valid[0].seatNumber);
        }
      } catch (err) {
        setError(err instanceof Error ? err.message : "Failed to load seats");
      }
    }

    load();
  }, [carriageNumber, venueId, venues]);

  // Обработка создания билета.
  async function handleSubmit(event: FormEvent) {
    event.preventDefault();

    if (venueId === null) {
      setError("Select a venue");
      return;
    }

    if (!carriageNumber) {
      setError("Select a carriage");
      return;
    }

    if (!seatNumber) {
      setError("Select a seat");
      return;
    }

    if (discount < 1 || discount > 100) {
      setError("Discount must be between 1 and 100");

      return;
    }

    try {
      setLoading(true);
      setError(null);

      const ticket = await createTicket({
        name: name.trim() || null,

        venueId,

        carriageNumber,
        seatNumber,

        basePrice: basePrice ? Number(basePrice) : null,

        discount,

        refundable: refundable === "null" ? null : refundable === "true",

        type: type || null,
      });

      onCreated(ticket);

      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create ticket");
    } finally {
      setLoading(false);
    }
  }

  // Форма создания билета.
  return (
    <div
      className="modal-backdrop"

      onMouseDown={() => {
        if (!loading) {
          onClose();
        }
      }}
    >
      <form
        className="booking-modal passenger-modal"

        onSubmit={handleSubmit}

        onMouseDown={(event) => event.stopPropagation()}
      >
        <div className="modal-header">
          <div>
            <span className="modal-eyebrow">Tickets</span>

            <h2>Add ticket</h2>
          </div>

          <button
            type="button"
            className="close-button"

            onClick={onClose}
          >
            ×
          </button>
        </div>

        <label className="form-field">
          <span>Venue *</span>

          <select
            value={venueId ?? ""}

            onChange={(event) => setVenueId(Number(event.target.value))}
          >
            {venues.map((venue) => (
              <option key={venue.id} value={venue.id}>
                {venue.name}
                {" — "}
                Train set #{venue.trainSetId}
              </option>
            ))}
          </select>
        </label>

        <div className="form-row">
          <label className="form-field">
            <span>Carriage *</span>

            <select
              value={carriageNumber}

              onChange={(event) => setCarriageNumber(event.target.value)}
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
            <span>Seat *</span>

            <select
              value={seatNumber}

              onChange={(event) => setSeatNumber(event.target.value)}
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

        <label className="form-field">
          <span>Name</span>

          <input
            value={name}

            placeholder="Leave empty for automatic name"

            onChange={(event) => setName(event.target.value)}
          />

          <small>If empty, server generates the ticket name.</small>
        </label>

        <div className="form-row">
          <label className="form-field">
            <span>Base price</span>

            <input
              type="number"
              min="0.01"
              step="0.01"

              value={basePrice}

              placeholder="5000"

              onChange={(event) => setBasePrice(event.target.value)}
            />
          </label>

          <label className="form-field">
            <span>Discount *</span>

            <input
              type="number"
              min="1"
              max="100"

              value={discount}

              onChange={(event) => setDiscount(Number(event.target.value))}
            />
          </label>
        </div>

        <div className="form-row">
          <label className="form-field">
            <span>Type</span>

            <select
              value={type}
              onChange={(event) => {
                const value = event.target.value;

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

              onChange={(event) =>
                setRefundable(event.target.value as "true" | "false" | "null")
              }
            >
              <option value="true">Yes</option>

              <option value="false">No</option>

              <option value="null">Not specified</option>
            </select>
          </label>
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
            {loading ? "Creating..." : "Create ticket"}
          </button>
        </div>
      </form>
    </div>
  );
}
