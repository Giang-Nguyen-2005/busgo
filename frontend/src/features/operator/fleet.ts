import { statusLabels } from "../../utils/status";
import type { MaintenanceStatus, MaintenanceType } from '../../types/fleet';
export const maintenanceTypes:Record<MaintenanceType,string> = { PERIODIC_SERVICE:'Bảo dưỡng định kỳ', OIL_CHANGE:'Thay dầu', TIRE:'Lốp xe', BRAKE:'Phanh', ELECTRICAL:'Hệ thống điện', ENGINE:'Động cơ', AIR_CONDITIONING:'Điều hòa', INSPECTION:'Kiểm tra', REPAIR:'Sửa chữa', OTHER:'Khác' };
export const maintenanceStatuses:Record<MaintenanceStatus,string> = statusLabels.maintenance;
export function maintenanceActions(status:MaintenanceStatus, manage:boolean) { return !manage?[]:status==='SCHEDULED'?['start','cancel']:status==='IN_PROGRESS'?['complete']:[]; }
// Inputs represent Vietnam wall time regardless of browser timezone.
export function vietnamTimestamp(value:string) { return new Date(value+':00+07:00').toISOString(); }
export function validMaintenanceWindow(start:string,end:string) { return Number.isFinite(Date.parse(start+':00+07:00')) && Date.parse(end+':00+07:00')>Date.parse(start+':00+07:00'); }
