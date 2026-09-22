import "./App.css";

import { useEffect, useState } from "react";

import type { Ticket } from "./types/ticket";

import type { Passenger } from "./types/passenger";

import type { MeResponse } from "./types/auth";

import { fetchTickets } from "./api/ticketApi";

import { fetchPassengers } from "./api/passengerApi";

import { sellTicket, sellTicketWithDiscount } from "./api/bookingApi";

import { getCurrentUser, logout } from "./api/authApi";

import { LoginPage } from "./components/LoginPage";

import { RegisterPage } from "./components/RegisterPage";

import { PassengersPage } from "./components/PassengersPage";

type AuthMode = "login" | "register";

type Page = "tickets" | "passengers";

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

  // Состояние списка билетов.
  const [tickets, setTickets] = useState<Ticket[]>([]);

  const [ticketsLoading, setTicketsLoading] = useState(false);

  const [ticketsError, setTicketsError] = useState<string | null>(null);

  // Состояние списка пассажиров.
  const [passengers, setPassengers] = useState<Passenger[]>([]);

  const [passengersLoading, setPassengersLoading] = useState(false);

  const [passengersError, setPassengersError] = useState<string | null>(null);

  // Состояние диалога продажи билета.
  const [dialog, setDialog] = useState<BookingDialog | null>(null);

  const [passengerId, setPassengerId] = useState("");

  const [discount, setDiscount] = useState(20);

  const [bookingLoading, setBookingLoading] = useState(false);

  const [bookingError, setBookingError] = useState<string | null>(null);

  const [bookingResult, setBookingResult] = useState<string | null>(null);

  // Проверка текущей сессии пользователя.
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

    checkAuthentication();
  }, []);

  // Загрузка данных после авторизации.
  useEffect(() => {
    if (!currentUser) {
      return;
    }

    loadTickets();
    loadPassengers();
  }, [currentUser]);

  // Загрузка билетов.
  async function loadTickets() {
    try {
      setTicketsLoading(true);
      setTicketsError(null);

      const response = await fetchTickets({
        page: 1,
        size: 100,
        sort: "id,asc",
      });

      setTickets(response.content);
    } catch (err) {
      setTicketsError(
        err instanceof Error ? err.message : "Failed to load tickets",
      );
    } finally {
      setTicketsLoading(false);
    }
  }

  // Загрузка пассажиров.
  async function loadPassengers() {
    try {
      setPassengersLoading(true);
      setPassengersError(null);

      const data = await fetchPassengers();

      setPassengers(data);
    } catch (err) {
      setPassengersError(
        err instanceof Error ? err.message : "Failed to load passengers",
      );
    } finally {
      setPassengersLoading(false);
    }
  }

  // Открытие диалога продажи.
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

  // Закрытие диалога продажи.
  function closeDialog() {
    if (bookingLoading) {
      return;
    }

    setDialog(null);

    setPassengerId("");

    setBookingError(null);
    setBookingResult(null);
  }

  // Обработка продажи билета.
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

  // Выход из аккаунта и очистка данных.
  async function handleLogout() {
    try {
      await logout();
    } catch (err) {
      console.error("Logout failed:", err);
    } finally {
      setCurrentUser(null);

      setTickets([]);
      setPassengers([]);

      setActivePage("tickets");
      setAuthMode("login");

      setDialog(null);
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

  return (
    <main className="container">
      {/* Шапка приложения. */}
      <header className="header">
        <div>
          <h1>Snezhnaya Railway</h1>

          <p>Railway ticket management system</p>
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

      {/* Навигация между разделами. */}
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
            activePage === "passengers" ? "nav-button active" : "nav-button"
          }

          onClick={() => setActivePage("passengers")}
        >
          Passengers
          <span className="nav-count">{passengers.length}</span>
        </button>
      </nav>

      {/* Раздел управления билетами. */}
      {activePage === "tickets" && (
        <section>
          <div className="section-header">
            <div>
              <h2>Tickets</h2>

              <p>Available railway tickets</p>
            </div>
          </div>

          {ticketsLoading && <p className="message">Loading tickets...</p>}

          {ticketsError && <p className="message error">{ticketsError}</p>}

          {!ticketsLoading && !ticketsError && tickets.length === 0 && (
            <p className="message">No tickets available.</p>
          )}

          {!ticketsLoading && !ticketsError && tickets.length > 0 && (
            <div className="ticket-wrapper">
              <table className="ticket-table">
                <thead>
                  <tr>
                    <th>ID</th>

                    <th>Name</th>

                    <th>Venue</th>

                    <th>Train set</th>

                    <th>Carriage</th>

                    <th>Seat</th>

                    <th>Price</th>

                    <th>Discount</th>

                    <th>Type</th>

                    <th>Refundable</th>

                    <th>Actions</th>
                  </tr>
                </thead>

                <tbody>
                  {tickets.map((ticket) => (
                    <tr key={ticket.id}>
                      <td>{ticket.id}</td>

                      <td>
                        <div className="ticket-name">{ticket.name}</div>

                        <div className="secondary-text">
                          {ticket.creationDate}
                        </div>
                      </td>

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
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      )}

      {/* Раздел управления пассажирами. */}
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

      {/* Диалог продажи билета. */}
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

            {bookingError && (
              <div className="booking-alert error">{bookingError}</div>
            )}

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
    </main>
  );
}

export default App;
