import { ErrorNotice } from "./ErrorNotice";
import { ConfirmDialog } from "./ConfirmDialog";
import { type FormEvent, useCallback, useEffect, useState } from "react";

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

  const [deletingVenue, setDeletingVenue] = useState<Venue | null>(null);
  const [deleteBusy, setDeleteBusy] = useState(false);

  // Загрузка данных страницы.

  const loadData = useCallback(async () => {
    try {
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
  }, []);

  useEffect(() => {
    let active = true;
    void Promise.all([fetchVenues(), fetchTrainSets()])
      .then(([venueResponse, trainResponse]) => {
        if (!active) return;
        setVenues(venueResponse.content);
        setTrainSets(trainResponse.content);
      })
      .catch((err: unknown) => {
        if (active)
          setError(
            err instanceof Error ? err.message : "Failed to load routes",
          );
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  // Открытие формы создания маршрута.

  function openCreate() {
    setError(null);
    setForm({
      id: null,
      name: "",
      trainSetId: trainSets[0]?.id ?? null,
    });
  }

  // Открытие формы редактирования маршрута.

  function openEdit(venue: Venue) {
    setError(null);
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
    setDeleteBusy(true);
    try {
      setError(null);

      await deleteVenue(venue.id);

      await loadData();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to delete route");
    } finally {
      setDeleteBusy(false);
      setDeletingVenue(null);
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
      {deletingVenue && (
        <ConfirmDialog
          message={`Delete route "${deletingVenue.name}"?`}
          busy={deleteBusy}
          onCancel={() => setDeletingVenue(null)}
          onConfirm={() => void handleDelete(deletingVenue)}
        />
      )}

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

      {error && !form && <ErrorNotice message={error} />}

      {loading && <p className="message">Loading routes...</p>}

      {!loading && venues.length === 0 && (
        <p className="message">No routes yet.</p>
      )}

      {!loading && venues.length > 0 && (
        <div className="route-table-wrapper">
          <table className="ticket-table routes-list-table">
            <colgroup>
              <col style={{ width: 100 }} />
              <col style={{ width: 280 }} />
              <col style={{ width: 280 }} />
              <col style={{ width: 220 }} />
            </colgroup>
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

                        onClick={() => setDeletingVenue(venue)}
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

            {error && <ErrorNotice message={error} />}
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
