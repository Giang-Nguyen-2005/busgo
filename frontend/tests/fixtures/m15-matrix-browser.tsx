// Development-only stress fixture: sixty seats, six segments and incomplete inventory.
import React from 'react';
import { createRoot } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { OccupancyContent } from '../../src/pages/operator/OperatorTripOperationsPages';
import type { TripOccupancy } from '../../src/types/operator';
import '../../src/styles.css';
import '../../src/features/operator/operator.css';
const segments = Array.from({length:6},(_,i)=>({tripSegmentId:90+i,segmentOrder:i+1,fromTripStopId:10+i,toTripStopId:11+i,fromName:`Điểm ${i+1}`,toName:`Điểm ${i+2}`,counts:{available:12,booked:12,held:12,blocked:12}}));
const states=['AVAILABLE','BOOKED','HELD','BLOCKED',null] as const;
const data: TripOccupancy={tripId:7,tripStatus:'SCHEDULED',seatCount:60,segmentCount:6,wholeTripAvailableSeatCount:0,complete:false,expectedInventoryCellCount:360,actualInventoryCellCount:288,missingInventoryCellCount:72,segments,seats:Array.from({length:60},(_,i)=>({tripSeatId:i+1,seatCode:`A${String(i+1).padStart(2,'0')}`,row:i%30+1,column:1,floor:i<30?1:2,seatType:'STANDARD',segments:segments.map((s,j)=>({tripSegmentId:s.tripSegmentId,segmentOrder:j+1,status:states[(i+j)%5],missing:states[(i+j)%5]===null,bookingId:states[(i+j)%5]==='BOOKED'?101:null,bookingCode:states[(i+j)%5]==='BOOKED'?'BG-STRESS-101':null,bookingStatus:states[(i+j)%5]==='BOOKED'?'PENDING':null,holdExpiresAt:states[(i+j)%5]==='HELD'?'2027-01-15T12:00:00+07:00':null}))}))};
createRoot(document.getElementById('root')!).render(<MemoryRouter><div className="operator-shell" style={{display:'block',padding:16}}><h1>Fixture · 60 ghế × 6 chặng</h1><OccupancyContent occupancy={data}/></div></MemoryRouter>);
