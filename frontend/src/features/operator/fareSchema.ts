import { z } from "zod";
import type { RouteStopResponse } from "../../types/operator";

export function fareSchema(stops: RouteStopResponse[]) {
  return z
    .object({
      fares: z.array(
        z.object({
          fromRouteStopId: z.number().int().positive("Chọn điểm đi."),
          toRouteStopId: z.number().int().positive("Chọn điểm đến."),
          price: z
            .number()
            .positive("Giá phải lớn hơn 0.")
            .max(9999999999.99, "Giá vượt giới hạn.")
            .refine(
              (v) => Math.abs(v * 100 - Math.round(v * 100)) < 0.0001,
              "Giá tối đa 2 chữ số thập phân.",
            ),
        }),
      ),
    })
    .superRefine((values, ctx) => {
      const pairs = new Set<string>();
      values.fares.forEach((fare, index) => {
        const from = stops.find(
          (s) => s.id === fare.fromRouteStopId && s.status === "ACTIVE",
        );
        const to = stops.find(
          (s) => s.id === fare.toRouteStopId && s.status === "ACTIVE",
        );
        if (!from || !to || from.stopOrder >= to.stopOrder)
          ctx.addIssue({
            code: "custom",
            path: ["fares", index, "toRouteStopId"],
            message: "Điểm đến phải nằm sau điểm đi trên tuyến đang hoạt động.",
          });
        const pair = `${fare.fromRouteStopId}:${fare.toRouteStopId}`;
        if (pairs.has(pair))
          ctx.addIssue({
            code: "custom",
            path: ["fares", index, "fromRouteStopId"],
            message: "Cặp điểm đi/đến bị trùng.",
          });
        pairs.add(pair);
      });
    });
}
