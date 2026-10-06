import "./App.css";

import { useCallback, useEffect, useRef, useState } from "react";

import type { Ticket, TicketRequest, TicketResponse } from "./types/ticket";

import type { Passenger } from "./types/passenger";

import type { MeResponse } from "./types/auth";

import { deleteTicket, fetchTickets } from "./api/ticketApi";

import { fetchPassengers } from "./api/passengerApi";

import { sellTicket, sellTicketWithDiscount } from "./api/bookingApi";

import { getCurrentUser, logout } from "./api/authApi";

import { LoginPage } from "./components/LoginPage";

import { RegisterPage } from "./components/RegisterPage";

import { PassengersPage } from "./components/PassengersPage";

import { AddTicketModal } from "./components/AddTicketModal";

import { EditTicketModal } from "./components/EditTicketModal";

import { TicketFilters } from "./components/TicketFilters";

import { TicketToolsPage } from "./components/TicketToolsPage";

import { VenuesPage } from "./components/VenuesPage";

import { ConfirmDialog } from "./components/ConfirmDialog";
import { ErrorNotice } from "./components/ErrorNotice";

type AuthMode = "login" | "register";

type Page = "tickets" | "routes" | "passengers" | "tools";

type BookingMode = "sell" | "discount";

interface BookingDialog {
  ticket: Ticket;
  mode: BookingMode;
}

