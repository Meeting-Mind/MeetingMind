import { QueryClient } from "@tanstack/react-query";

/**
 * 앱 전역 QueryClient.
 * - server state는 TanStack Query가 관리한다 (AGENTS.md State Management Rules).
 * - BFF 세션 쿠키 기반 요청이므로 401/403은 재시도하지 않는다.
 */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      refetchOnWindowFocus: false,
      retry: (failureCount, error) => {
        const status = (error as { status?: number }).status;
        if (status === 401 || status === 403 || status === 404) {
          return false;
        }
        return failureCount < 2;
      }
    }
  }
});
