import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useNavigate } from "react-router-dom";
import { LockKeyhole, UserRound } from "lucide-react";
import { apiClient } from "../api/client";
import { useAuth } from "../features/auth/AuthProvider";
import { name, password, phone } from "../features/auth/forms";
import { ErrorState, Field } from "../components/ui";
const profileSchema = z.object({ fullName: name, phone });
const passwordSchema = z
  .object({
    currentPassword: z.string().min(1, "Nhập mật khẩu hiện tại."),
    newPassword: password,
    confirmPassword: z.string(),
  })
  .refine((v) => v.newPassword === v.confirmPassword, {
    path: ["confirmPassword"],
    message: "Mật khẩu xác nhận chưa khớp.",
  });
export function ProfilePage() {
  const auth = useAuth();
  const cache = useQueryClient();
  const navigate = useNavigate();
  const profile = useForm<z.infer<typeof profileSchema>>({
    resolver: zodResolver(profileSchema),
    defaultValues: {
      fullName: auth.user?.fullName || "",
      phone: auth.user?.phone || "",
    },
  });
  const passwords = useForm<z.infer<typeof passwordSchema>>({
    resolver: zodResolver(passwordSchema),
  });
  const update = useMutation({
    mutationFn: (values: z.infer<typeof profileSchema>) =>
      apiClient.patch("/users/me", values),
    onSuccess: () => {
      void cache.invalidateQueries({ queryKey: ["me"] });
    },
  });
  const change = useMutation({
    mutationFn: ({
      confirmPassword: _,
      ...values
    }: z.infer<typeof passwordSchema>) =>
      apiClient.post("/users/me/change-password", values),
    onSuccess: () => {
      passwords.reset();
      auth.logout();
      navigate("/login?returnTo=%2Fprofile", { replace: true });
    },
  });
  return (
    <>
      <div className="page-heading">
        <div>
          <span className="eyebrow">TÀI KHOẢN BUSGO</span>
          <h1>Thông tin cá nhân</h1>
          <p className="muted">Cập nhật thông tin để đặt vé nhanh hơn.</p>
        </div>
      </div>
      <div className="profile-layout">
        <section className="card">
          <div className="section-title">
            <UserRound />
            <h2>Hồ sơ của bạn</h2>
          </div>
          <form
            className="form-stack"
            onSubmit={profile.handleSubmit((values) => update.mutate(values))}
          >
            <Field label="Email" value={auth.user?.email || ""} readOnly />
            <Field
              label="Họ và tên"
              autoComplete="name"
              {...profile.register("fullName")}
              error={profile.formState.errors.fullName?.message}
            />
            <Field
              label="Số điện thoại"
              type="tel"
              autoComplete="tel"
              {...profile.register("phone")}
              error={profile.formState.errors.phone?.message}
            />
            {update.isError && <ErrorState error={update.error} />}
            {update.isSuccess && (
              <div className="notice success" role="status">
                Đã cập nhật thông tin.
              </div>
            )}
            <button disabled={update.isPending}>
              {update.isPending ? "Đang lưu…" : "Lưu thay đổi"}
            </button>
          </form>
        </section>
        <section className="card">
          <div className="section-title">
            <LockKeyhole />
            <h2>Đổi mật khẩu</h2>
          </div>
          <p className="muted">
            Sau khi đổi mật khẩu, vui lòng đăng nhập lại để tiếp tục.
          </p>
          <form
            className="form-stack"
            onSubmit={passwords.handleSubmit((values) => change.mutate(values))}
          >
            <Field
              label="Mật khẩu hiện tại"
              type="password"
              autoComplete="current-password"
              {...passwords.register("currentPassword")}
              error={passwords.formState.errors.currentPassword?.message}
            />
            <Field
              label="Mật khẩu mới"
              type="password"
              autoComplete="new-password"
              {...passwords.register("newPassword")}
              error={passwords.formState.errors.newPassword?.message}
            />
            <Field
              label="Nhập lại mật khẩu mới"
              type="password"
              autoComplete="new-password"
              {...passwords.register("confirmPassword")}
              error={passwords.formState.errors.confirmPassword?.message}
            />
            {change.isError && <ErrorState error={change.error} />}
            <button className="secondary" disabled={change.isPending}>
              {change.isPending ? "Đang đổi mật khẩu…" : "Cập nhật mật khẩu"}
            </button>
          </form>
        </section>
      </div>
    </>
  );
}
