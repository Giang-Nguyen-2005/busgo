package com.busgo.route;

import com.busgo.common.entity.ActiveStatus;
import com.busgo.common.exception.BusinessException;
import com.busgo.common.security.CurrentUser;
import com.busgo.operator.OperatorContextService;
import com.busgo.route.RouteDtos.*;
import com.busgo.route.entity.*;
import com.busgo.route.repository.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperatorRouteFareService {
    private final OperatorRouteFareRepository fares;
    private final RouteStopRepository stops;
    private final OperatorRouteService operatorRoutes;
    private final OperatorContextService context;

    public OperatorRouteFareService(OperatorRouteFareRepository fares, RouteStopRepository stops,
            OperatorRouteService operatorRoutes, OperatorContextService context) {
        this.fares = fares;
        this.stops = stops;
        this.operatorRoutes = operatorRoutes;
        this.context = context;
    }

    @Transactional(readOnly = true)
    public List<FareResponse> list(CurrentUser user, Long operatorRouteId) {
        Long operatorId = context.requireAdminOperator(user).getId();
        operatorRoutes.owned(operatorRouteId, operatorId);
        return fares.findByOperatorRouteIdOrderByFromRouteStopStopOrderAscToRouteStopStopOrderAsc(operatorRouteId)
                .stream().filter(fare -> fare.getStatus() == ActiveStatus.ACTIVE).map(this::response).toList();
    }

    @Transactional
    public List<FareResponse> replace(CurrentUser user, Long operatorRouteId, ReplaceFaresRequest request) {
        Long operatorId = context.requireAdminOperator(user).getId();
        OperatorRoute association = operatorRoutes.owned(operatorRouteId, operatorId);
        Long routeId = association.getRoute().getId();
        List<ValidatedFare> validated = validate(request.fares(), routeId);
        List<OperatorRouteFare> existing = fares
                .findByOperatorRouteIdOrderByFromRouteStopStopOrderAscToRouteStopStopOrderAsc(operatorRouteId);
        existing.forEach(fare -> fare.setStatus(ActiveStatus.INACTIVE));
        Map<String, OperatorRouteFare> reusable = new HashMap<>();
        existing.forEach(fare -> reusable.putIfAbsent(key(fare.getFromRouteStop().getId(), fare.getToRouteStop().getId()), fare));
        List<OperatorRouteFare> active = new ArrayList<>();
        for (ValidatedFare item : validated) {
            OperatorRouteFare fare = reusable.getOrDefault(key(item.from().getId(), item.to().getId()), new OperatorRouteFare());
            fare.setOperatorRoute(association);
            fare.setFromRouteStop(item.from());
            fare.setToRouteStop(item.to());
            fare.setPrice(item.input().price());
            fare.setStatus(ActiveStatus.ACTIVE);
            active.add(fare);
        }
        fares.saveAll(existing);
        fares.saveAll(active);
        fares.flush();
        return active.stream().sorted(Comparator
                        .comparing((OperatorRouteFare f) -> f.getFromRouteStop().getStopOrder())
                        .thenComparing(f -> f.getToRouteStop().getStopOrder()))
                .map(this::response).toList();
    }

    private List<ValidatedFare> validate(List<FareInput> inputs, Long routeId) {
        Set<String> pairs = new HashSet<>();
        List<ValidatedFare> result = new ArrayList<>();
        for (FareInput input : inputs) {
            RouteStop from = stops.findByIdAndRouteId(input.fromRouteStopId(), routeId)
                    .orElseThrow(() -> invalid("Fare stops must belong to the operator route's route."));
            RouteStop to = stops.findByIdAndRouteId(input.toRouteStopId(), routeId)
                    .orElseThrow(() -> invalid("Fare stops must belong to the operator route's route."));
            if (from.getStatus() != ActiveStatus.ACTIVE || to.getStatus() != ActiveStatus.ACTIVE
                    || from.getStopOrder() >= to.getStopOrder()) {
                throw invalid("Fare must follow the route's stop order.");
            }
            if (!pairs.add(key(from.getId(), to.getId()))) throw invalid("Duplicate fare pair.");
            result.add(new ValidatedFare(input, from, to));
        }
        return result;
    }

    private FareResponse response(OperatorRouteFare fare) {
        return new FareResponse(fare.getId(), fare.getFromRouteStop().getId(), fare.getToRouteStop().getId(),
                fare.getPrice(), fare.getStatus());
    }

    private static String key(Long from, Long to) { return from + ":" + to; }
    private static BusinessException invalid(String message) {
        return new BusinessException("INVALID_ROUTE_FARE", message, HttpStatus.BAD_REQUEST, null);
    }
    private record ValidatedFare(FareInput input, RouteStop from, RouteStop to) {}
}
