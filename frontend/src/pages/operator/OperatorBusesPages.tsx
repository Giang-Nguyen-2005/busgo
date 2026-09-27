import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate, useParams } from "react-router-dom";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { operatorApi } from "../../api/operatorApi";
import type { BusResponse } from "../../types/operator";
import { Field } from "../../components/ui";
import { useBusTypes } from "../../features/operator/queries";
import {
  busStatuses,
  Confirm,
  FormErrors,
  OperatorError,
  OperatorPageHeader,
  OperatorStatusBadge,
  OperatorTable,
  Pagination,
  positiveId,
  QueryState,
  useOperatorFilters,
} from "../../features/operator/shared";
export function OperatorBusesPage() {
  const { params, page, size, set } = useOperatorFilters();
  const types = useBusTypes();
  const status = busStatuses.find((s) => s === params.get("status"));
  const filters = {
    page,
    size,
    q: (params.get("q") || "").slice(0, 30),
    status,
    busTypeId: positiveId(params.get("busTypeId")),
  };
  const query = useQuery({
    queryKey: ["operator", "buses", filters],
    queryFn: ({ signal }) => operatorApi.buses(filters, signal),
  });
  return (
    <>
      <OperatorPageHeader title="Đội xe">
        <Link className="button" to="/operator/buses/new">
          Thêm xe
        </Link>
      </OperatorPageHeader>
      <form
        className="operator-filters"
        onSubmit={(e) => {
          e.preventDefault();
          set("q", String(new FormData(e.currentTarget).get("q") || "").trim());
        }}
      >
        <Field
          key={params.get("q")}
          label="Tìm biển số"
          name="q"
          maxLength={30}
          defaultValue={params.get("q") || ""}
        />
        <button type="submit">Tìm kiếm</button>
        <label className="field">
          Trạng thái
          <select
            value={status || ""}
            onChange={(e) => set("status", e.target.value)}
          >
            <option value="">Tất cả</option>
            {busStatuses.map((s) => (
              <option key={s}>{s}</option>
            ))}
          </select>
        </label>
        <label className="field">
          Loại xe
          <select
            value={filters.busTypeId || ""}
            onChange={(e) => set("busTypeId", e.target.value)}
          >
            <option value="">Tất cả</option>
            {types.data?.map((t) => (
              <option value={t.id} key={t.id}>
                {t.name}
              </option>
            ))}
          </select>
        </label>
      </form>
      {types.isError && (
        <OperatorError error={types.error} retry={() => types.refetch()} />
      )}
      <QueryState query={query}>
        {(result) => (
          <>
            <OperatorTable
              headers={["Biển số", "Loại xe", "Số ghế", "Trạng thái"]}
              empty={!result.data.length}
            >
              {result.data.map((b) => (
                <tr key={b.id}>
                  <td>
                    <Link to={`/operator/buses/${b.id}`}>{b.licensePlate}</Link>
                  </td>
                  <td>{b.busType.name}</td>
                  <td>{b.busType.seatCount}</td>
                  <td>
                    <OperatorStatusBadge status={b.status} />
                  </td>
                </tr>
              ))}
            </OperatorTable>
            <Pagination pagination={result.pagination} set={set} />
          </>
        )}
      </QueryState>
    </>
  );
}
const busSchema = z.object({
  licensePlate: z
    .string()
    .trim()
    .min(1, "Nhập biển số.")
    .max(30, "Biển số tối đa 30 ký tự."),
  busTypeId: z.number().int().positive("Chọn loại xe."),
  status: z.enum(busStatuses),
});
type BusValues = z.infer<typeof busSchema>;
function BusForm({ bus }: { bus?: BusResponse }) {
  const types = useBusTypes();
  const cache = useQueryClient();
  const navigate = useNavigate();
  const [confirmation, setConfirmation] = useState<BusValues | null>(null);
  const form = useForm<BusValues>({
    resolver: zodResolver(busSchema),
    defaultValues: {
      licensePlate: bus?.licensePlate || "",
      busTypeId: bus?.busType.id || 0,
      status: bus?.status || "AVAILABLE",
    },
  });
  const mutation = useMutation({
    mutationFn: (values: BusValues) =>
      bus
        ? operatorApi.updateBus(bus.id, {
            licensePlate:
              values.licensePlate !== bus.licensePlate
                ? values.licensePlate
                : undefined,
            busTypeId:
              values.busTypeId !== bus.busType.id
                ? values.busTypeId
                : undefined,
            status: values.status !== bus.status ? values.status : undefined,
          })
        : operatorApi.createBus({
            licensePlate: values.licensePlate,
            busTypeId: values.busTypeId,
          }),
    onSuccess: async (saved) => {
      setConfirmation(null);
      form.reset({
        licensePlate: saved.licensePlate,
        busTypeId: saved.busType.id,
        status: saved.status,
      });
      await cache.invalidateQueries({ queryKey: ["operator", "buses"] });
      navigate(`/operator/buses/${saved.id}`, { replace: !bus });
    },
  });
  const submit = (values: BusValues) => {
    if (bus && values.status !== bus.status && values.status !== "AVAILABLE")
      setConfirmation(values);
    else mutation.mutate(values);
  };
  return (
    <QueryState query={types}>
      {(rows) => (
        <form
          className="card form-stack operator-form"
          onSubmit={form.handleSubmit(submit)}
        >
          <fieldset disabled={mutation.isPending || !!confirmation}>
            <Field
              label="Biển số"
              maxLength={30}
              {...form.register("licensePlate")}
              error={form.formState.errors.licensePlate?.message}
            />
            <label className="field">
              Loại xe
              <select {...form.register("busTypeId", { valueAsNumber: true })}>
                <option value={0}>Chọn loại xe</option>
                {bus && !rows.some((r) => r.id === bus.busType.id) && (
                  <option value={bus.busType.id}>
                    {bus.busType.name} (hiện tại)
                  </option>
                )}
                {rows.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.name} · {t.seatCount} ghế
                  </option>
                ))}
              </select>
            </label>
            {bus ? (
              <label className="field">
                Trạng thái
                <select {...form.register("status")}>
                  {busStatuses.map((s) => (
                    <option key={s}>{s}</option>
                  ))}
                </select>
              </label>
            ) : (
              <p>Xe mới được tạo ở trạng thái AVAILABLE.</p>
            )}
            <FormErrors
              messages={[
                form.formState.errors.busTypeId?.message,
                form.formState.errors.status?.message,
              ]}
            />
            <button disabled={mutation.isPending}>
              {mutation.isPending
                ? "Đang lưu…"
                : bus
                  ? "Lưu thay đổi"
                  : "Tạo xe"}
            </button>
          </fieldset>
          {confirmation && (
            <Confirm
              text={`Chuyển xe ${bus?.licensePlate} sang ${confirmation.status}? Xe sẽ không sẵn sàng để tạo chuyến mới.`}
              pending={mutation.isPending}
              confirm={() => mutation.mutate(confirmation)}
              cancel={() => setConfirmation(null)}
            />
          )}
          {mutation.isError && <OperatorError error={mutation.error} />}
          {mutation.isSuccess && (
            <p role="status" className="notice success">
              Đã lưu xe.
            </p>
          )}
          {!rows.length && (
            <p>
              Chưa có loại xe hoạt động. Cần cấu hình loại xe trước khi tạo xe.
            </p>
          )}
        </form>
      )}
    </QueryState>
  );
}
export function OperatorBusCreatePage() {
  return (
    <>
      <OperatorPageHeader title="Thêm xe">
        <Link to="/operator/buses">Danh sách xe</Link>
      </OperatorPageHeader>
      <BusForm />
    </>
  );
}
export function OperatorBusDetailPage() {
  const id = Number(useParams().busId);
  const query = useQuery({
    queryKey: ["operator", "buses", id],
    queryFn: ({ signal }) => operatorApi.bus(id, signal),
  });
  return (
    <>
      <OperatorPageHeader title="Chi tiết xe">
        <Link to="/operator/buses">Danh sách xe</Link>
      </OperatorPageHeader>
      <QueryState query={query}>
        {(bus) => (
          <>
            <p>
              <OperatorStatusBadge status={bus.status} /> ·{" "}
              <Link to={`/operator/bus-types/${bus.busType.id}`}>
                Xem loại xe / mẫu ghế
              </Link>
            </p>
            <BusForm key={bus.id} bus={bus} />
          </>
        )}
      </QueryState>
    </>
  );
}
