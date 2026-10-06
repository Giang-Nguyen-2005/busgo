import { RouteProgress } from "../components/RouteProgress";
import { operatorTripRoute } from "./operatorTripRoutes";
import { createBrowserRouter, Link, Outlet } from "react-router-dom";
import { CustomerLayout } from "../layouts/CustomerLayout";
import { CustomerGuard } from "../features/auth/AuthProvider";

import { Empty } from "../components/ui";

export const router = createBrowserRouter([{ element: <><RouteProgress /><Outlet /></>, children: [
  { path: "/pay/:token", lazy: async () => ({ Component: (await import("../pages/PublicPaymentPage")).PublicPaymentPage }) },
  {
    path: "/admin",
    lazy: async () => ({ Component: (await import("../features/auth/SystemAdminGuard")).SystemAdminGuard }),
    errorElement: <Empty title="Không thể hiển thị khu vực quản trị"><a href="/admin">Tải lại</a></Empty>,
    children: [{
      lazy: async () => ({ Component: (await import("../layouts/AdminLayout")).AdminLayout }),
      children: [
        { index: true, lazy: async () => ({ Component: (await import("../pages/admin/AdminPages")).AdminHomePage }) },
        { path: "operators", lazy: async () => ({ Component: (await import("../pages/admin/AdminPages")).AdminOperatorsPage }) },
        { path: "operators/new", lazy: async () => ({ Component: (await import("../pages/admin/AdminPages")).AdminOperatorCreatePage }) },
        { path: "operators/:operatorId", lazy: async () => ({ Component: (await import("../pages/admin/AdminPages")).AdminOperatorDetailPage }) },
        { path: "*", element: <Empty title="Không tìm thấy trang quản trị"><Link to="/admin">Tổng quan</Link></Empty> },
      ],
    }],
  },
  {
    path: "/operator",
    lazy: async () => ({
      Component: (await import("../features/auth/OperatorGuard")).OperatorGuard,
    }),
    errorElement: (
      <Empty title="Không thể hiển thị khu vực nhà xe">
        <a href="/operator">Tải lại khu vực nhà xe</a>
      </Empty>
    ),
    children: [
      {
        lazy: async () => ({
          Component: (await import("../layouts/OperatorLayout")).OperatorLayout,
        }),
        children: [
          { path: "notifications", lazy: async () => ({ Component: (await import("../features/notifications/Notifications")).NotificationsPage }) },
          { path: "maintenance", lazy: async () => ({ Component: (await import("../features/operator/FleetMaintenance")).OperatorMaintenancePage }) },
          { path: "customers", lazy: async () => ({ Component: (await import("../features/operator/Customers")).OperatorCustomersPage }) },
          { path: "customers/:customerKey", lazy: async () => ({ Component: (await import("../features/operator/Customers")).OperatorCustomerDetailPage }) },
          { path: "reports", lazy: async () => ({ Component: (await import("../features/operator/Reports")).OperatorReportsPage }) },
          { path: "employees", lazy: async () => ({ Component: (await import("../features/operator/CrewBoarding")).EmployeeDirectory }) },
          {
            path: "staff",
            lazy: async () => ({ Component: (await import("../features/operator/StaffManagement")).StaffManagement }),
          },
          {
            path: "bookings",
            lazy: async () => ({ Component: (await import("../pages/operator/OperatorBookingsPages")).OperatorBookingsPage }),
          },
          {
            path: "bookings/new",
            lazy: async () => ({ Component: (await import("../pages/operator/OperatorBookingCreatePage")).OperatorBookingCreatePage }),
          },
          {
            path: "bookings/:bookingId/modify",
            lazy: async () => ({ Component: (await import("../features/booking/Modification")).ModificationPage }),
          },
          {
            path: "bookings/:bookingId",
            lazy: async () => ({ Component: (await import("../pages/operator/OperatorBookingsPages")).OperatorBookingDetailPage }),
          },
          {
            index: true,
            lazy: async () => ({
              Component: (await import("../pages/operator/OperatorHomePage"))
                .OperatorHomePage,
            }),
          },
          {
            path: "trips",
            lazy: async () => ({
              Component: (await import("../pages/operator/OperatorTripsPages"))
                .OperatorTripsPage,
            }),
          },
          {
            path: "trips/new",
            lazy: async () => ({
              Component: (await import("../pages/operator/OperatorTripsPages"))
                .OperatorTripCreatePage,
            }),
          },
          operatorTripRoute,
          {
            path: "buses",
            lazy: async () => ({
              Component: (await import("../pages/operator/OperatorBusesPages"))
                .OperatorBusesPage,
            }),
          },
          {
            path: "buses/new",
            lazy: async () => ({
              Component: (await import("../pages/operator/OperatorBusesPages"))
                .OperatorBusCreatePage,
            }),
          },
          {
            path: "buses/:busId",
            lazy: async () => ({
              Component: (await import("../pages/operator/OperatorBusesPages"))
                .OperatorBusDetailPage,
            }),
          },
          {
            path: "bus-types",
            lazy: async () => ({
              Component: (
                await import("../pages/operator/OperatorBusTypesPages")
              ).OperatorBusTypesPage,
            }),
          },
          {
            path: "bus-types/:busTypeId",
            lazy: async () => ({
              Component: (
                await import("../pages/operator/OperatorBusTypesPages")
              ).OperatorBusTypeDetailPage,
            }),
          },
          {
            path: "routes",
            lazy: async () => ({
              Component: (await import("../pages/operator/OperatorRoutesPages"))
                .OperatorRoutesPage,
            }),
          },
          {
            path: "routes/catalog",
            lazy: async () => ({
              Component: (await import("../pages/operator/OperatorRoutesPages"))
                .OperatorRouteCatalogPage,
            }),
          },
          {
            path: "routes/:operatorRouteId",
            lazy: async () => ({
              Component: (await import("../pages/operator/OperatorRoutesPages"))
                .OperatorRouteDetailPage,
            }),
          },
          {
            path: "*",
            element: (
              <Empty title="Không tìm thấy trang nhà xe">
                <Link to="/operator">Về khu vực nhà xe</Link>
              </Empty>
            ),
          },
        ],
      },
    ],
  },
  {
    element: <CustomerLayout />,
    errorElement: (
      <Empty title="Không thể hiển thị trang">
        <p>Vui lòng tải lại trang để thử lại.</p>
        <a href="/">Về trang chủ</a>
      </Empty>
    ),
    children: [
      { path: "/", lazy: async () => ({ Component: (await import("../pages/HomePage")).HomePage }) },
      { path: "/login", lazy: async () => ({ Component: (await import("../pages/AuthPage")).AuthPage }) },
      { path: "/register", lazy: async () => ({ Component: (await import("../pages/AuthPage")).RegisterPage }) },
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
          { path: "/notifications", lazy: async () => ({ Component: (await import("../features/notifications/Notifications")).NotificationsPage }) },
          { path: "/notification-preferences", lazy: async () => ({ Component: (await import("../features/notifications/Notifications")).NotificationPreferencesPage }) },
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
            path: "/my-bookings/:bookingId/modify",
            lazy: async () => ({ Component: (await import("../features/booking/Modification")).ModificationPage }),
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
] }]);
