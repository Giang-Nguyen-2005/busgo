import { z } from "zod";
export const name = z
  .string()
  .trim()
  .min(1, "Vui lòng nhập họ và tên.")
  .max(100, "Tối đa 100 ký tự.");
export const phone = z
  .string()
  .trim()
  .min(1, "Vui lòng nhập số điện thoại.")
  .max(20, "Tối đa 20 ký tự.");
export const email = z
  .email("Email không hợp lệ.")
  .max(150, "Tối đa 150 ký tự.");
export const password = z
  .string()
  .refine(
    (value) =>
      value.trim().length > 0 &&
      [...value].length >= 8 &&
      new TextEncoder().encode(value).length <= 72,
    "Ít nhất 8 ký tự, tối đa 72 byte UTF-8.",
  );
export const contactSchema = z.object({
  contactName: name,
  contactPhone: phone,
  contactEmail: email,
});
