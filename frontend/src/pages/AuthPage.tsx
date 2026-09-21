import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useMutation } from "@tanstack/react-query";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { BusFront, ShieldCheck, Ticket } from "lucide-react";
import { post } from "../api/client";
import { useAuth } from "../features/auth/AuthProvider";
import { email, name, password, phone } from "../features/auth/forms";
import type { LoginResponse } from "../types/customer";
import { safeReturn } from "../utils/format";
import { ErrorState, Field } from "../components/ui";

const loginSchema = z.object({
  email,
  password: z.string().min(1, "Vui lòng nhập mật khẩu."),
});
const registerSchema = z
  .object({
    email,
    password,
    fullName: name,
    phone,
    confirmPassword: z.string(),
  })
  .refine((value) => value.password === value.confirmPassword, {
    path: ["confirmPassword"],
    message: "Mật khẩu xác nhận chưa khớp.",
  });
export function AuthPage({ register = false }: { register?: boolean }) {
  return (
    <div className="auth-layout">
      <aside className="auth-story">
        <span className="eyebrow">CÙNG BUSGO, ĐI MUÔN NƠI</span>
        <h1>
          Hành trình mới.
          <br />
          Bắt đầu thật dễ dàng.
        </h1>
        <p>
          Tìm chuyến phù hợp, chọn chỗ yêu thích và quản lý vé trong một nơi.
        </p>
        <div>
          <Ticket />
          <span>Vé điện tử ngay sau thanh toán giả lập</span>
        </div>
        <div>
          <ShieldCheck />
          <span>Thông tin đặt vé được xác nhận bởi hệ thống</span>
        </div>
        <BusFront className="story-bus" size={100} />
      </aside>
      <section className="card auth-form">
        <span className="eyebrow">CHÀO MỪNG ĐẾN BUSGO</span>
        <h1>{register ? "Tạo tài khoản" : "Đăng nhập"}</h1>
        <p className="muted">
          {register
            ? "Sẵn sàng cho chuyến đi tiếp theo của bạn."
            : "Tiếp tục hành trình và xem vé của bạn."}
        </p>
        {register ? <RegisterForm /> : <LoginForm />}
      </section>
    </div>
  );
}
function LoginForm() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const auth = useAuth();
  const form = useForm<z.infer<typeof loginSchema>>({
    resolver: zodResolver(loginSchema),
  });
  const mutation = useMutation({
    mutationFn: (values: z.infer<typeof loginSchema>) =>
      post<LoginResponse>("/auth/login", values),
    onSuccess: (tokens) => {
      auth.login(tokens);
      navigate(safeReturn(params.get("returnTo")), { replace: true });
    },
  });
  return (
    <form
      className="form-stack"
      onSubmit={form.handleSubmit((values) => mutation.mutate(values))}
    >
      {params.get("registered") && (
        <div className="notice success">
          Đăng ký thành công. Mời bạn đăng nhập.
        </div>
      )}
      <Field
        label="Email"
        type="email"
        autoComplete="email"
        {...form.register("email")}
        error={form.formState.errors.email?.message}
      />
      <Field
        label="Mật khẩu"
        type="password"
        autoComplete="current-password"
        {...form.register("password")}
        error={form.formState.errors.password?.message}
      />
      {mutation.isError && <ErrorState error={mutation.error} />}
      <button disabled={mutation.isPending}>
        {mutation.isPending ? "Đang đăng nhập…" : "Đăng nhập"}
      </button>
      <p className="muted">
        Chưa có tài khoản?{" "}
        <Link
          to={`/register?returnTo=${encodeURIComponent(safeReturn(params.get("returnTo")))}`}
        >
          Đăng ký ngay
        </Link>
      </p>
    </form>
  );
}
function RegisterForm() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const form = useForm<z.infer<typeof registerSchema>>({
    resolver: zodResolver(registerSchema),
  });
  const mutation = useMutation({
    mutationFn: ({
      confirmPassword: _,
      ...values
    }: z.infer<typeof registerSchema>) => post("/auth/register", values),
    onSuccess: () =>
      navigate(
        `/login?registered=1&returnTo=${encodeURIComponent(safeReturn(params.get("returnTo")))}`,
        { replace: true },
      ),
  });
  return (
    <form
      className="form-stack"
      onSubmit={form.handleSubmit((values) => mutation.mutate(values))}
    >
      <Field
        label="Họ và tên"
        autoComplete="name"
        {...form.register("fullName")}
        error={form.formState.errors.fullName?.message}
      />
      <Field
        label="Email"
        type="email"
        autoComplete="email"
        {...form.register("email")}
        error={form.formState.errors.email?.message}
      />
      <Field
        label="Số điện thoại"
        type="tel"
        autoComplete="tel"
        {...form.register("phone")}
        error={form.formState.errors.phone?.message}
      />
      <Field
        label="Mật khẩu"
        type="password"
        autoComplete="new-password"
        {...form.register("password")}
        error={form.formState.errors.password?.message}
      />
      <small className="muted">Ít nhất 8 ký tự, tối đa 72 byte UTF-8.</small>
      <Field
        label="Nhập lại mật khẩu"
        type="password"
        autoComplete="new-password"
        {...form.register("confirmPassword")}
        error={form.formState.errors.confirmPassword?.message}
      />
      {mutation.isError && <ErrorState error={mutation.error} />}
      <button disabled={mutation.isPending}>
        {mutation.isPending ? "Đang tạo tài khoản…" : "Đăng ký"}
      </button>
      <p className="muted">
        Đã có tài khoản?{" "}
        <Link
          to={`/login?returnTo=${encodeURIComponent(safeReturn(params.get("returnTo")))}`}
        >
          Đăng nhập
        </Link>
      </p>
    </form>
  );
}
