import { useEffect, useRef, useState, type KeyboardEvent } from "react";
import { Link } from "react-router-dom";
import { ArrowUpRight, ChevronLeft, ChevronRight } from "lucide-react";
import { DestinationImage } from "../customer/CustomerMedia";
import { destinationAssets, destinationForLocation } from "../customer/visualAssets";
import { routeSearch, routeLabel } from "../customer/presentation";
import { ErrorState } from "../../components/ui";
import { LoadingSkeleton } from "../../components/foundations/Loading";
import type { Named } from "../../types/customer";

type DiscoveryRoute = Named & { pickupLocationId: number; dropoffLocationId: number };
export function DestinationDiscovery({ routes = [], locations = [], pending, error, retry }: {
  routes?: DiscoveryRoute[]; locations?: Named[]; pending: boolean; error: unknown; retry: () => void;
}) {
  const track = useRef<HTMLDivElement>(null);
  const [edges, setEdges] = useState({ start: true, end: false });

  useEffect(() => {
    const element = track.current;
    if (!element) return;
    const measure = () => {
      const start = element.scrollLeft <= 1;
      const end = element.scrollLeft + element.clientWidth >= element.scrollWidth - 1;
      setEdges(previous => previous.start === start && previous.end === end ? previous : { start, end });
    };
    const observer = new ResizeObserver(measure);
    observer.observe(element);
    element.addEventListener("scroll", measure, { passive: true });
    measure();
    return () => {
      observer.disconnect();
      element.removeEventListener("scroll", measure);
    };
  }, []);

  function step(direction: -1 | 1) {
    const element = track.current;
    if (!element) return;
    const start = element.getBoundingClientRect().left;
    const offsets = Array.from(element.children, card => card.getBoundingClientRect().left - start);
    const delta = direction === 1 ? offsets.find(x => x > 1) : offsets.reverse().find(x => x < -1);
    if (delta !== undefined) element.scrollBy({ left: delta });
  }

  function navigate(event: KeyboardEvent<HTMLDivElement>) {
    if (event.target !== event.currentTarget || event.altKey || event.ctrlKey || event.metaKey) return;
    switch (event.key) {
      case "ArrowRight": step(1); break;
      case "ArrowLeft": step(-1); break;
      case "Home": event.currentTarget.scrollLeft = 0; break;
      case "End": event.currentTarget.scrollLeft = event.currentTarget.scrollWidth; break;
      default: return;
    }
    event.preventDefault();
  }

  return <section aria-labelledby="destinations-heading">
    <span className="eyebrow">MỘT ĐIỂM ĐẾN, NHIỀU ĐIỀU KHÁM PHÁ</span>
    <h2 id="destinations-heading">Điểm đến cho chuyến đi tới</h2>
    <p className="muted">Từ phố biển đến cao nguyên, khám phá điểm đến và chọn ngày khởi hành trên tuyến phù hợp.</p>
    {pending ? <LoadingSkeleton rows={2} /> : error ? <ErrorState error={error} retry={retry} /> : null}
    <div id="destination-track" ref={track} className="destination-track" role="region"
      aria-label="Danh sách điểm đến" tabIndex={0} onKeyDown={navigate}>{destinationAssets.map(asset => {
      const route = !pending && !error ? routes.find(r => destinationForLocation(r.dropoffLocationId, locations)?.key === asset.key) : undefined;
      return <article className="card destination-card" key={asset.key}>
        <DestinationImage asset={asset} />
        <div><h3>{asset.name}</h3>{route ? <><p>{routeLabel(route.name)}</p><Link to={routeSearch(route)}>Chọn ngày khởi hành <ArrowUpRight size={16} aria-hidden="true" /></Link></> : <p className="muted">{pending ? "Đang kiểm tra tuyến được giới thiệu…" : error ? "Chưa tải được thông tin tuyến." : "Điểm đến này chưa có tuyến được giới thiệu."}</p>}</div>
      </article>;
    })}</div>
    <div className="destination-controls">
      <button type="button" className="secondary" aria-label="Điểm đến trước" aria-controls="destination-track"
        disabled={edges.start} onClick={() => step(-1)}><ChevronLeft size={19} aria-hidden="true" /></button>
      <button type="button" className="secondary" aria-label="Điểm đến tiếp theo" aria-controls="destination-track"
        disabled={edges.end} onClick={() => step(1)}><ChevronRight size={19} aria-hidden="true" /></button>
    </div>
  </section>;
}
