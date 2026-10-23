import { LoadingSkeleton } from "../components/foundations/Loading";
import { X } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { useRef } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { SearchForm } from "../features/search/SearchForm";
import { readJourney, journeyTitle } from "../features/customer/presentation";
import { blockingQueryError, RefreshNotice } from "../features/customer/QueryFeedback";
import { FilterPanel } from "../features/search/FilterPanel";
import { apiClient } from "../api/client";
import type { PagedResponse } from "../types/api";
import type { PublicOperator } from "../features/marketplace/Marketplace";
import { get } from "../api/client";
import type { Named } from "../types/customer";
import type { Trip } from "../types/customer";
import {
  Empty,
  ErrorState,
  Field,
  Pagination,
  TripCard,
} from "../components/ui";
import { date, positiveId } from "../utils/format";
const filterSchema = z
  .object({
    operatorId: z.string(), busTypeId: z.string(), minRating: z.string(), minSeats: z.string(),
    minPrice: z.string(),
    maxPrice: z.string(),
    departureFrom: z.string(),
    departureTo: z.string(),
  })
  .refine(
    (v) =>
      !v.minPrice || !v.maxPrice || Number(v.minPrice) <= Number(v.maxPrice),
    {
      path: ["maxPrice"],
      message: "Giá tối đa phải lớn hơn hoặc bằng giá tối thiểu.",
    },
  )
  .refine(
    (v) =>
      !v.departureFrom || !v.departureTo || v.departureFrom <= v.departureTo,
    { path: ["departureTo"], message: "Giờ kết thúc phải sau giờ bắt đầu." },
  );
