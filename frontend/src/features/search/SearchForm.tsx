import { useEffect, useId, useState } from "react";
import { useForm, Controller } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useQuery } from "@tanstack/react-query";
import { useNavigate, useSearchParams } from "react-router-dom";
import { ArrowLeftRight, MapPin, Search } from "lucide-react";
import { get } from "../../api/client";
import type { Location } from "../../types/customer";
import { today } from "../../utils/format";
import { ErrorState, Field } from "../../components/ui";

import { readJourney, journeyParams, swapJourney } from "../customer/presentation";
const schema = z
  .object({
    pickupLabel: z.string(),
    dropoffLabel: z.string(),
    pickupLocationId: z.number().positive("Chọn điểm đón từ danh sách."),
    dropoffLocationId: z.number().positive("Chọn điểm đến từ danh sách."),
    departureDate: z
      .string()
      .min(1, "Chọn ngày đi.")
      .refine((value) => value >= today(), "Chọn ngày hôm nay hoặc sau đó."),
  })
  .refine((value) => value.pickupLocationId !== value.dropoffLocationId, {
    path: ["dropoffLocationId"],
    message: "Điểm đến phải khác điểm đón.",
  });
function LocationInput({
  label,
  onChange,
  value,
  error,
}: {
  label: string;
  onChange: (id: number, label: string) => void;
  value: string;
  error?: string;
}) {
  const id = useId();
  const text = value;
  const [query, setQuery] = useState("");
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(0);
  useEffect(() => {
    const timer = setTimeout(() => {
      setQuery(text.trim());
      setActive(0);
    }, 250);
    return () => clearTimeout(timer);
  }, [text]);
  const locations = useQuery({
    queryKey: ["locations", query],
    queryFn: ({ signal }) =>
      get<Location[]>("/locations", { q: query }, signal),
    enabled: open,
    staleTime: 60_000,
  });
  const choose = (location: Location) => {
    onChange(location.id, location.name);
    setOpen(false);
  };
  return (
    <div
      className="autocomplete"
      onBlur={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget)) setOpen(false);
      }}
    >
      <label className="field" htmlFor={id}>
        <span>
          <MapPin size={15} />
          {label}
        </span>
        <input
          id={id}
          role="combobox"
          aria-expanded={open}
          aria-controls={`${id}-list`}
          aria-autocomplete="list"
          aria-invalid={!!error}
          aria-describedby={error ? `${id}-error` : undefined}
          aria-activedescendant={
            open && locations.data?.[active] ? `${id}-${active}` : undefined
          }
          autoComplete="off"
          placeholder="Tỉnh, thành phố, bến xe"
          value={text}
          maxLength={150}
          onFocus={() => setOpen(true)}
          onChange={(event) => {
            onChange(0, event.target.value);
            setOpen(true);
          }}
          onKeyDown={(event) => {
            if (event.key === "Escape") setOpen(false);
            if (event.key === "ArrowDown") {
              event.preventDefault();
              setOpen(true);
              setActive((i) =>
                Math.min(i + 1, (locations.data?.length || 1) - 1),
              );
            }
            if (event.key === "ArrowUp") {
              event.preventDefault();
              setActive((i) => Math.max(0, i - 1));
            }
            if (event.key === "Enter" && open) {
              event.preventDefault();
              const item = locations.data?.[active];
              if (item) choose(item);
            }
          }}
        />
        {error && (
          <small id={`${id}-error`} className="field-error">
            {error}
          </small>
        )}
      </label>
      {open && (
        <div
          className="suggestions"
          id={`${id}-list`}
          role="listbox"
          aria-label={label}
        >
          {locations.isPending ? (
            <p>Đang tìm địa điểm…</p>
          ) : locations.isError ? (
            <ErrorState
              error={locations.error}
              retry={() => locations.refetch()}
            />
          ) : locations.data?.length === 0 ? (
            <p>Không tìm thấy địa điểm.</p>
          ) : (
            locations.data?.map((location, index) => (
              <button
                type="button"
                role="option"
                aria-selected={active === index}
                id={`${id}-${index}`}
                key={location.id}
                onMouseDown={(event) => event.preventDefault()}
                onClick={() => choose(location)}
              >
                <MapPin size={17} />
                <span>
                  <strong>{location.name}</strong>
                  <small>
                    {[location.district, location.province]
                      .filter(Boolean)
                      .join(", ")}
                  </small>
                </span>
              </button>
            ))
          )}
        </div>
      )}
    </div>
  );
}
export function SearchForm() {
  const [params] = useSearchParams();
  return <JourneyForm key={params.toString()} params={params} />;
}
function JourneyForm({ params }: { params: URLSearchParams }) {
  const navigate = useNavigate();
  const form = useForm<z.infer<typeof schema>>({
    resolver: zodResolver(schema),
    defaultValues: {
      ...readJourney(params),
      departureDate: params.get("departureDate") || today(),
    },
  });
  return (
    <form
      className="search-form card"
      onSubmit={form.handleSubmit((values) =>
        navigate(
          `/search?${journeyParams(values, params)}`,
        ),
      )}
    >
      <Controller
        name="pickupLocationId"
        control={form.control}
        render={({ field, fieldState }) => (
          <LocationInput
            label="Điểm đón"
            value={form.watch("pickupLabel")}
            onChange={(id, label) => { field.onChange(id); form.setValue("pickupLabel", label); }}
            error={fieldState.error?.message}
          />
        )}
      />
      <button type="button" className="secondary search-swap" aria-label="Đổi điểm đón và điểm trả" onClick={() => form.reset(swapJourney(form.getValues()))}><ArrowLeftRight size={20} /></button>
      <Controller
        name="dropoffLocationId"
        control={form.control}
        render={({ field, fieldState }) => (
          <LocationInput
            label="Điểm trả"
            value={form.watch("dropoffLabel")}
            onChange={(id, label) => { field.onChange(id); form.setValue("dropoffLabel", label); }}
            error={fieldState.error?.message}
          />
        )}
      />
      <Field
        label="Ngày đi"
        type="date"
        min={today()}
        {...form.register("departureDate")}
        value={form.watch("departureDate")}
        error={form.formState.errors.departureDate?.message}
      />
      <button type="submit">
        <Search size={18} />
        Tìm chuyến
      </button>
    </form>
  );
}
