import { useEffect, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, get } from "../../api/client";
import { getSessionVersion } from "../../api/session";
import { OperatorLogo, type VehiclePhoto } from "../customer/CustomerMedia";
import { ErrorState } from "../../components/ui";
import "./operator-media.css";

const categories={INTERIOR:"Nội thất",EXTERIOR:"Ngoại thất",SEAT:"Ghế và khu vực bên trong",OTHER:"Ảnh khác"};
type Category=keyof typeof categories;
const accept="image/jpeg,image/png,image/webp";
function useObjectUrl(blob?:Blob) {
  const [url,setUrl]=useState("");
  useEffect(()=>{
    if(!blob) { setUrl(""); return; }
    const value=URL.createObjectURL(blob);
    setUrl(value);
    return ()=>URL.revokeObjectURL(value);
  },[blob]);
  return url;
}
function useSave() {
  const cache=useQueryClient();
  return useMutation({mutationFn:(action:()=>Promise<unknown>)=>action(),onSuccess:async()=>{
    await Promise.all(["marketplace","operator-gallery","operator-media"].map(key=>cache.invalidateQueries({queryKey:[key]})));
  }});
}
function usePreview(path:string, version:string, enabled=true) {
  const result=useQuery({queryKey:["operator-preview",getSessionVersion(),path,version], enabled,
    queryFn:async({signal})=>(await apiClient.get<Blob>(path,{responseType:"blob",signal})).data,staleTime:Infinity});
  return {result,src:useObjectUrl(result.data)};
}
function Upload({url,logo=false,count=0}:{url:string;logo?:boolean;count?:number}) {
  const [files,setFiles]=useState<File[]>([]), [error,setError]=useState(""), [category,setCategory]=useState<Category>("INTERIOR"), [alt,setAlt]=useState("");
  const input=useRef<HTMLInputElement>(null), save=useSave();
  function choose(selected:File[]) {
    save.reset();
    const invalid=selected.some(f=>!accept.split(",").includes(f.type) || !f.size || f.size>5*1024*1024);
    const message=invalid ? "Chọn ảnh PNG, JPEG hoặc WebP tối đa 5 MiB." : !logo && count+selected.length>12 ? "Mỗi loại xe có tối đa 12 ảnh." : "";
    setError(message); setFiles(message ? [] : selected);
  }
  async function upload() {
    const data=new FormData();
    files.forEach(file=>data.append(logo ? "file" : "files",file));
    if(!logo) { data.append("category",category); data.append("alt",alt); }
    await apiClient.post(url,data,{timeout:120000});
    setFiles([]); setAlt("");
    if(input.current) input.current.value="";
  }
  return <form className="form-stack media-upload" onSubmit={e=>{e.preventDefault();save.mutate(upload);}}>
    <fieldset disabled={save.isPending}><label className="field">{logo ? "Chọn logo mới" : "Chọn ảnh xe"}<input ref={input} type="file" accept={accept} multiple={!logo} onChange={e=>choose(Array.from(e.target.files||[]))}/></label>
    <small>PNG, JPEG hoặc WebP · Tối đa 5 MiB mỗi ảnh{!logo && " · 12 ảnh mỗi loại xe"}</small>
    {files.length>0 && <div className="media-previews">{files.map((file,i)=><FilePreview key={i} file={file}/>)}</div>}
    {!logo && <><CategoryField value={category} change={setCategory}/><label className="field">Mô tả ảnh<input maxLength={180} value={alt} onChange={e=>setAlt(e.target.value)}/></label></>}
    <button disabled={!files.length}>{save.isPending ? "Đang tải ảnh…" : save.isError ? "Thử tải lại" : logo ? "Lưu logo" : "Tải ảnh lên"}</button></fieldset>
    {error && <p role="alert">{error}</p>}{save.isError && <ErrorState error={save.error}/>}
    {save.isSuccess && <p role="status">Đã lưu hình ảnh.</p>}
  </form>;
}
function FilePreview({file}:{file:File}) {
  const url=useObjectUrl(file);
  return url ? <img src={url} alt={`Xem trước ${file.name}`} width={120} height={90}/> : null;
}
function CategoryField({value,change}:{value:string;change:(v:Category)=>void}) {
  return <label className="field">Phân loại<select value={value} onChange={e=>change(e.target.value as Category)}>
    {Object.entries(categories).map(([key,name])=><option key={key} value={key}>{name}</option>)}</select></label>;
}
export function LogoEditor({profile}:{profile:{name:string;logoUrl:string|null}}) {
  const save=useSave(), url="/operator/marketplace-profile/logo";
  const managed=!!profile.logoUrl?.startsWith("/api/v1/public/operators/");
  const {src,result}=usePreview(url,profile.logoUrl || "",managed);
  return <section className="operator-media"><h2>Logo nhà xe</h2>
    {managed && src ? <span className="operator-logo"><img src={src} alt={`Logo ${profile.name}`} width={72} height={72}/></span> : <OperatorLogo {...profile}/>}
    {result.isError && <ErrorState error={result.error} retry={()=>result.refetch()}/>}
    <Upload url={url} logo/>
    {profile.logoUrl && <button className="secondary" disabled={save.isPending} onClick={()=>{if(window.confirm("Xóa logo hiện tại?")) save.mutate(()=>apiClient.delete(url));}}>Xóa logo</button>}
    {save.isError && <ErrorState error={save.error}/>} {save.isSuccess && <p role="status">Đã xóa logo.</p>}
  </section>;
}
export function VehicleMediaEditor({type}:{type:number}) {
  const base=`/operator/bus-types/${type}/media`;
  const list=useQuery({queryKey:["operator-media",getSessionVersion(),type],queryFn:({signal})=>get<VehiclePhoto[]>(base,undefined,signal),retry:false});
  const save=useSave(), remove=useSave();
  function reorder(index:number,step:number) {
    const ids=list.data!.map(p=>p.id);
    [ids[index],ids[index+step]]=[ids[index+step],ids[index]];
    save.mutate(()=>apiClient.put(base+"/order",{ids}));
  }
  return <section className="operator-media"><h2>Hình ảnh loại xe</h2>
    {list.isPending ? <p role="status">Đang tải hình ảnh…</p> : list.isError ? <ErrorState error={list.error} retry={()=>list.refetch()}/> : <>
    <Upload url={base} count={list.data.length}/>
    {!list.data.length && <p>Nhà xe chưa thêm hình ảnh cho loại xe này.</p>}
    <div className="media-grid">{list.data.map((photo,index)=><PhotoEditor key={photo.id} photo={photo} base={base}
      first={index===0} last={index===list.data.length-1} busy={save.isPending || remove.isPending} move={step=>reorder(index,step)}
      remove={()=>remove.mutate(()=>apiClient.delete(base+"/"+photo.id))}/>)}</div></>}
    {save.isError && <ErrorState error={save.error}/>} {save.isSuccess && <p role="status">Đã cập nhật thứ tự.</p>}
    {remove.isError && <ErrorState error={remove.error}/>} {remove.isSuccess && <p role="status">Đã xóa ảnh.</p>}
  </section>;
}
function PhotoEditor({photo,base,first,last,busy,move,remove}:{photo:VehiclePhoto;base:string;first:boolean;last:boolean;busy:boolean;move:(step:number)=>void;remove:()=>void}) {
  const [alt,setAlt]=useState(photo.alt), [category,setCategory]=useState(photo.category), save=useSave();
  const dialog=useRef<HTMLDialogElement>(null);
  const path=base+"/"+photo.id;
  const {result:image,src}=usePreview(path,photo.id);
  const update=(featured:boolean)=>save.mutate(()=>apiClient.patch(path,{alt,category,featured}));
  return <article><fieldset disabled={busy || save.isPending}>
    {image.isError ? <ErrorState error={image.error} retry={()=>image.refetch()}/> : <button type="button" disabled={!src} className="media-preview-button" onClick={()=>dialog.current?.showModal()}>{src && <img src={src} alt={photo.alt || "Ảnh xe"} width={240} height={150}/>}Xem ảnh lớn</button>}
    <dialog className="vehicle-image-modal" ref={dialog} aria-label="Xem ảnh lớn"><button onClick={()=>dialog.current?.close()}>Đóng ảnh</button>{src && <img src={src} alt={photo.alt || "Ảnh xe"}/>}</dialog>
    <CategoryField value={category} change={setCategory}/><label className="field">Mô tả ảnh<input maxLength={180} value={alt} onChange={e=>setAlt(e.target.value)}/></label>
    <div className="media-actions"><button onClick={()=>update(false)}>Lưu thay đổi</button><button className="secondary" disabled={photo.featured} onClick={()=>update(true)}>{photo.featured ? "Ảnh đại diện" : "Đặt làm đại diện"}</button>
    <button className="secondary" disabled={first} onClick={()=>move(-1)}>Đưa lên</button><button className="secondary" disabled={last} onClick={()=>move(1)}>Đưa xuống</button>
    <button className="secondary" onClick={()=>{if(window.confirm("Xóa ảnh xe này?")) remove();}}>Xóa ảnh</button></div>
    </fieldset>{save.isError && <ErrorState error={save.error}/>} {save.isSuccess && <p role="status">Đã cập nhật ảnh.</p>}</article>;
}
