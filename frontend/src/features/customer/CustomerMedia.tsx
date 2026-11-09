import { photoDescription } from "./displayCopy";
import "./customer-media.css";
import { useEffect, useId, useRef, useState } from "react";
import { BusFront, MapPin, Image } from "lucide-react";
import { demoOperatorLogo, heroAsset, interiorForBusType, safeImageUrl, type DestinationAsset } from "./visualAssets";

const demoMedia = import.meta.env?.VITE_DEMO_MEDIA === "true" ||
  (import.meta.env?.VITE_DEMO_MEDIA !== "false" && !import.meta.env?.PROD);

// A keyed child resets failure state when the API supplies a different URL.
export function AssetImage({ src, fallbackSrc, alt, className, eager = false, width, height }: { src?: string | null; fallbackSrc?: string; alt: string; className?: string; eager?: boolean; width: number; height: number }) {
  const primary = safeImageUrl(src), fallback = safeImageUrl(fallbackSrc);
  return <ImageAttempt key={JSON.stringify([primary, fallback])} primary={primary} fallback={fallback} alt={alt} className={className} eager={eager} width={width} height={height} />;
}
function ImageAttempt({ primary, fallback, alt, className, eager, width, height }: { primary?: string; fallback?: string; alt: string; className?: string; eager: boolean; width: number; height: number }) {
  const [failed, setFailed] = useState<string[]>([]);
  const src = [primary, fallback].find(url => url && !failed.includes(url));
  return src ? <img className={className} src={src} alt={alt} width={width} height={height} loading={eager ? "eager" : "lazy"} fetchPriority={eager ? "high" : undefined} decoding="async" onError={() => setFailed(previous => [...previous, src])} /> : null;
}
export function OperatorLogo({ name, logoUrl }: { name: string; logoUrl?: string | null }) {
  const demo = demoMedia && !safeImageUrl(logoUrl) ? demoOperatorLogo(name) : undefined;
  return <span className="operator-logo"><BusFront aria-hidden="true" size={28} /><AssetImage src={safeImageUrl(logoUrl) || demo} alt={`Logo ${name}`} width={72} height={72} /></span>;
}
export function DestinationImage({ asset }: { asset?: DestinationAsset }) {
  return <span className="destination-image" aria-hidden="true"><MapPin size={26} /><AssetImage src={asset?.imageUrl} alt="" width={1200} height={900} /></span>;
}
export function HeroArt() {
  return <div className="hero-art" aria-hidden="true" style={{ "--hero-position": heroAsset.desktopPosition, "--hero-mobile-position": heroAsset.mobilePosition } as React.CSSProperties}><AssetImage src={heroAsset.imageUrl} fallbackSrc={heroAsset.fallbackUrl} alt="" width={1920} height={960} eager /><div className="hero-image-shade" /></div>;
}

