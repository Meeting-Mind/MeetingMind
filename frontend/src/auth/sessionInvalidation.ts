type SessionInvalidListener = () => void;

const listeners = new Set<SessionInvalidListener>();

export function subscribeToSessionInvalid(listener: SessionInvalidListener) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

export async function notifyIfSessionInvalid(response: Response): Promise<void> {
  if (response.status !== 401) {
    return;
  }

  try {
    const payload = (await response.clone().json()) as { code?: unknown };
    if (payload.code !== "SESSION_INVALID") {
      return;
    }
  } catch {
    return;
  }

  listeners.forEach((listener) => {
    try {
      listener();
    } catch {
      // A UI listener must not replace or mask the original BFF response.
    }
  });
}
