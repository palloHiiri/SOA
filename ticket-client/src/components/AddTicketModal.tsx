import { type FormEvent, useEffect, useState } from "react";

import type {
  Carriage,
  Seat,
  Ticket,
  TicketType,
  TrainSet,
  Venue,
} from "../types/ticket";

import {
  createTicket,
  createVenue,
  fetchCarriages,
  fetchSeats,
  fetchTrainSets,
  fetchVenues,
} from "../api/ticketApi";

interface AddTicketModalProps {
  onClose: () => void;
  onCreated: (ticket: Ticket) => void;
}

type VenueMode = "existing" | "new";

export function AddTicketModal({ onClose, onCreated }: AddTicketModalProps) {
  // Состояние справочных данных.

  const [trainSets, setTrainSets] = useState<TrainSet[]>([]);

  const [venues, setVenues] = useState<Venue[]>([]);

  const [carriages, setCarriages] = useState<Carriage[]>([]);

  const [seats, setSeats] = useState<Seat[]>([]);

  // Состояние выбранного поезда и маршрута.
  const [trainSetId, setTrainSetId] = useState<number | null>(null);

  const [venueMode, setVenueMode] = useState<VenueMode>("existing");

  const [venueId, setVenueId] = useState<number | null>(null);

  const [newVenueName, setNewVenueName] = useState("");

  // Состояние выбранного места.
  const [carriageNumber, setCarriageNumber] = useState("");

  const [seatNumber, setSeatNumber] = useState("");

  // Состояние создаваемого билета.
  const [name, setName] = useState("");

  const [basePrice, setBasePrice] = useState("");

  const [discount, setDiscount] = useState(1);

  const [refundable, setRefundable] = useState<"true" | "false" | "null">(
    "true",
  );

  const [type, setType] = useState<TicketType | "">("USUAL");

  const [loading, setLoading] = useState(false);

  const [error, setError] = useState<string | null>(null);

  // Загрузка поездов и маршрутов.

  useEffect(() => {
    async function loadInitialData() {
      try {
        setError(null);

        const [trainResponse, venueResponse] = await Promise.all([
          fetchTrainSets(),
          fetchVenues(),
        ]);

        setTrainSets(trainResponse.content);

        setVenues(venueResponse.content);

        if (trainResponse.content.length > 0) {
          setTrainSetId(trainResponse.content[0].id);
        }
      } catch (err) {
        setError(err instanceof Error ? err.message : "Failed to load data");
      }
    }

    void loadInitialData();
  }, []);

  // Обновление маршрутов при смене поезда.

  useEffect(() => {
    if (trainSetId === null || venueMode !== "existing") {
      return;
    }

    const matchingVenues = venues.filter(
      (venue) => venue.trainSetId === trainSetId,
    );

    const currentStillValid = matchingVenues.some(
      (venue) => venue.id === venueId,
    );

    if (!currentStillValid) {
      setVenueId(matchingVenues[0]?.id ?? null);
    }
  }, [trainSetId, venueMode, venues, venueId]);

  // Загрузка вагонов выбранного поезда.

  useEffect(() => {
    if (trainSetId === null) {
      return;
    }

    async function loadCarriages() {
      try {
        setError(null);

        setCarriageNumber("");
        setSeatNumber("");
        setSeats([]);

        const response = await fetchCarriages(trainSetId!);

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

    void loadCarriages();
  }, [trainSetId]);

  // Загрузка мест выбранного вагона.

  useEffect(() => {
    if (trainSetId === null || !carriageNumber) {
      return;
    }

    async function loadSeats() {
      try {
        setError(null);
        setSeatNumber("");

        const response = await fetchSeats(trainSetId!, carriageNumber);

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

    void loadSeats();
  }, [trainSetId, carriageNumber]);

  const availableVenues =
    trainSetId === null
      ? []
      : venues.filter((venue) => venue.trainSetId === trainSetId);

  // Создание маршрута и билета.

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();

    if (trainSetId === null) {
      setError("Select a train");

      return;
    }

    if (venueMode === "existing" && venueId === null) {
      setError("Select an existing route or create a new one");

      return;
    }

    if (venueMode === "new" && !newVenueName.trim()) {
      setError("Enter route name");

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

      let actualVenueId = venueId;

      if (venueMode === "new") {
        const createdVenue = await createVenue({
          name: newVenueName.trim(),

          trainSetId,
        });

        actualVenueId = createdVenue.id;
      }

      if (actualVenueId === null) {
        throw new Error("Venue is not selected");
      }

      const ticket = await createTicket({
        name: name.trim() || null,

        venueId: actualVenueId,

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

          <button type="button" className="close-button" onClick={onClose}>
            ×
          </button>
        </div>

        <label className="form-field">
          <span>Train *</span>

          <select
            value={trainSetId ?? ""}

            onChange={(event) => setTrainSetId(Number(event.target.value))}
          >
            {trainSets.map((train) => (
              <option key={train.id} value={train.id}>
                {train.name}

                {" — "}

                {train.code}

                {" — #"}

                {train.id}
              </option>
            ))}
          </select>
        </label>

        <div className="venue-mode-switch">
          <button
            type="button"

            className={
              venueMode === "existing"
                ? "venue-mode-button active"
                : "venue-mode-button"
            }

            onClick={() => setVenueMode("existing")}
          >
            Existing route
          </button>

          <button
            type="button"

            className={
              venueMode === "new"
                ? "venue-mode-button active"
                : "venue-mode-button"
            }

            onClick={() => setVenueMode("new")}
          >
            New route
          </button>
        </div>

        {venueMode === "existing" && (
          <label className="form-field">
            <span>Route *</span>

            {availableVenues.length > 0 ? (
              <select
                value={venueId ?? ""}

                onChange={(event) => setVenueId(Number(event.target.value))}
              >
                {availableVenues.map((venue) => (
                  <option key={venue.id} value={venue.id}>
                    {venue.name}
                  </option>
                ))}
              </select>
            ) : (
              <div className="no-passengers-warning">
                <span>This train has no routes yet.</span>

                <button
                  type="button"

                  onClick={() => setVenueMode("new")}
                >
                  Create route
                </button>
              </div>
            )}
          </label>
        )}

        {venueMode === "new" && (
          <label className="form-field">
            <span>New route name *</span>

            <input
              value={newVenueName}

              placeholder="Berlin → Hamburg"

              onChange={(event) => setNewVenueName(event.target.value)}
            />

            <small>The route will be linked to the selected train.</small>
          </label>
        )}

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
          <span>Ticket name</span>

          <input
            value={name}

            placeholder="Leave empty for automatic name"

            onChange={(event) => setName(event.target.value)}
          />
        </label>

        <div className="form-row">
          <label className="form-field">
            <span>Base price</span>

            <input
              type="number"
              min="0.01"
              step="0.01"

              value={basePrice}

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

              onChange={(event) => {
                const value = event.target.value;

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
