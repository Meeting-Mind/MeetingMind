import { useEffect, type ReactNode } from "react";
import { Navigate, useLocation } from "react-router-dom";
import type { AuthSession } from "../../auth/session";

export function ProtectedRoute({
  children,
  loading,
  onRequestLogin,
  session
}: {
  children: ReactNode;
  loading: boolean;
  onRequestLogin: () => void;
  session: AuthSession | null;
}) {
  const location = useLocation();

  useEffect(() => {
    if (!loading && !session) {
      onRequestLogin();
    }
  }, [loading, location.hash, location.pathname, location.search, onRequestLogin, session]);

  if (loading) {
    return null;
  }

  if (!session) {
    return <Navigate replace state={{ requestedPath: `${location.pathname}${location.search}${location.hash}` }} to="/" />;
  }

  return <>{children}</>;
}
