import { useEffect, useRef, useState } from "react";

type GoogleCredentialResponse = {
  credential?: string;
};

declare global {
  interface Window {
    google?: {
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
  }
}

export function GoogleReauthenticationButton({
  disabled,
  onCredential,
  onError
}: {
  disabled: boolean;
  onCredential: (credential: string) => void;
  onError: (message: string) => void;
}) {
  const buttonRef = useRef<HTMLDivElement | null>(null);
  const credentialHandler = useRef(onCredential);
  const errorHandler = useRef(onError);
  const [scriptReady, setScriptReady] = useState(Boolean(window.google));
  const clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID?.trim();

  useEffect(() => {
    credentialHandler.current = onCredential;
    errorHandler.current = onError;
  }, [onCredential, onError]);

  useEffect(() => {
    if (window.google) {
      setScriptReady(true);
      return;
    }
    const script = document.createElement("script");
    script.src = "https://accounts.google.com/gsi/client";
    script.async = true;
    script.defer = true;
    script.onload = () => setScriptReady(true);
    script.onerror = () => errorHandler.current("Google 재인증 스크립트를 불러오지 못했습니다.");
    document.head.appendChild(script);
    return () => script.remove();
  }, []);

  useEffect(() => {
    if (!clientId || !scriptReady || !buttonRef.current || !window.google) {
      return;
    }
    buttonRef.current.innerHTML = "";
    window.google.accounts.id.initialize({
      client_id: clientId,
      callback: (response) => {
        if (!response.credential) {
          errorHandler.current("Google 재인증 정보를 확인하지 못했습니다.");
          return;
        }
        credentialHandler.current(response.credential);
      }
    });
    window.google.accounts.id.renderButton(buttonRef.current, {
      theme: "outline",
      size: "medium",
      shape: "rectangular",
      text: "continue_with",
      width: 230
    });
  }, [clientId, scriptReady]);

  if (!clientId || disabled) {
    return null;
  }
  return <div aria-label="Google 재인증" className="auth-google-reauth" ref={buttonRef} />;
}
