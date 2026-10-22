import { useEffect, useRef, type ReactNode } from "react";
import { useLocation } from "react-router-dom";

/** Animate the existing DOM, without keying/remounting the outlet or changing scroll. */
export function PageContent({ children }: { children: ReactNode }) {
  const ref = useRef<HTMLDivElement>(null);
  const { pathname } = useLocation();
  useEffect(() => {
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    const element = ref.current;
    const duration = Number.parseFloat(getComputedStyle(document.documentElement).getPropertyValue("--motion-content"));
    const animation = element?.animate([{ opacity: 0.6, transform: "translateY(4px)" }, { opacity: 1, transform: "translateY(0)" }], {
      duration, easing: "cubic-bezier(.2,.7,.25,1)",
    });
    const preference = window.matchMedia("(prefers-reduced-motion: reduce)");
    const stop = () => { if (preference.matches) animation?.cancel(); };
    preference.addEventListener("change", stop);
    return () => { animation?.cancel(); preference.removeEventListener("change", stop); };
  }, [pathname]);
  return <div ref={ref} className="app-page-content">{children}</div>;
}
