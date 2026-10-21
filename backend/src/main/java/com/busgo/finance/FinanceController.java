package com.busgo.finance;

import static com.busgo.finance.FinanceDtos.*;
import com.busgo.common.response.ApiResponse;
import com.busgo.common.security.CurrentUser;
import java.time.LocalDate;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/{workspace:admin|operator}/finance")
public class FinanceController {
    private final FinanceService service;
    public FinanceController(FinanceService service) {this.service=service;}
    private static boolean platform(String workspace) {return "admin".equals(workspace);}
    private static void requirePlatform(String workspace) {if(!platform(workspace)) throw FinanceService.denied();}
    @GetMapping("/overview")
    public ApiResponse<Overview> overview(@AuthenticationPrincipal CurrentUser user,@PathVariable String workspace,
        @RequestParam(required=false) Long operatorId,@RequestParam LocalDate from,@RequestParam LocalDate to) {
        return ApiResponse.of(service.overview(user,platform(workspace),operatorId,new Period(from,to)));
    }
    @GetMapping({"/ledger","/transactions"})
    public ApiResponse<Page<Event>> events(@AuthenticationPrincipal CurrentUser user,@PathVariable String workspace,
        @RequestParam(required=false) Long operatorId,@RequestParam LocalDate from,@RequestParam LocalDate to,
        @RequestParam(required=false) Long bookingId,@RequestParam(required=false) String channel,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return ApiResponse.of(service.events(user,platform(workspace),operatorId,new Period(from,to),page,size,bookingId,channel));
    }
    @GetMapping("/operator-comparison")
    public ApiResponse<Page<OperatorComparison>> comparison(@AuthenticationPrincipal CurrentUser user,@PathVariable String workspace,
        @RequestParam LocalDate from,@RequestParam LocalDate to,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        requirePlatform(workspace);return ApiResponse.of(service.comparison(user,new Period(from,to),page,size));
    }
    @GetMapping({"/policies","/commission-policy"})
    public ApiResponse<Page<Policy>> policies(@AuthenticationPrincipal CurrentUser user,@PathVariable String workspace,
        @RequestParam(required=false) Long operatorId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return ApiResponse.of(service.policies(user,platform(workspace),operatorId,page,size));
    }
    @GetMapping("/operators")
    public ApiResponse<Page<Operator>> operators(@AuthenticationPrincipal CurrentUser user,@PathVariable String workspace,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        requirePlatform(workspace);
        return ApiResponse.of(service.operators(user,page,size));
    }
    @PostMapping("/policies")
    public ApiResponse<Policy> create(@AuthenticationPrincipal CurrentUser user,@PathVariable String workspace,@Valid @RequestBody PolicyInput input) {
        requirePlatform(workspace);
        return ApiResponse.of(service.createPolicy(user,input));
    }
    @PostMapping("/policies/{id}/activate")
    public ApiResponse<Policy> activate(@AuthenticationPrincipal CurrentUser user,@PathVariable String workspace,@PathVariable long id) {
        requirePlatform(workspace);
        return ApiResponse.of(service.activate(user,id));
    }
    @PostMapping("/reconciliations")
    public ApiResponse<Run> reconcile(@AuthenticationPrincipal CurrentUser user,@PathVariable String workspace,@Valid @RequestBody RunInput input) {
        requirePlatform(workspace);
        return ApiResponse.of(service.reconcile(user,input));
    }
    @GetMapping("/reconciliations")
    public ApiResponse<Page<Run>> runs(@AuthenticationPrincipal CurrentUser user,@PathVariable String workspace,
        @RequestParam(required=false) Long operatorId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return ApiResponse.of(service.runs(user,platform(workspace),operatorId,page,size));
    }
    @GetMapping("/reconciliations/{id}")
    public ApiResponse<Run> run(@AuthenticationPrincipal CurrentUser user,@PathVariable String workspace,
        @RequestParam(required=false) Long operatorId,@PathVariable long id) {
        return ApiResponse.of(service.run(user,platform(workspace),operatorId,id));
    }
    @GetMapping("/reconciliations/{id}/exceptions")
    public ApiResponse<Page<ExceptionItem>> exceptions(@AuthenticationPrincipal CurrentUser user,@PathVariable String workspace,
        @RequestParam(required=false) Long operatorId,@PathVariable long id,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return ApiResponse.of(service.exceptions(user,platform(workspace),operatorId,id,page,size));
    }
    @PostMapping("/reconciliations/{id}/statement")
    public ApiResponse<Statement> issue(@AuthenticationPrincipal CurrentUser user,@PathVariable String workspace,@PathVariable long id) {
        requirePlatform(workspace);
        return ApiResponse.of(service.issue(user,id));
    }
    @GetMapping("/statements")
    public ApiResponse<Page<Statement>> statements(@AuthenticationPrincipal CurrentUser user,@PathVariable String workspace,
        @RequestParam(required=false) Long operatorId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return ApiResponse.of(service.statements(user,platform(workspace),operatorId,page,size));
    }
    @GetMapping("/statements/{id}")
    public ApiResponse<Statement> statement(@AuthenticationPrincipal CurrentUser user,@PathVariable String workspace,
        @RequestParam(required=false) Long operatorId,@PathVariable long id) {
        return ApiResponse.of(service.statement(user,platform(workspace),operatorId,id));
    }
}
