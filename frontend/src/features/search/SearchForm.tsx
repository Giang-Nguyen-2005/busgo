import { useEffect, useId, useState } from "react";
import { useForm, Controller } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useQuery } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { ArrowRight, MapPin, Search } from "lucide-react";
import { get } from "../../api/client";
import type { Location } from "../../types/customer";
import { today } from "../../utils/format";
import { ErrorState, Field } from "../../components/ui";

const schema = z
  .object({
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
  error,
}: {
  label: string;
  onChange: (id: number) => void;
  error?: string;
}) {
  const id = useId();
  const [text, setText] = useState("");
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
    setText(location.name);
    onChange(location.id);
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
            setText(event.target.value);
            onChange(0);
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
  const navigate = useNavigate();
  const form = useForm<z.infer<typeof schema>>({
    resolver: zodResolver(schema),
    defaultValues: {
      pickupLocationId: 0,
      dropoffLocationId: 0,
      departureDate: today(),
    },
  });
  return (
    <form
      className="search-form card"
      onSubmit={form.handleSubmit((values) =>
        navigate(
          `/search?${new URLSearchParams(Object.entries(values).map(([key, value]) => [key, String(value)]))}`,
        ),
      )}
    >
      <Controller
        name="pickupLocationId"
        control={form.control}
        render={({ field, fieldState }) => (
          <LocationInput
            label="Điểm đón"
            onChange={field.onChange}
            error={fieldState.error?.message}
          />
        )}
      />
      <ArrowRight className="search-arrow" size={20} />
      <Controller
        name="dropoffLocationId"
        control={form.control}
        render={({ field, fieldState }) => (
          <LocationInput
            label="Điểm đến"
            onChange={field.onChange}
            error={fieldState.error?.message}
          />
        )}
      />
      <Field
        label="Ngày đi"
        type="date"
        min={today()}
        {...form.register("departureDate")}
        error={form.formState.errors.departureDate?.message}
      />
      <button type="submit">
        <Search size={18} />
        Tìm chuyến
      </button>
    </form>
  );
}
