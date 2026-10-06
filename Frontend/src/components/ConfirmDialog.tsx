import { useEffect, useRef } from "react";

export function ConfirmDialog({
  message,
  busy,
  onConfirm,
  onCancel,
}: {
  message: string;
  busy: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    dialog.current?.showModal();
  }, []);
  return (
    <dialog
      ref={dialog}
      className="confirm-dialog"
      aria-labelledby="confirm-title"
      onCancel={(event) => {
        event.preventDefault();
        if (!busy) onCancel();
      }}
    >
      <h2 id="confirm-title">Confirm deletion</h2>
      <p>{message}</p>
      <div className="modal-actions">
        <button
          type="button"
          className="cancel-button"
          disabled={busy}
          onClick={onCancel}
          autoFocus
        >
          Cancel
        </button>
        <button
          type="button"
          className="action-button delete"
          disabled={busy}
          onClick={onConfirm}
        >
          {busy ? "Deleting…" : "Delete"}
        </button>
      </div>
    </dialog>
  );
}