export function SearchPage() {
  const [params, setParams] = useSearchParams();
  const closeFilters = useRef<() => void>(() => {});
  const activeFilters = [
    "minPrice",
    "maxPrice",
    "departureFrom",
    "departureTo", "operatorId", "busTypeId", "minRating", "minSeats",
  ].filter((key) => params.get(key)).length;
  const valid =
    positiveId(params.get("pickupLocationId")) &&
    positiveId(params.get("dropoffLocationId")) &&
    params.get("pickupLocationId") !== params.get("dropoffLocationId") &&
    /^\d{4}-\d{2}-\d{2}$/.test(params.get("departureDate") || "");
  const allowed = [
    "pickupLocationId",
    "dropoffLocationId",
    "departureDate",
    "operatorId",
    "busTypeId",
    "minPrice",
    "maxPrice",
    "departureFrom",
    "departureTo",
    "minRating", "minSeats",
    "sort",
    "page",
  ];
  const request = Object.fromEntries(
    [...params.entries()].filter(([key]) => allowed.includes(key)),
  );
  const result = useQuery({
    queryKey: ["search", request],
    queryFn: async ({ signal }) =>
      (
        await apiClient.get<PagedResponse<Trip>>("/trips/search", {
          params: { ...request, size: 10 },
          signal,
        })
      ).data,
    enabled: valid,
  });
  const operators = useQuery({ queryKey: ["marketplace", "search-operators"], queryFn: async ({signal}) => (await apiClient.get<PagedResponse<PublicOperator>>("/public/operators", {params:{size:100},signal})).data });
  const types = useQuery({ queryKey: ["marketplace", "search-types"], queryFn: ({signal}) => get<Named[]>("/public/discovery/bus-types",undefined,signal) });
  const appliedValues = {
      operatorId: params.get("operatorId") || "", busTypeId: params.get("busTypeId") || "", minRating: params.get("minRating") || "", minSeats: params.get("minSeats") || "",
      minPrice: params.get("minPrice") || "",
      maxPrice: params.get("maxPrice") || "",
      departureFrom: params.get("departureFrom") || "",
      departureTo: params.get("departureTo") || "",
    };
  const form = useForm<z.infer<typeof filterSchema>>({ resolver: zodResolver(filterSchema), values: appliedValues });
  const emptyFilters = { minPrice: "", maxPrice: "", departureFrom: "", departureTo: "", operatorId: "", busTypeId: "", minRating: "", minSeats: "" };
  const filterLabels: Record<string, string> = { minPrice: "Giá từ", maxPrice: "Giá đến", departureFrom: "Khởi hành từ", departureTo: "Khởi hành đến", operatorId: "Nhà xe", busTypeId: "Loại xe", minRating: "Sao tối thiểu", minSeats: "Chỗ trống tối thiểu" };
  const filterValue = (key: string, value: string) => key === "operatorId" ? operators.data?.data.find(o => String(o.id) === value)?.name || value : key === "busTypeId" ? types.data?.find(t => String(t.id) === value)?.name || value : value;
  const update = (values: Record<string, string>) => {
    const next = new URLSearchParams(params);
    next.delete("page");
    Object.entries(values).forEach(([key, value]) =>
      value ? next.set(key, value) : next.delete(key),
    );
    setParams(next);
    closeFilters.current();
  };
  if (!valid)
    return (
      <Empty title="Bạn muốn đi đâu?">
        <p>Chọn điểm đón, điểm trả và ngày đi để tìm chuyến.</p><SearchForm />
        <Link className="button" to="/">
          Tìm chuyến xe
        </Link>
      </Empty>
    );
  return (
    <>
      <div className="page-heading">
        <div>
          <span className="eyebrow">CHỌN CHUYẾN PHÙ HỢP</span>
          <h1>
            {journeyTitle(readJourney(params))}
          </h1>
          <p className="muted">
            Ngày đi {date(params.get("departureDate")!)} · Giờ Việt Nam
          </p>
        </div>

      </div>
      <details className="search-editor"><summary>Chỉnh sửa hành trình</summary><SearchForm /></details>
      <div className="results-layout">
        <FilterPanel
          activeCount={activeFilters}
          onOpen={() => form.reset(appliedValues)}
          onCloseReady={(close) => {
            closeFilters.current = close;
          }}
        >
          <form className="form-stack filter-form" onSubmit={form.handleSubmit(update)}>
            <div className="filter-fields">
            <label className="field">Nhà xe<select aria-label="Nhà xe" {...form.register("operatorId")} value={form.watch("operatorId")}><option value="">Tất cả nhà xe</option>{operators.data?.data.map(op=><option value={op.id} key={op.id}>{op.name}</option>)}</select></label>
            {operators.isError && <ErrorState error={operators.error} retry={()=>operators.refetch()} />}
            <label className="field">Loại xe<select aria-label="Loại xe" {...form.register("busTypeId")} value={form.watch("busTypeId")}><option value="">Tất cả loại xe</option>{types.data?.map(t=><option value={t.id} key={t.id}>{t.name}</option>)}</select></label>
            {types.isError && <ErrorState error={types.error} retry={()=>types.refetch()} />}
            <label className="field">Đánh giá tối thiểu<select aria-label="Đánh giá tối thiểu" {...form.register("minRating")} value={form.watch("minRating")}><option value="">Không giới hạn</option>{[4,3,2,1].map(v=><option key={v} value={v}>{v} sao trở lên</option>)}</select></label>
            <Field label="Số chỗ trống tối thiểu" type="number" min="1" max="100" step="1" {...form.register("minSeats")} />
            <fieldset className="filter-group"><legend>Khoảng giá (đ)</legend>
              <Field
                label="Giá tối thiểu"
                type="number"
                min="0"
                step="1"
                {...form.register("minPrice")}
              />
              <Field
                label="Giá tối đa"
                type="number"
                min="0"
                step="1"
                {...form.register("maxPrice")}
                error={form.formState.errors.maxPrice?.message}
              />
            </fieldset>
            <fieldset className="filter-group">
              <legend>Giờ khởi hành</legend>
              <Field
                label="Từ"
                type="time"
                {...form.register("departureFrom")}
              />
              <Field
                label="Đến"
                type="time"
                {...form.register("departureTo")}
                error={form.formState.errors.departureTo?.message}
              />
            </fieldset>
            </div>
            <div className="filter-actions"><button>Áp dụng bộ lọc</button><button type="button" className="secondary" onClick={() => form.reset(emptyFilters)}>Đặt lại</button></div>
          </form>
        </FilterPanel>
        <section className="results">
          <div className="results-toolbar">
            <span>
              {result.data
                ? `${result.data!.pagination.totalElements} chuyến phù hợp`
                : result.isError ? "Chưa tải được kết quả" : "Đang tìm chuyến…"}
            </span>
            <label>
              Sắp xếp{" "}
              <select
                value={params.get("sort") || "RECOMMENDED"}
                onChange={(event) => update({ sort: event.target.value })}
              >
                <option value="RECOMMENDED">Gợi ý phù hợp</option>
                <option value="RATING_DESC">Đánh giá cao nhất</option>
                <option value="DEPARTURE_ASC">Giờ đi sớm nhất</option>
                <option value="DEPARTURE_DESC">Giờ đi muộn nhất</option>
                <option value="PRICE_ASC">Giá thấp nhất</option>
                <option value="PRICE_DESC">Giá cao nhất</option>
              </select>
            </label>
          </div>
          {activeFilters > 0 && <div className="filter-chips" aria-label="Bộ lọc đang áp dụng">{Object.entries(appliedValues).filter(([, value]) => value).map(([key, value]) => <button key={key} className="filter-chip" onClick={() => update({ [key]: "" })} aria-label={`Bỏ ${filterLabels[key]}: ${filterValue(key, value)}`}>{filterLabels[key]}: {filterValue(key, value)}<X size={14} aria-hidden="true" /></button>)}<button className="text-button" onClick={() => update(emptyFilters)}>Xóa tất cả ({activeFilters})</button></div>}

          {result.data !== undefined && !blockingQueryError(result) && <RefreshNotice query={result} />}
          {result.isPending ? (
            <LoadingSkeleton label="Đang tìm chuyến phù hợp…" />
          ) : blockingQueryError(result) ? (
            <ErrorState error={result.error} retry={() => result.refetch()} />
          ) : (
            <>
              {result.data!.data.length ? (
                result.data!.data.map((trip) => (
                  <TripCard key={trip.tripId} trip={trip} searchContext={`/search?${params}`} />
                ))
              ) : (
                <Empty title={activeFilters ? "Không có chuyến khớp bộ lọc" : "Chưa có chuyến cho hành trình và ngày này"}>
                  {activeFilters > 0 && <button className="secondary" onClick={() => update(emptyFilters)}>Xóa bộ lọc</button>}
                  <p>{activeFilters ? "Xóa bộ lọc để xem lại kết quả cho hành trình đã chọn." : "Thử ngày đi khác hoặc chỉnh sửa hành trình phía trên."}</p>
                </Empty>
              )}
              <Pagination
                pagination={result.data!.pagination}
                onPage={(page) => {
                  const next = new URLSearchParams(params);
                  next.set("page", String(page));
                  setParams(next);
                }}
              />
            </>
          )}
        </section>
      </div>
    </>
  );
}