export interface VehiclePhoto {
  id: string; busTypeId: number; url: string; alt: string; featured: boolean; category: string;
}
export function VehicleImage({ busType, detail = false, compact = false, photos = [] }: {
  busType: { name: string }; detail?: boolean; compact?: boolean; photos?: VehiclePhoto[];
}) {
  const demo = demoMedia ? interiorForBusType(busType) : undefined;
  return <VehicleImageView key={JSON.stringify([busType.name, photos.map(p=>p.url), demo])} demo={demo} photos={photos} name={busType.name} detail={detail} compact={compact} />;
}
function VehicleImageView({ demo, photos, name, detail, compact }: { demo?: string; photos: VehiclePhoto[]; name: string; detail: boolean; compact: boolean }) {
  const [failed,setFailed]=useState<string[]>([]), [open,setOpen]=useState(false), [index,setIndex]=useState(0);
  const dialog=useRef<HTMLDialogElement>(null), trigger=useRef<HTMLButtonElement>(null);
  const titleId=useId();
  const usable=photos.filter(p=>safeImageUrl(p.url) && !failed.includes(p.url));
  const cover=usable.find(p=>p.featured) ?? usable[0];
  const current=open ? usable[index % (usable.length || 1)] : cover;
  const src=current?.url || (demo && !failed.includes(demo) ? demo : undefined);
  const hasImage=!!src;
  const illustrative = !current || current.alt.startsWith("[DEMO M31]");
  const caption=illustrative ? "Ảnh minh họa loại xe" : "Ảnh do nhà xe cung cấp";
  const label=illustrative ? `Xem ảnh minh họa ${name}` : `Xem ảnh ${name}`;
  const alt=current ? photoDescription(current.alt, name) : `Nội thất minh họa ${name}`;
  function move(direction:number) { setIndex(i=>(i+direction+usable.length)%usable.length); }
  function broken() { if(src) setFailed(prev=>[...prev,src]); }
  useEffect(()=>{
    if (!open || !hasImage || !dialog.current) return;
    const modal=dialog.current, overflow=document.body.style.overflow;
    modal.showModal(); document.body.style.overflow="hidden";
    return ()=>{ modal.close(); document.body.style.overflow=overflow; trigger.current?.focus({preventScroll:true}); };
  },[open,hasImage]);
  function show() { setIndex(Math.max(0,usable.findIndex(p=>p.featured))); setOpen(true); }
  const image=src ? <img src={src} width={1200} height={900} alt={alt} loading="lazy" onError={broken} /> : <BusFront size={32} aria-hidden="true" />;
  return <div className={`vehicle-media${compact ? " vehicle-media-compact" : detail ? " vehicle-media-detail" : ""}`}>
    {compact ? (src ? <button ref={trigger} className="secondary vehicle-media-trigger" type="button" aria-haspopup="dialog" aria-label={label} onClick={show}><Image size={16} aria-hidden="true" />{illustrative ? "Xem ảnh minh họa loại xe" : "Xem ảnh nhà xe"}</button> : <small className="muted">Chưa có ảnh minh họa</small>) : src ? <button ref={trigger} className="vehicle-image-button" type="button" aria-label={label} onClick={show}>{image}<span>Xem ảnh</span></button> : <div className="vehicle-image-placeholder">{image}<span>Chưa có ảnh nội thất phù hợp</span></div>}
    {!compact && src && <small className="vehicle-image-caption">{caption}</small>}
    {!compact && detail && illustrative && <p className="fine-print">Hình ảnh minh họa bố trí cơ bản, không xác nhận tiện nghi VIP hoặc trang bị của từng xe.</p>}
    {src && <dialog ref={dialog} className="vehicle-image-modal" aria-labelledby={titleId} onKeyDown={e=>{
      if (usable.length>1 && e.key==="ArrowLeft") move(-1);
      if (usable.length>1 && e.key==="ArrowRight") move(1);
      if (e.key==="Tab") {
        const buttons=e.currentTarget.querySelectorAll("button");
        const first=buttons[0], last=buttons[buttons.length-1];
        if (e.shiftKey && document.activeElement===first) { e.preventDefault(); last.focus(); }
        else if (!e.shiftKey && document.activeElement===last) { e.preventDefault(); first.focus(); }
      }
    }} onCancel={e=>{ e.preventDefault(); setOpen(false); }}>
      <div className="split"><h2 id={titleId}>{caption}</h2><button type="button" autoFocus onClick={()=>setOpen(false)}>Đóng ảnh</button></div>
      {open && <img src={src} width={1200} height={900} alt={alt} onError={broken} />}
      <p aria-live="polite">{current ? `${name} · ${alt} · ${index%usable.length+1}/${usable.length}` : `${name} · Ảnh minh họa bố trí cơ bản, không xác nhận tiện nghi VIP hoặc trang bị của từng xe.`}</p>
      {usable.length>1 && <div className="market-actions"><button type="button" onClick={()=>move(-1)}>Ảnh trước</button><button type="button" onClick={()=>move(1)}>Ảnh sau</button></div>}
    </dialog>}
  </div>;
}
