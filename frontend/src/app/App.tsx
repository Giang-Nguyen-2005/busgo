import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { RouterProvider } from "react-router-dom";
import { router } from "../routes/router";
import { AuthProvider } from "../features/auth/AuthProvider";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, staleTime: 15_000 },
    mutations: { retry: false },
  },
});

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <RouterProvider router={router} />
      </AuthProvider>
    </QueryClientProvider>
  );
}
