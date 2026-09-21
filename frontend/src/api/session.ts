import type { Tokens } from "../types/customer";

// Tab-scoped persistence. Never serialize tokens into URLs, query keys or logs.
const key = "busgo.auth";
let sessionVersion = 0;
export const getSessionVersion = () => sessionVersion;
export function getTokens(): Tokens | null {
  try {
    const value = JSON.parse(sessionStorage.getItem(key) || "null");
    return value?.accessToken && value?.refreshToken ? value : null;
  } catch {
    return null;
  }
}
export function setTokens(tokens: Tokens | null) {
  if (tokens)
    sessionStorage.setItem(
      key,
      JSON.stringify({
        accessToken: tokens.accessToken,
        refreshToken: tokens.refreshToken,
        expiresIn: tokens.expiresIn,
      }),
    );
  else {
    sessionVersion += 1;
    sessionStorage.removeItem(key);
    sessionStorage.removeItem("busgo.hold");
  }
  window.dispatchEvent(new Event("busgo:auth"));
}
