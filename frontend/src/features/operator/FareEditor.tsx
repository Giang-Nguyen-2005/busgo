import { useState } from "react";
import { useFieldArray, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { fareSchema } from "./fareSchema";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { operatorApi } from "../../api/operatorApi";
import type {
  FareResponse,
  ReplaceFaresRequest,
  RouteStopResponse,
} from "../../types/operator";
import { Confirm, FormErrors, OperatorError, QueryState } from "./shared";
import { money } from "../../utils/format";
export function FareEditor({
  id,
  stops,
}: {
  id: number;
  stops: RouteStopResponse[];
}) {
  const query = useQuery({
    queryKey: ["operator", "fares", id],
    queryFn: ({ signal }) => operatorApi.fares(id, signal),
  });
  return (
    <section className="card">
      <h2>Bảng giá vé</h2>
      <p>
        Thao tác lưu <strong>thay thế TOÀN BỘ bảng giá đang hoạt động</strong>.
        Giữ tất cả các dòng muốn tiếp tục áp dụng. Dòng bị bỏ sẽ ngừng hoạt
        động. Lưu bảng rỗng sẽ ngừng toàn bộ giá vé của tuyến.
      </p>
      <QueryState query={query}>
        {(fares) => <FareForm key={id} id={id} stops={stops} fares={fares} />}
      </QueryState>
    </section>
  );
}
function FareForm({
  id,
  stops,
  fares,
}: {
  id: number;
  stops: RouteStopResponse[];
  fares: FareResponse[];
}) {
  const cache = useQueryClient();
  const [confirmation, setConfirmation] = useState<ReplaceFaresRequest | null>(
    null,
  );
  const toValues = (rows: FareResponse[]): ReplaceFaresRequest => ({
    fares: rows.map(({ fromRouteStopId, toRouteStopId, price }) => ({
      fromRouteStopId,
      toRouteStopId,
      price,
    })),
  });
  // Default values capture the complete loaded set once; background refetches never overwrite edits.
  const form = useForm<ReplaceFaresRequest>({
    resolver: zodResolver(fareSchema(stops)),
    defaultValues: toValues(fares),
  });
  const array = useFieldArray({ control: form.control, name: "fares" });
  const mutation = useMutation({
    mutationFn: (body: ReplaceFaresRequest) =>
      operatorApi.replaceFares(id, body),
    onSuccess: (saved) => {
      cache.setQueryData(["operator", "fares", id], saved);
      form.reset(toValues(saved));
      setConfirmation(null);
    },
  });
  const active = [...stops]
    .filter((s) => s.status === "ACTIVE")
    .sort((a, b) => a.stopOrder - b.stopOrder);
  const values = form.watch("fares");
  return (
    <form
      className="form-stack"
      onSubmit={form.handleSubmit((v) => {
        mutation.reset();
        setConfirmation(v);
      })}
    >
      <fieldset disabled={mutation.isPending || !!confirmation}>
        {array.fields.map((field, index) => {
          const from = active.find(
            (s) => s.id === values[index]?.fromRouteStopId,
          );
          return (
            <div className="operator-fare-row" key={field.id}>
              <label className="field">
                Điểm đi
                <select
                  {...form.register(`fares.${index}.fromRouteStopId`, {
                    valueAsNumber: true,
                  })}
                >
                  <option value={0}>Chọn điểm đi</option>
                  {active.map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.stopOrder}. {s.location.name}
                    </option>
                  ))}
                </select>
              </label>
              <label className="field">
                Điểm đến
                <select
                  {...form.register(`fares.${index}.toRouteStopId`, {
                    valueAsNumber: true,
                  })}
                >
                  <option value={0}>Chọn điểm đến</option>
                  {active.map((s) => (
                    <option
                      disabled={!from || s.stopOrder <= from.stopOrder}
                      key={s.id}
                      value={s.id}
                    >
                      {s.stopOrder}. {s.location.name}
                    </option>
                  ))}
                </select>
              </label>
              <label className="field">
                Giá (VND)
                <input
                  type="number"
                  step="0.01"
                  min="0.01"
                  max="9999999999.99"
                  {...form.register(`fares.${index}.price`, {
                    valueAsNumber: true,
                  })}
                />
              </label>
              <button
                type="button"
                className="secondary"
                aria-label={`Bỏ giá dòng ${index + 1}`}
                onClick={() => array.remove(index)}
              >
                Bỏ dòng
              </button>
              <FormErrors
                messages={[
                  form.formState.errors.fares?.[index]?.fromRouteStopId
                    ?.message,
                  form.formState.errors.fares?.[index]?.toRouteStopId?.message,
                  form.formState.errors.fares?.[index]?.price?.message,
                ]}
              />
            </div>
          );
        })}
        {!array.fields.length && (
          <p>
            Không có dòng giá. Lưu lúc này sẽ ngừng toàn bộ bảng giá hiện tại.
          </p>
        )}
        <div className="operator-actions">
          <button
            type="button"
            className="secondary"
            onClick={() =>
              array.append({ fromRouteStopId: 0, toRouteStopId: 0, price: 0 })
            }
          >
            Thêm dòng giá
          </button>
          <button
            type="button"
            className="secondary"
            onClick={() => {
              form.reset(toValues(fares));
              mutation.reset();
            }}
          >
            Bỏ thay đổi
          </button>
          <button type="submit">Xem lại toàn bộ bảng giá</button>
        </div>
      </fieldset>
      {confirmation && (
        <>
          <h3>Bảng giá sẽ được lưu ({confirmation.fares.length} dòng)</h3>
          <ul>
            {confirmation.fares.map((f) => (
              <li key={`${f.fromRouteStopId}:${f.toRouteStopId}`}>
                {stops.find((s) => s.id === f.fromRouteStopId)?.location.name} →{" "}
                {stops.find((s) => s.id === f.toRouteStopId)?.location.name}:{" "}
                {money(f.price)}
              </li>
            ))}
          </ul>
          <Confirm
            text={
              confirmation.fares.length
                ? "Thay thế toàn bộ bảng giá bằng các dòng trên? Mọi cặp điểm không có trong danh sách sẽ ngừng áp dụng."
                : "Xác nhận ngừng TOÀN BỘ giá vé? Khách hàng có thể không tìm hoặc đặt được các hành trình trên tuyến này."
            }
            pending={mutation.isPending}
            confirm={() => mutation.mutate(confirmation)}
            cancel={() => setConfirmation(null)}
          />
        </>
      )}
      {mutation.isError && <OperatorError error={mutation.error} />}
      {mutation.isSuccess && (
        <p className="notice success" role="status">
          Đã thay thế toàn bộ bảng giá.
        </p>
      )}
    </form>
  );
}
