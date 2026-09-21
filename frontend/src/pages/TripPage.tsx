import { useEffect, useRef, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import {
  Link,
  useLocation,
  useNavigate,
  useParams,
  useSearchParams,
} from "react-router-dom";
import { ArrowRight, BusFront } from "lucide-react";
import { get, post } from "../api/client";
import { errorCode } from "../api/errors";
import { useAuth } from "../features/auth/AuthProvider";
import { saveHold } from "../features/booking/holdStore";
import { SeatMap } from "../features/trip/SeatMap";
import {
  readSelection,
  saveSelection,
  clearSelection,
  revalidateSelection,
} from "../features/trip/selectionDraft";
import type { Hold, SeatMapData, TripDetail } from "../types/customer";
import {
  Empty,
  ErrorState,
  Journey,
  Loading,
  PriceSummary,
  Steps,
} from "../components/ui";
import { positiveId, safeReturn } from "../utils/format";
export function TripPage() {
  const { tripId } = useParams();
  const [params] = useSearchParams();
  return (
    <TripSelection
      key={`${tripId}-${params.get("pickupLocationId")}-${params.get("dropoffLocationId")}`}
    />
  );
}
function TripSelection() {
  const { tripId } = useParams();
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const location = useLocation();
  const auth = useAuth();
  const [selected, setSelected] = useState<number[]>([]);
  const journey = {
    pickupLocationId: Number(params.get("pickupLocationId")),
    dropoffLocationId: Number(params.get("dropoffLocationId")),
  };
  const [draft] = useState(() =>
    readSelection(
      Number(tripId),
      journey.pickupLocationId,
      journey.dropoffLocationId,
    ),
  );
  const restored = useRef(false);
  const [selectionNotice, setSelectionNotice] = useState("");
  const valid =
    positiveId(tripId) &&
    positiveId(params.get("pickupLocationId")) &&
    positiveId(params.get("dropoffLocationId")) &&
    journey.pickupLocationId !== journey.dropoffLocationId;
  const detail = useQuery({
    queryKey: ["trip", tripId, journey],
    queryFn: ({ signal }) =>
      get<TripDetail>(`/trips/${tripId}`, journey, signal),
    enabled: valid,
  });
  const seats = useQuery({
    queryKey: ["seats", tripId, journey],
    queryFn: ({ signal }) =>
      get<SeatMapData>(`/trips/${tripId}/seats`, journey, signal),
    enabled: valid,
    refetchInterval: 15_000,
    staleTime: 0,
    refetchOnMount: "always",
  });
  useEffect(() => {
    if (!seats.isSuccess || !seats.isFetchedAfterMount) return;
    if (draft && !restored.current) {
      restored.current = true;
      const kept = revalidateSelection(draft.tripSeatIds, seats.data.seats);
      setSelected(kept);
      clearSelection();
      setSelectionNotice(
        kept.length === draft.tripSeatIds.length
          ? "Đã khôi phục ghế bạn chọn và kiểm tra lại chỗ trống. Tiếp tục để giữ chỗ."
          : "Một số ghế bạn chọn trước khi đăng nhập không còn khả dụng. Các ghế còn trống được giữ trong lựa chọn; vui lòng chọn lại ghế khác nếu cần.",
      );
    } else {
      const kept = revalidateSelection(selected, seats.data.seats);
      if (kept.length !== selected.length) {
        setSelected(kept);
        setSelectionNotice(
          "Một số ghế đã không còn khả dụng. Lựa chọn đã được cập nhật; vui lòng kiểm tra trước khi tiếp tục.",
        );
      }
    }
  }, [seats.data, seats.isSuccess, seats.isFetchedAfterMount, draft, selected]);
  const availableSelection = selected.filter((id) =>
    seats.data?.seats.some((seat) => seat.tripSeatId === id && seat.available),
  );
  const hold = useMutation({
    mutationFn: () =>
      post<Hold>("/seat-holds", {
        tripId: Number(tripId),
        ...journey,
        tripSeatIds: availableSelection,
      }),
    onSuccess: (data) => {
      clearSelection();
      saveHold({
        ...data,
        operatorName: detail.data?.operator.name,
        routeName: detail.data?.route.name,
      });
      navigate("/booking");
    },
    onError: (error) => {
      if (errorCode(error) === "SEAT_NOT_AVAILABLE") {
        setSelected([]);
        void seats.refetch();
      }
    },
  });
  if (!valid)
    return (
      <Empty title="Thiếu thông tin hành trình">
        <p>Hãy tìm chuyến với điểm đón và điểm đến để xem ghế.</p>
        <Link className="button" to="/">
          Tìm chuyến
        </Link>
      </Empty>
    );
  return (
    <>
      <Steps current={0} />
      {location.state?.notice && (
        <div className="notice warning" role="status">
          {String(location.state.notice)}
        </div>
      )}
      {selectionNotice && (
        <div className="notice info" role="status">
          {selectionNotice}
        </div>
      )}
      {detail.isPending || seats.isPending ? (
        <Loading />
      ) : detail.isError || seats.isError ? (
        <ErrorState
          error={detail.error || seats.error}
          retry={() => {
            void detail.refetch();
            void seats.refetch();
          }}
        />
      ) : (
        <>
          <div className="page-heading">
            <div>
              <span className="eyebrow">CHỌN CHỖ CHO HÀNH TRÌNH</span>
              <h1>
                {detail.data.pickup.name} → {detail.data.dropoff.name}
              </h1>
              <p className="muted">
                {detail.data.operator.name} · {detail.data.busType.name}
              </p>
            </div>
            <Link className="text-button" to="/">
              Đổi hành trình
            </Link>
          </div>
          <div className="checkout-layout">
            <section className="card">
              <div className="section-title">
                <BusFront size={22} />
                <div>
                  <h2>Chọn ghế ngồi</h2>
                  <p className="muted">
                    Còn {seats.data.availableSeatCount} ghế · Tối đa 5 ghế mỗi
                    lần đặt
                  </p>
                </div>
              </div>
              {!seats.data.seats.length ? (
                <Empty title="Chưa có sơ đồ ghế" />
              ) : (
                <SeatMap
                  seats={seats.data.seats}
                  selected={availableSelection}
                  disabled={hold.isPending}
                  onToggle={(id) =>
                    setSelected((current) =>
                      current.includes(id)
                        ? current.filter((value) => value !== id)
                        : [...availableSelection, id],
                    )
                  }
                />
              )}
              {seats.data.availableSeatCount === 0 && (
                <div className="notice warning">
                  Hành trình hiện đã hết chỗ. Vui lòng chọn chuyến khác.
                </div>
              )}
            </section>
            <aside className="card summary sticky">
              <span className="eyebrow">THÔNG TIN CHUYẾN</span>
              <h2>{detail.data.operator.name}</h2>
              <p className="muted">{detail.data.route.name}</p>
              <Journey
                pickup={detail.data.pickup.name}
                dropoff={detail.data.dropoff.name}
                departure={detail.data.pickup.departureTime}
                arrival={detail.data.dropoff.arrivalTime}
              />
              <PriceSummary
                seats={seats.data.seats
                  .filter((s) => availableSelection.includes(s.tripSeatId))
                  .map((s) => s.seatCode)}
                unit={seats.data.price}
                total={availableSelection.length * seats.data.price}
              />
              <p className="fine-print">
                Giá tạm tính. Hệ thống sẽ xác nhận giá và giữ chỗ khi bạn tiếp
                tục.
              </p>
              {selected.length !== availableSelection.length && (
                <div className="notice warning">
                  Một số ghế đã thay đổi trạng thái. Vui lòng kiểm tra lựa chọn.
                </div>
              )}
              {hold.isError && <ErrorState error={hold.error} />}
              {auth.user && !auth.user.roles.includes("CUSTOMER") && (
                <p className="field-error">
                  Cần tài khoản có quyền khách hàng để đặt vé.
                </p>
              )}
              <button
                className="full"
                disabled={
                  !availableSelection.length ||
                  hold.isPending ||
                  (seats.isFetching && !seats.isFetchedAfterMount) ||
                  auth.loading ||
                  (!!auth.user && !auth.user.roles.includes("CUSTOMER"))
                }
                onClick={() => {
                  if (!auth.authenticated) {
                    const returnTo = safeReturn(
                      location.pathname + location.search,
                    );
                    saveSelection({
                      tripId: Number(tripId),
                      ...journey,
                      tripSeatIds: availableSelection,
                      returnTo,
                    });
                    navigate(`/login?returnTo=${encodeURIComponent(returnTo)}`);
                  } else hold.mutate();
                }}
              >
                {hold.isPending ? "Đang giữ chỗ…" : "Tiếp tục"}
                <ArrowRight size={17} />
              </button>
            </aside>
          </div>
        </>
      )}
    </>
  );
}