function App() {
  // Состояние авторизации.

  const [currentUser, setCurrentUser] = useState<MeResponse | null>(null);

  const [authLoading, setAuthLoading] = useState(true);

  const [authMode, setAuthMode] = useState<AuthMode>("login");

  // Состояние навигации.

  const [activePage, setActivePage] = useState<Page>("tickets");

  // Состояние билетов.

  const [tickets, setTickets] = useState<Ticket[]>([]);

  const [loadedTicketRequest, setLoadedTicketRequest] =
    useState<TicketRequest | null>(null);

  const [ticketsError, setTicketsError] = useState<string | null>(null);

  const [ticketRequest, setTicketRequest] = useState<TicketRequest>({
    page: 1,
    size: 10,
    sort: "id,asc",
  });

  const ticketsLoading = loadedTicketRequest !== ticketRequest;

  const [ticketMeta, setTicketMeta] = useState<Omit<
    TicketResponse,
    "content"
  > | null>(null);

  const [deletingTicket, setDeletingTicket] = useState<Ticket | null>(null);
  const [deleteBusy, setDeleteBusy] = useState(false);
  const ticketLoadId = useRef(0);
  const [filterResetVersion, setFilterResetVersion] = useState(0);

  const [addTicketOpened, setAddTicketOpened] = useState(false);

  const [editingTicket, setEditingTicket] = useState<Ticket | null>(null);

  // Состояние пассажиров.

  const [passengers, setPassengers] = useState<Passenger[]>([]);

  const [passengersLoading, setPassengersLoading] = useState(true);

  const [passengersError, setPassengersError] = useState<string | null>(null);

  // Состояние продажи билета.

  const [dialog, setDialog] = useState<BookingDialog | null>(null);

  const [passengerId, setPassengerId] = useState("");

  const [discount, setDiscount] = useState(20);

  const [bookingLoading, setBookingLoading] = useState(false);

  const [bookingError, setBookingError] = useState<string | null>(null);

  const [bookingResult, setBookingResult] = useState<string | null>(null);

  // Проверка текущей сессии.

  useEffect(() => {
    async function checkAuthentication() {
      try {
        const user = await getCurrentUser();

        setCurrentUser(user);
      } catch (err) {
        console.error("Failed to check authentication:", err);

        setCurrentUser(null);
      } finally {
        setAuthLoading(false);
      }
    }

    void checkAuthentication();
  }, []);

  // Работа с API билетов.

  const loadTickets = useCallback(() => {
    const loadId = ++ticketLoadId.current;
    return fetchTickets(ticketRequest)
      .then((response) => {
        if (loadId !== ticketLoadId.current) return;
        setTicketsError(null);
        setTickets(response.content);
        const { page, size, totalElements, totalPages, hasNext, hasPrevious } =
          response;
        setTicketMeta({
          page,
          size,
          totalElements,
          totalPages,
          hasNext,
          hasPrevious,
        });
      })
      .catch((err: unknown) => {
        if (loadId !== ticketLoadId.current) return;
        setTicketsError(
          err instanceof Error ? err.message : "Failed to load tickets",
        );
      })
      .finally(() => {
        if (loadId === ticketLoadId.current)
          setLoadedTicketRequest(ticketRequest);
      });
  }, [ticketRequest]);

  // Работа с API пассажиров.
  function loadPassengers() {
    return fetchPassengers()
      .then((data) => {
        setPassengersError(null);
        setPassengers(data);
      })
      .catch((err: unknown) => {
        setPassengersError(
          err instanceof Error ? err.message : "Failed to load passengers",
        );
      })
      .finally(() => {
        setPassengersLoading(false);
      });
  }

  // Загрузка пассажиров после авторизации.

  useEffect(() => {
    if (!currentUser) {
      return;
    }

    void loadPassengers();
  }, [currentUser]);

  // Загрузка билетов при изменении запроса.

  useEffect(() => {
    if (!currentUser) {
      return;
    }

    void loadTickets();
    return () => {
      ticketLoadId.current += 1;
    };
  }, [currentUser, loadTickets]);

  // Открытие окна продажи.

  function openDialog(ticket: Ticket, mode: BookingMode) {
    setDialog({
      ticket,
      mode,
    });

    setPassengerId(passengers[0]?.id ?? "");

    setDiscount(20);

    setBookingError(null);
    setBookingResult(null);
  }

  function closeDialog() {
    if (bookingLoading) {
      return;
    }

    setDialog(null);

    setPassengerId("");

    setBookingError(null);
    setBookingResult(null);
  }

  async function handleBooking() {
    if (!dialog) {
      return;
    }

    if (!passengerId) {
      setBookingError("Select a passenger");

      return;
    }

    if (dialog.mode === "discount" && (discount < 1 || discount > 100)) {
      setBookingError("Discount must be between 1 and 100");

      return;
    }

    try {
      setBookingLoading(true);

      setBookingError(null);
      setBookingResult(null);

      if (dialog.mode === "sell") {
        const result = await sellTicket(dialog.ticket.id, passengerId);

        setBookingResult(`Ticket #${result.ticketId} sold for ${result.price}`);
      } else {
        const result = await sellTicketWithDiscount(
          dialog.ticket.id,
          passengerId,
          discount,
        );

        setBookingResult(
          `Ticket #${result.ticketId} created and sold for ${result.price}`,
        );

        await loadTickets();
      }
    } catch (err) {
      setBookingError(err instanceof Error ? err.message : "Booking failed");
    } finally {
      setBookingLoading(false);
    }
  }

  // Удаление билета.

  async function handleDeleteTicket(ticket: Ticket) {
    setDeleteBusy(true);
    try {
      setTicketsError(null);

      await deleteTicket(ticket.id);

      if (tickets.length === 1 && (ticketRequest.page ?? 1) > 1) {
        setTicketRequest((current) => ({
          ...current,

          page: (current.page ?? 1) - 1,
        }));

        return;
      }

      await loadTickets();
    } catch (err) {
      setTicketsError(
        err instanceof Error ? err.message : "Failed to delete ticket",
      );
    } finally {
      setDeleteBusy(false);
      setDeletingTicket(null);
    }
  }

  // Выход из аккаунта и очистка данных.

  async function handleLogout() {
    try {
      await logout();
    } catch (err) {
      console.error("Logout failed:", err);
    } finally {
      setCurrentUser(null);

      setLoadedTicketRequest(null);
      setPassengersLoading(true);
      setTickets([]);
      setPassengers([]);

      setTicketMeta(null);

      setTicketRequest({
        page: 1,
        size: 10,
        sort: "id,asc",
      });

      setDeletingTicket(null);

      setActivePage("tickets");
      setAuthMode("login");

      setDialog(null);
      setEditingTicket(null);
      setAddTicketOpened(false);
    }
  }

  // Экран проверки авторизации.

  if (authLoading) {
    return <p className="message">Checking session...</p>;
  }

  // Экраны входа и регистрации.

  if (!currentUser) {
    if (authMode === "register") {
      return (
        <RegisterPage
          onLoginClick={() => setAuthMode("login")}

          onRegistered={() => setAuthMode("login")}
        />
      );
    }

    return (
      <LoginPage
        onRegisterClick={() => setAuthMode("register")}

        onLogin={async () => {
          try {
            const user = await getCurrentUser();

            setCurrentUser(user);
          } catch (err) {
            console.error("Failed to load current user:", err);
          }
        }}
      />
    );
  }

  // Основной экран приложения.

  return (
    <main className="container">
      {deletingTicket && (
        <ConfirmDialog
          message={`Delete ticket #${deletingTicket.id} "${deletingTicket.name}"?`}
          busy={deleteBusy}
          onCancel={() => setDeletingTicket(null)}
          onConfirm={() => void handleDeleteTicket(deletingTicket)}
        />
      )}
      {/* Шапка приложения. */}

      <header className="header">
        <div>
          <h1>Snezhnaya Railway</h1>
        </div>

        <div className="header-actions">
          <div className="current-user">
            <span>Signed in</span>

            <strong>
              {currentUser.attributes.username ??
                currentUser.attributes.email ??
                currentUser.userId}
            </strong>
          </div>

          <button
            type="button"
            className="logout-button"

            onClick={handleLogout}
          >
            Logout
          </button>
        </div>
      </header>

      {/* Навигация приложения. */}

      <nav className="main-navigation">
        <button
          type="button"

          className={
            activePage === "tickets" ? "nav-button active" : "nav-button"
          }

          onClick={() => setActivePage("tickets")}
        >
          Tickets
        </button>

        <button
          type="button"

          className={
            activePage === "routes" ? "nav-button active" : "nav-button"
          }

          onClick={() => setActivePage("routes")}
        >
          Routes
        </button>

        <button
          type="button"

          className={
            activePage === "passengers" ? "nav-button active" : "nav-button"
          }

          onClick={() => setActivePage("passengers")}
        >
          Passengers
          <span className="nav-count">{passengers.length}</span>
        </button>

        <button
          type="button"

          className={
            activePage === "tools" ? "nav-button active" : "nav-button"
          }

          onClick={() => setActivePage("tools")}
        >
          Tools
        </button>
      </nav>

      {/* Раздел билетов. */}

      {activePage === "tickets" && (
        <section>
          <div className="section-header">
            <div>
              <h2>Tickets</h2>
            </div>

            <div className="section-actions">
              <button
                type="button"
                className="add-ticket-button"

                onClick={() => setAddTicketOpened(true)}
              >
                Add Ticket
              </button>
            </div>
          </div>

          {ticketsError && <ErrorNotice message={ticketsError} />}
          <div className="table-toolbar">
            <button
              type="button"
              className="filter-reset"
              onClick={() => {
                setFilterResetVersion((current) => current + 1);
                setTicketRequest((current) => ({
                  page: 1,
                  size: current.size ?? 10,
                  sort: "id,asc",
                }));
              }}
            >
              Reset filters
            </button>
          </div>
          <>
            <div className="ticket-wrapper">
              <table className="ticket-table tickets-list-table">
                <colgroup>
                  {[
                    100, 220, 145, 220, 120, 120, 110, 120, 120, 120, 130, 240,
                  ].map((width, index) => (
                    <col key={index} style={{ width }} />
                  ))}
                </colgroup>
                <thead>
                  <TicketFilters
                    key={filterResetVersion}
                    request={ticketRequest}
                    onApply={setTicketRequest}
                  />
                </thead>

                <tbody>
                  {ticketsLoading ? (
                    <tr>
                      <td colSpan={12} className="table-state">
                        Loading tickets…
                      </td>
                    </tr>
                  ) : tickets.length === 0 ? (
                    <tr>
                      <td colSpan={12} className="table-state">
                        No tickets found.
                      </td>
                    </tr>
                  ) : null}

                  {!ticketsLoading &&
                    tickets.map((ticket) => (
                      <tr key={ticket.id}>
                        <td>{ticket.id}</td>

                        <td>
                          <div className="ticket-name">{ticket.name}</div>
                        </td>
                        <td>{ticket.creationDate}</td>

                        <td>
                          <div className="venue-details">
                            <strong>{ticket.venue.name}</strong>

                            <span>Venue ID: {ticket.venue.id}</span>
                          </div>
                        </td>

                        <td>{ticket.venue.trainSetId}</td>

                        <td>{ticket.carriageNumber}</td>

                        <td>{ticket.seatNumber}</td>

                        <td>
                          {ticket.basePrice !== null ? ticket.basePrice : "—"}
                        </td>

                        <td>{ticket.discount}%</td>

                        <td>{ticket.type ?? "—"}</td>

                        <td>
                          {ticket.refundable === null
                            ? "—"
                            : ticket.refundable
                              ? "Yes"
                              : "No"}
                        </td>

                        <td>
                          <div className="ticket-actions">
                            <button
                              type="button"
                              className="action-button"

                              onClick={() => openDialog(ticket, "sell")}
                            >
                              Sell
                            </button>

                            <button
                              type="button"
                              className="action-button secondary"

                              onClick={() => openDialog(ticket, "discount")}
                            >
                              Special
                            </button>

                            <button
                              type="button"
                              className="action-button edit"

                              onClick={() => setEditingTicket(ticket)}
                            >
                              Edit
                            </button>

                            <button
                              type="button"
                              className="action-button delete"

                              onClick={() => setDeletingTicket(ticket)}
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

            {/* Пагинация билетов. */}

            {ticketMeta && (
              <div className="pagination">
                <button
                  type="button"

                  disabled={!ticketMeta.hasPrevious}

                  onClick={() =>
                    setTicketRequest((current) => ({
                      ...current,

                      page: Math.max(
                        1,

                        (current.page ?? 1) - 1,
                      ),
                    }))
                  }
                >
                  ← Previous
                </button>

                <div className="pagination-info">
                  <strong>
                    Page {ticketMeta.totalPages > 0 ? ticketMeta.page : 0}
                    {" / "}
                    {ticketMeta.totalPages}
                  </strong>

                  <span>{ticketMeta.totalElements} tickets</span>
                </div>

                <button
                  type="button"

                  disabled={!ticketMeta.hasNext}

                  onClick={() =>
                    setTicketRequest((current) => ({
                      ...current,

                      page: (current.page ?? 1) + 1,
                    }))
                  }
                >
                  Next →
                </button>
                <label className="page-size">
                  Per page
                  <select
                    aria-label="Tickets per page"
                    value={ticketRequest.size ?? 10}
                    onChange={(event) =>
                      setTicketRequest((current) => ({
                        ...current,
                        page: 1,
                        size: Number(event.target.value),
                      }))
                    }
                  >
                    {[5, 10, 20, 50].map((size) => (
                      <option key={size} value={size}>
                        {size}
                      </option>
                    ))}
                  </select>
                </label>
              </div>
            )}
          </>
        </section>
      )}

      {activePage === "routes" && <VenuesPage />}

      {/* Раздел пассажиров. */}

      {activePage === "passengers" && (
        <PassengersPage
          passengers={passengers}

          loading={passengersLoading}

          error={passengersError}

          onPassengerCreated={(passenger) => {
            setPassengers((current) => [...current, passenger]);
          }}
        />
      )}

      {/* Инструменты билетов. */}

      {activePage === "tools" && <TicketToolsPage />}

      {/* Окно продажи билета. */}

      {dialog && (
        <div
          className="modal-backdrop"

          onMouseDown={closeDialog}
        >
          <div
            className="booking-modal"

            onMouseDown={(event) => event.stopPropagation()}
          >
            <div className="modal-header">
              <div>
                <span className="modal-eyebrow">
                  Ticket #{dialog.ticket.id}
                </span>

                <h2>
                  {dialog.mode === "sell" ? "Sell ticket" : "Special discount"}
                </h2>
              </div>

              <button
                type="button"
                className="close-button"

                onClick={closeDialog}
              >
                ×
              </button>
            </div>

            <div className="selected-ticket">
              <strong>{dialog.ticket.name}</strong>

              <span>
                {dialog.ticket.venue.name}
                {" · "}
                Carriage {dialog.ticket.carriageNumber}
                {" · "}
                Seat {dialog.ticket.seatNumber}
              </span>

              <span>Current price: {dialog.ticket.basePrice ?? "—"}</span>
            </div>

            <label className="form-field">
              <span>Passenger</span>

              {passengers.length === 0 ? (
                <div className="no-passengers-warning">
                  <span>No passengers linked to your account.</span>

                  <button
                    type="button"

                    onClick={() => {
                      closeDialog();

                      setActivePage("passengers");
                    }}
                  >
                    Add passenger
                  </button>
                </div>
              ) : (
                <select
                  value={passengerId}

                  onChange={(event) => setPassengerId(event.target.value)}
                >
                  {passengers.map((passenger) => (
                    <option
                      key={passenger.id}

                      value={passenger.id}
                    >
                      {passenger.firstName} {passenger.lastName}
                      {" — "}
                      {passenger.documentSeriesNumber}{" "}
                      {passenger.documentNumber}
                    </option>
                  ))}
                </select>
              )}
            </label>

            {dialog.mode === "discount" && (
              <label className="form-field">
                <span>Discount / price increase %</span>

                <input
                  type="number"
                  min="1"
                  max="100"

                  value={discount}

                  onChange={(event) => setDiscount(Number(event.target.value))}
                />

                {dialog.ticket.basePrice !== null && (
                  <small>
                    New base price:{" "}
                    {(dialog.ticket.basePrice * (1 + discount / 100)).toFixed(
                      2,
                    )}
                  </small>
                )}
              </label>
            )}

            {bookingError && <ErrorNotice message={bookingError} />}

            {bookingResult && (
              <div className="booking-alert success">{bookingResult}</div>
            )}

            <div className="modal-actions">
              <button
                type="button"
                className="cancel-button"

                disabled={bookingLoading}

                onClick={closeDialog}
              >
                Cancel
              </button>

              <button
                type="button"
                className="submit-booking-button"

                disabled={bookingLoading || passengers.length === 0}

                onClick={handleBooking}
              >
                {bookingLoading
                  ? "Processing..."
                  : dialog.mode === "sell"
                    ? "Sell ticket"
                    : "Create & sell"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Окно добавления билета. */}

      {addTicketOpened && (
        <AddTicketModal
          onClose={() => setAddTicketOpened(false)}

          onCreated={() => {
            setAddTicketOpened(false);

            void loadTickets();
          }}
        />
      )}

      {/* Окно редактирования билета. */}

      {editingTicket && (
        <EditTicketModal
          ticket={editingTicket}

          onClose={() => setEditingTicket(null)}

          onUpdated={() => {
            setEditingTicket(null);

            void loadTickets();
          }}
        />
      )}
    </main>
  );
}

export default App;
