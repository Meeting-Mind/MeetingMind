import { useEffect, useRef, useState } from "react";

type GoogleCredentialResponse = {
  credential?: string;
};

type GoogleIdentityApi = {
  accounts: {
    id: {
      initialize: (config: {
        client_id: string;
        callback: (response: GoogleCredentialResponse) => void;
      }) => void;
      renderButton: (
        element: HTMLElement,
        options: {
          theme?: "outline" | "filled_blue" | "filled_black";
          size?: "large" | "medium" | "small";
          shape?: "rectangular" | "pill" | "circle" | "square";
          text?: "signin_with" | "signup_with" | "continue_with" | "signin";
          width?: number;
        }
      ) => void;
    };
  };
};

declare global {
  interface Window {
    google?: GoogleIdentityApi;
  }
}

const scriptId = "meetingmind-google-identity";
let scriptRequest: Promise<GoogleIdentityApi> | null = null;
let initializedClientId: string | null = null;
let activeCredentialCallback: ((response: GoogleCredentialResponse) => void) | null = null;

export function GoogleCredentialButton({
  clientId,
  disabled = false,
  onCredential,
  onError
}: {
  clientId: string;
  disabled?: boolean;
  onCredential: (credential: string) => void;
  onError: (message: string) => void;
}) {
  const buttonRef = useRef<HTMLDivElement | null>(null);
  const disabledRef = useRef(disabled);
  const onCredentialRef = useRef(onCredential);
  const onErrorRef = useRef(onError);
  const [google, setGoogle] = useState<GoogleIdentityApi | null>(window.google ?? null);

  disabledRef.current = disabled;
  onCredentialRef.current = onCredential;
  onErrorRef.current = onError;

  useEffect(() => {
    let active = true;
    void loadGoogleIdentity()
      .then((api) => {
        if (active) {
          setGoogle(api);
        }
      })
      .catch(() => {
        if (active) {
          onError("Unable to load the Google sign-in script.");
        }
      });
    return () => {
      active = false;
    };
  }, [onError]);

  useEffect(() => {
    const buttonElement = buttonRef.current;
    if (!google || !buttonElement) {
      return;
    }
    buttonElement.innerHTML = "";
    const handleCredential = (response: GoogleCredentialResponse) => {
      if (disabledRef.current) {
        return;
      }
      if (!response.credential) {
        onErrorRef.current("Unable to verify the Google credential response.");
        return;
      }
      onCredentialRef.current(response.credential);
    };
    activeCredentialCallback = handleCredential;
    if (initializedClientId !== clientId) {
      google.accounts.id.initialize({
        client_id: clientId,
        callback: (response) => activeCredentialCallback?.(response)
      });
      initializedClientId = clientId;
    }
    google.accounts.id.renderButton(buttonElement, {
      theme: "outline",
      size: "large",
      shape: "pill",
      text: "continue_with",
      width: 320
    });
    return () => {
      if (activeCredentialCallback === handleCredential) {
        activeCredentialCallback = null;
      }
      buttonElement.innerHTML = "";
    };
  }, [clientId, google]);

  return (
    <div
      aria-disabled={disabled}
      className={`google-credential-button${disabled ? " disabled" : ""}`}
      ref={buttonRef}
    />
  );
}

function loadGoogleIdentity(): Promise<GoogleIdentityApi> {
  if (window.google) {
    return Promise.resolve(window.google);
  }
  if (scriptRequest) {
    return scriptRequest;
  }
  scriptRequest = new Promise((resolve, reject) => {
    const existing = document.getElementById(scriptId) as HTMLScriptElement | null;
    const script = existing ?? document.createElement("script");
    const loaded = () => {
      if (window.google) {
        resolve(window.google);
      } else {
        scriptRequest = null;
        reject(new Error("Google Identity API unavailable"));
      }
    };
    const failed = () => {
      scriptRequest = null;
      reject(new Error("Google Identity script failed"));
    };
    script.addEventListener("load", loaded, { once: true });
    script.addEventListener("error", failed, { once: true });
    if (!existing) {
      script.id = scriptId;
      script.src = "https://accounts.google.com/gsi/client";
      script.async = true;
      script.defer = true;
      document.head.appendChild(script);
    }
  });
  return scriptRequest;
}
