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
import type { Trip } from "../types/customer";
import {
  Empty,
  ErrorState,
  Field,
  Loading,
  Pagination,
  TripCard,
} from "../components/ui";
import { date, positiveId } from "../utils/format";
const filterSchema = z
  .object({
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
    "departureTo", "operatorId", "busTypeId",
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
  const form = useForm<z.infer<typeof filterSchema>>({
    resolver: zodResolver(filterSchema),
    values: {
      minPrice: params.get("minPrice") || "",
      maxPrice: params.get("maxPrice") || "",
      departureFrom: params.get("departureFrom") || "",
      departureTo: params.get("departureTo") || "",
    },
  });
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
      <details className="search-editor" open><summary>Chỉnh sửa hành trình</summary><SearchForm /></details>
      <div className="results-layout">
        <FilterPanel
          activeCount={activeFilters}
          onCloseReady={(close) => {
            closeFilters.current = close;
          }}
        >
          <form className="form-stack" onSubmit={form.handleSubmit(update)}>
            <fieldset className="filter-group">
              <legend>Khoảng giá (đ)</legend>
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
            <button>Áp dụng bộ lọc</button>
            <button
              type="button"
              className="text-button"
              onClick={() =>
                update({
                  minPrice: "",
                  maxPrice: "",
                  departureFrom: "",
                  departureTo: "",
                  operatorId: "",
                  busTypeId: "",
                })
              }
            >
              Xóa bộ lọc
            </button>
          </form>
        </FilterPanel>
        <section className="results">
          <div className="results-toolbar">
            <span>
              {result.data
                ? `${result.data!.pagination.totalElements} chuyến phù hợp`
                : "Đang tìm chuyến…"}
            </span>
            <label>
              Sắp xếp{" "}
              <select
                value={params.get("sort") || "DEPARTURE_ASC"}
                onChange={(event) => update({ sort: event.target.value })}
              >
                <option value="DEPARTURE_ASC">Giờ đi sớm nhất</option>
                <option value="DEPARTURE_DESC">Giờ đi muộn nhất</option>
                <option value="PRICE_ASC">Giá thấp nhất</option>
                <option value="PRICE_DESC">Giá cao nhất</option>
              </select>
            </label>
          </div>
          {activeFilters > 0 && <div className="filter-chips"><span>{activeFilters} điều kiện lọc</span><button className="text-button" onClick={() => update({ minPrice: "", maxPrice: "", departureFrom: "", departureTo: "", operatorId: "", busTypeId: "" })}>Xóa bộ lọc</button></div>}
          <RefreshNotice query={result} />
          {result.isPending ? (
            <Loading />
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
