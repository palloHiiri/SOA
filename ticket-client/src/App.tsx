
import './App.css'
import {useEffect, useState} from "react";
import type {Ticket} from "./types/ticket";
import {fetchTickets} from "./api/ticketApi";

function App() {
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  
  useEffect(() => {
    async function loadTickets() {
      try {
        const data = await fetchTickets();
        setTickets(data);
      } catch (err) {
        setError((err as Error).message);
      } finally {
        setLoading(false);
      }
    }

    loadTickets();
  }, []);

  if(loading){
    return <p className="message">Loading...</p>
  }

  if(error){
    return <p className="message error">{error}</p>
  }

  return (
    <main className = "container">
      <div className='header'>
        <div>
          <h1>Snezhnaya Railway List</h1>
          <p>Tickets management</p>
        </div>
        <button className='add-ticket-button' type="button">Add Ticket</button>
      </div>
      {tickets.length === 0 ? (
        <p>No tickets available.</p>
      ) : (
        <div className='ticket-wrapper'>
          <table className='ticket-table'>
            <thead>
              <tr>
                <th>ID</th>
                <th>Name</th>
                <th>Coordinates</th>
                <th>Creation Date</th>
                <th>Price</th>
                <th>Discount</th>
                <th>Refundable</th>
                <th>Type</th>
                <th>Venue</th>
              </tr>
            </thead>
            <tbody>
              {tickets.map((ticket) => (
                <tr key={ticket.id}>
                  <td>{ticket.id}</td>
                  <td>{ticket.name}</td>
                  <td>{`(${ticket.coordinates.x}, ${ticket.coordinates.y})`}</td>
                  <td>{ticket.creationDate}</td>
                  <td>{ticket.price}</td>
                  <td>{ticket.discount}</td>
                  <td>{ticket.refundable ? "Yes" : "No"}</td>
                  <td>{ticket.type}</td>
                  <td>
                    {ticket.venue ? (
                      <div className="venue-details">
                        <strong>{ticket.venue.name}</strong>
                        <span>ID: {ticket.venue.id ?? "N/A"}</span>
                        <span>Capacity: {ticket.venue.capacity}</span>
                      </div>
                    ) : "N/A"}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </main>
  );
}

export default App
