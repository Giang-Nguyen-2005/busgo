import { createBrowserRouter, Link } from "react-router-dom";
import { CustomerLayout } from "../layouts/CustomerLayout";
import { CustomerGuard } from "../features/auth/AuthProvider";
import { HomePage } from "../pages/HomePage";
import { AuthPage } from "../pages/AuthPage";
import { Empty } from "../components/ui";

export const router = createBrowserRouter([
  {
    element: <CustomerLayout />,
    errorElement: (
      <Empty title="Không thể hiển thị trang">
        <p>Vui lòng tải lại trang để thử lại.</p>
        <a href="/">Về trang chủ</a>
      </Empty>
    ),
    children: [
      { path: "/", element: <HomePage /> },
      { path: "/login", element: <AuthPage /> },
      { path: "/register", element: <AuthPage register /> },
      {
        path: "/search",
        lazy: async () => ({
          Component: (await import("../pages/SearchPage")).SearchPage,
        }),
      },
      {
        path: "/trips/:tripId",
        lazy: async () => ({
          Component: (await import("../pages/TripPage")).TripPage,
        }),
      },
      {
        element: <CustomerGuard />,
        children: [
          {
            path: "/booking",
            lazy: async () => ({
              Component: (await import("../pages/BookingPage")).BookingPage,
            }),
          },
          {
            path: "/payment",
            lazy: async () => ({
              Component: (await import("../pages/PaymentPage")).PaymentPage,
            }),
          },
          {
            path: "/booking-success",
            lazy: async () => ({
              Component: (await import("../pages/TicketPage")).TicketPage,
            }),
          },
          {
            path: "/profile",
            lazy: async () => ({
              Component: (await import("../pages/ProfilePage")).ProfilePage,
            }),
          },
          {
            path: "/my-bookings",
            lazy: async () => ({
              Component: (await import("../pages/MyBookingsPage"))
                .MyBookingsPage,
            }),
          },
          {
            path: "/my-bookings/:bookingId",
            lazy: async () => ({
              Component: (await import("../pages/BookingDetailPage"))
                .BookingDetailPage,
            }),
          },
        ],
      },
      {
        path: "*",
        element: (
          <Empty title="Không tìm thấy trang">
            <Link className="button" to="/">
              Về trang chủ
            </Link>
          </Empty>
        ),
      },
    ],
  },
]);
