export function ErrorNotice({ message }: { message: string }) {
  return (
    <div className="error-notice" role="alert">
      <span className="error-notice-icon" aria-hidden="true">
        !
      </span>
      <div>
        <strong>Something went wrong</strong>
        <p>{message}</p>
      </div>
    </div>
  );
}
