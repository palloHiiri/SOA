import { type FormEvent, useEffect, useState } from "react";

import type { TrainSet, Venue } from "../types/ticket";

import {
  createVenue,
  deleteVenue,
  fetchTrainSets,
  fetchVenues,
  updateVenue,
} from "../api/ticketApi";

interface VenueFormState {
  id: number | null;
  name: string;
  trainSetId: number | null;
}

export function VenuesPage() {
  // Состояние маршрутов и поездов.
  const [venues, setVenues] = useState<Venue[]>([]);

  const [trainSets, setTrainSets] = useState<TrainSet[]>([]);

  const [loading, setLoading] = useState(true);

  const [error, setError] = useState<string | null>(null);

  const [form, setForm] = useState<VenueFormState | null>(null);

  // Загрузка данных страницы.

  async function loadData() {
    try {
      setLoading(true);
      setError(null);

      const [venueResponse, trainResponse] = await Promise.all([
        fetchVenues(),
        fetchTrainSets(),
      ]);

      setVenues(venueResponse.content);

      setTrainSets(trainResponse.content);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load routes");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadData();
  }, []);

  // Открытие формы создания маршрута.

  function openCreate() {
    setForm({
      id: null,
      name: "",
      trainSetId: trainSets[0]?.id ?? null,
    });
  }

  // Открытие формы редактирования маршрута.

  function openEdit(venue: Venue) {
    setForm({
      id: venue.id,
      name: venue.name,
      trainSetId: venue.trainSetId,
    });
  }

  // Создание или обновление маршрута.

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();

    if (!form) {
      return;
    }

    if (!form.name.trim()) {
      setError("Route name is required");

      return;
    }

    if (form.trainSetId === null) {
      setError("Select a train");

      return;
    }

    try {
      setError(null);

      if (form.id === null) {
        await createVenue({
          name: form.name.trim(),

          trainSetId: form.trainSetId,
        });
      } else {
        await updateVenue(form.id, {
          name: form.name.trim(),

          trainSetId: form.trainSetId,
        });
      }

      setForm(null);

      await loadData();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save route");
    }
  }

  // Удаление маршрута.

  async function handleDelete(venue: Venue) {
    const confirmed = window.confirm(`Delete route "${venue.name}"?`);

    if (!confirmed) {
      return;
    }

    try {
      setError(null);

      await deleteVenue(venue.id);

      await loadData();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to delete route");
    }
  }

  // Получение отображаемого имени поезда.

  function trainName(trainSetId: number) {
    const train = trainSets.find((value) => value.id === trainSetId);

    if (!train) {
      return `Train #${trainSetId}`;
    }

    return `${train.name} (${train.code})`;
  }

  // Основной экран маршрутов.

  return (
    <section>
      <div className="section-header">
        <div>
          <h2>Routes</h2>

          <p>Manage routes and their trains</p>
        </div>

        <button
          type="button"
          className="add-ticket-button"

          onClick={openCreate}
        >
          Add Route
        </button>
      </div>

      {error && <div className="booking-alert error">{error}</div>}

      {loading && <p className="message">Loading routes...</p>}

      {!loading && venues.length === 0 && (
        <p className="message">No routes yet.</p>
      )}

      {!loading && venues.length > 0 && (
        <div className="route-table-wrapper">
          <table className="ticket-table">
            <thead>
              <tr>
                <th>ID</th>

                <th>Route</th>

                <th>Train</th>

                <th>Actions</th>
              </tr>
            </thead>

            <tbody>
              {venues.map((venue) => (
                <tr key={venue.id}>
                  <td>{venue.id}</td>

                  <td>{venue.name}</td>

                  <td>{trainName(venue.trainSetId)}</td>

                  <td>
                    <div className="ticket-actions">
                      <button
                        type="button"
                        className="action-button edit"

                        onClick={() => openEdit(venue)}
                      >
                        Edit
                      </button>

                      <button
                        type="button"
                        className="action-button delete"

                        onClick={() => void handleDelete(venue)}
                      >
                        Delete
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {form && (
        <div
          className="modal-backdrop"

          onMouseDown={() => setForm(null)}
        >
          <form
            className="booking-modal"

            onSubmit={handleSubmit}

            onMouseDown={(event) => event.stopPropagation()}
          >
            <div className="modal-header">
              <div>
                <span className="modal-eyebrow">Routes</span>

                <h2>{form.id === null ? "Add route" : "Edit route"}</h2>
              </div>

              <button
                type="button"
                className="close-button"

                onClick={() => setForm(null)}
              >
                ×
              </button>
            </div>

            <label className="form-field">
              <span>Route name *</span>

              <input
                value={form.name}

                placeholder="Berlin → Hamburg"

                onChange={(event) =>
                  setForm((current) =>
                    current
                      ? {
                          ...current,
                          name: event.target.value,
                        }
                      : null,
                  )
                }
              />
            </label>

            <label className="form-field">
              <span>Train *</span>

              <select
                value={form.trainSetId ?? ""}

                onChange={(event) =>
                  setForm((current) =>
                    current
                      ? {
                          ...current,

                          trainSetId: Number(event.target.value),
                        }
                      : null,
                  )
                }
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

            {form.id !== null && (
              <div className="selected-ticket">
                <span>
                  If this route already has tickets, the backend may forbid
                  changing its train.
                </span>
              </div>
            )}

            <div className="modal-actions">
              <button
                type="button"
                className="cancel-button"

                onClick={() => setForm(null)}
              >
                Cancel
              </button>

              <button type="submit" className="submit-booking-button">
                {form.id === null ? "Create route" : "Save"}
              </button>
            </div>
          </form>
        </div>
      )}
    </section>
  );
}
