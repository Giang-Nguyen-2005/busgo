import { useEffect, useState } from "react";
import { useNavigation } from "react-router-dom";

export function RouteProgress() {
  const navigation = useNavigation();
  const pending = navigation.state !== "idle";
  const [visible, setVisible] = useState(false);
  useEffect(() => {
    if (!pending) { setVisible(false); return; }
    // Delay only the indicator, never the router or page rendering.
    const timer = window.setTimeout(() => setVisible(true), 180);
    return () => window.clearTimeout(timer);
  }, [pending]);
  return pending && visible ? <div className="route-progress" role="status">Đang mở trang…</div> : null;
}
