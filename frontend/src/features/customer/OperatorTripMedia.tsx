import { useQuery } from "@tanstack/react-query";
import { get } from "../../api/client";
import { OperatorLogo, VehicleImage, type VehiclePhoto } from "./CustomerMedia";

function useOperatorGallery(operatorId: number) {
  return useQuery({
    queryKey: ["operator-gallery", operatorId],
    queryFn: ({ signal }) => get<{logoUrl: string | null; photos: VehiclePhoto[]}>(`/public/operators/${operatorId}/media`, undefined, signal),
    staleTime: 60000,
    retry: false,
  }).data;
}
export function TripOperatorLogo({operator}: {operator: {id:number; name:string}}) {
  const gallery=useOperatorGallery(operator.id);
  return <OperatorLogo name={operator.name} logoUrl={gallery?.logoUrl} />;
}
export function OperatorVehicleImage({operatorId,busType,compact=false}: {operatorId:number; busType:{id:number; name:string}; compact?:boolean}) {
  const gallery=useOperatorGallery(operatorId);
  return <VehicleImage busType={busType} compact={compact} photos={gallery?.photos.filter(photo=>photo.busTypeId===busType.id)} />;
}
