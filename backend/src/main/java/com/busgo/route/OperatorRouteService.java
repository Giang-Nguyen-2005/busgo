package com.busgo.route;

import com.busgo.common.entity.ActiveStatus;
import com.busgo.common.exception.*;
import com.busgo.common.response.PagedResponse;
import com.busgo.common.security.CurrentUser;
import com.busgo.operator.OperatorContextService;
import com.busgo.route.RouteDtos.*;
import com.busgo.route.entity.OperatorRoute;
import com.busgo.route.repository.OperatorRouteRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperatorRouteService {
    private final OperatorRouteRepository operatorRoutes;
    private final OperatorContextService context;
    private final RouteDefinitionService definitions;

    public OperatorRouteService(OperatorRouteRepository operatorRoutes, OperatorContextService context,
            RouteDefinitionService definitions) {
        this.operatorRoutes = operatorRoutes;
        this.context = context;
        this.definitions = definitions;
    }

    @Transactional(readOnly = true)
    public PagedResponse<OperatorRouteResponse> list(CurrentUser user, int page, int size) {
        Long operatorId = context.requireAdminOperator(user).getId();
        var result = operatorRoutes.findByOperatorId(operatorId,
                PageRequest.of(page, size, Sort.by("id").ascending()));
        return PagedResponse.from(result.map(this::response));
    }

    @Transactional
    public OperatorRouteResponse attach(CurrentUser user, AttachRouteRequest request) {
        var operator = context.requireAdminOperator(user);
        var route = definitions.requireUsable(request.routeId());
        var existing = operatorRoutes.findByOperatorIdAndRouteId(operator.getId(), route.getId());
        if (existing.isPresent()) {
            if (existing.get().getStatus() == ActiveStatus.ACTIVE) throw duplicate();
            existing.get().setStatus(ActiveStatus.ACTIVE);
            return response(operatorRoutes.saveAndFlush(existing.get()));
        }
        OperatorRoute association = new OperatorRoute();
        association.setOperator(operator);
        association.setRoute(route);
        association.setStatus(ActiveStatus.ACTIVE);
        try {
            return response(operatorRoutes.saveAndFlush(association));
        } catch (DataIntegrityViolationException ex) {
            throw duplicate();
        }
    }

    @Transactional(readOnly = true)
    public OperatorRouteResponse get(CurrentUser user, Long id) {
        Long operatorId = context.requireAdminOperator(user).getId();
        return response(owned(id, operatorId));
    }

    @Transactional
    public OperatorRouteResponse update(CurrentUser user, Long id, UpdateOperatorRouteRequest request) {
        Long operatorId = context.requireAdminOperator(user).getId();
        OperatorRoute association = owned(id, operatorId);
        association.setStatus(request.status());
        return response(operatorRoutes.saveAndFlush(association));
    }

    public OperatorRoute owned(Long id, Long operatorId) {
        return operatorRoutes.findOwnedById(id, operatorId)
                .orElseThrow(() -> new ResourceNotFoundException("OPERATOR_ROUTE_NOT_FOUND", "Operator route was not found."));
    }

    private OperatorRouteResponse response(OperatorRoute association) {
        return new OperatorRouteResponse(association.getId(), association.getStatus(),
                definitions.response(association.getRoute()));
    }

    private static BusinessException duplicate() {
        return new BusinessException("OPERATOR_ROUTE_ALREADY_EXISTS", "Operator is already associated with this route.",
                HttpStatus.CONFLICT, null);
    }
}
