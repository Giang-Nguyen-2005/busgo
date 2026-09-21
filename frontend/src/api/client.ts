import axios from "axios";
import { getTokens, setTokens, getSessionVersion } from "./session";
import type { Tokens } from "../types/customer";
import type { ApiResponse } from "../types/api";

declare module "axios" {
  interface InternalAxiosRequestConfig {
    _retried?: boolean;
    _sessionVersion?: number;
  }
}

export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "/api/v1",
  timeout: 10_000,
  headers: { Accept: "application/json" },
});

let refreshing: Promise<Tokens> | null = null;
apiClient.interceptors.request.use((config) => {
  if (
    config._sessionVersion !== undefined &&
    config._sessionVersion !== getSessionVersion()
  ) {
    throw new axios.CanceledError("Session changed");
  }
  config._sessionVersion = getSessionVersion();
  const token = getTokens()?.accessToken;
  if (token && !config.url?.startsWith("/auth/"))
    config.headers.Authorization = `Bearer ${token}`;
  else config.headers.delete("Authorization");
  return config;
});
apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const config = error.config;
    const session = getTokens();
    if (config?._sessionVersion !== getSessionVersion()) throw error;
    if (
      error.response?.status !== 401 ||
      !config ||
      config.url?.startsWith("/auth/") ||
      !session
    )
      throw error;
    if (config._retried) {
      setTokens(null);
      throw error;
    }
    config._retried = true;
    // A parallel request may arrive after another request has already rotated tokens.
    if (config.headers.Authorization !== `Bearer ${session.accessToken}`)
      return apiClient(config);
    if (!refreshing) {
      const previous = session.refreshToken;
      refreshing = apiClient
        .post<ApiResponse<Tokens>>("/auth/refresh", { refreshToken: previous })
        .then(({ data }) => {
          if (getTokens()?.refreshToken !== previous)
            throw new Error("Session changed");
          setTokens(data.data);
          return data.data;
        })
        .catch((failure) => {
          if (getTokens()?.refreshToken === previous) setTokens(null);
          throw failure;
        })
        .finally(() => {
          refreshing = null;
        });
    }
    await refreshing;
    if (!getTokens()) throw error;
    return apiClient(config);
  },
);

export async function get<T>(
  path: string,
  params?: object,
  signal?: AbortSignal,
) {
  return (await apiClient.get<ApiResponse<T>>(path, { params, signal })).data
    .data;
}
export async function post<T>(path: string, body?: object) {
  return (await apiClient.post<ApiResponse<T>>(path, body)).data.data;
}
