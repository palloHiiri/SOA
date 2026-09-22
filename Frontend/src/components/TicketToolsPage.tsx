import { useState } from "react";

import type { Ticket } from "../types/ticket";

import {
  fetchAverageDiscount,
  fetchLatestTicket,
  fetchTicketsBelowDiscount,
} from "../api/ticketApi";

export function TicketToolsPage() {
  const [latest, setLatest] = useState<Ticket | null>(null);

  const [average, setAverage] = useState<number | null>(null);

  const [threshold, setThreshold] = useState(20);

  const [below, setBelow] = useState<Ticket[]>([]);

  const [error, setError] = useState<string | null>(null);

  async function handleLatest() {
    try {
      setError(null);

      setLatest(await fetchLatestTicket());
    } catch (err) {
      setError(err instanceof Error ? err.message : "Request failed");
    }
  }

  async function handleAverage() {
    try {
      setError(null);

      setAverage(await fetchAverageDiscount());
    } catch (err) {
      setError(err instanceof Error ? err.message : "Request failed");
    }
  }

  async function handleBelow() {
    try {
      setError(null);

      const response = await fetchTicketsBelowDiscount(threshold);

      setBelow(response.content);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Request failed");
    }
  }

  return (
    <section>
      <div className="section-header">
        <div>
          <h2>Ticket tools</h2>

          <p>Additional ticket operations</p>
        </div>
      </div>

      {error && <div className="booking-alert error">{error}</div>}

      <div className="tools-grid">
        <article className="tool-card">
          <h3>Latest ticket</h3>

          <p>Ticket with maximum creation date.</p>

          <button className="action-button" onClick={handleLatest}>
            Find
          </button>

          {latest && (
            <div className="tool-result">
              <strong>
                #{latest.id} {latest.name}
              </strong>

              <span>{latest.creationDate}</span>

              <span>{latest.basePrice ?? "—"}</span>
            </div>
          )}
        </article>

        <article className="tool-card">
          <h3>Average discount</h3>

          <p>Average current discount of all tickets.</p>

          <button className="action-button" onClick={handleAverage}>
            Calculate
          </button>

          {average !== null && (
            <div className="tool-number">{average.toFixed(2)}%</div>
          )}
        </article>

        <article className="tool-card">
          <h3>Discount below</h3>

          <p>Find tickets with a discount below a value.</p>

          <div className="tool-input">
            <input
              type="number"
              min="1"
              max="100"

              value={threshold}

              onChange={(event) => setThreshold(Number(event.target.value))}
            />

            <button className="action-button" onClick={handleBelow}>
              Search
            </button>
          </div>

          {below.length > 0 && (
            <div className="tool-ticket-list">
              {below.map((ticket) => (
                <div key={ticket.id}>
                  <strong>#{ticket.id}</strong>

                  <span>{ticket.name}</span>

                  <span>{ticket.discount}%</span>
                </div>
              ))}
            </div>
          )}
        </article>
      </div>
    </section>
  );
}
