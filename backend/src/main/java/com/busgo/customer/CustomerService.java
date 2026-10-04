package com.busgo.customer;

import com.busgo.common.exception.BusinessException;
import com.busgo.common.response.PagedResponse;
import com.busgo.common.security.CurrentUser;
import com.busgo.customer.CustomerDtos.*;
import com.busgo.operator.OperatorContextService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class CustomerService {
    private final OperatorContextService context;
    private final CustomerRepository queries;
    public CustomerService(OperatorContextService context,CustomerRepository queries) { this.context=context;this.queries=queries; }
    public PagedResponse<Summary> directory(CurrentUser user,CustomerFilter f) {
        long operator=context.requireAdminOperator(user).getId();f.validate();return queries.directory(operator,f);
    }
    public Detail detail(CurrentUser user,String key,Integer page,Integer size) {
        long operator=context.requireAdminOperator(user).getId();CustomerFilter.validateKey(key);
        var f=new CustomerFilter(null,null,null,page,size);f.validate();
        var summary=queries.summary(operator,key,f).orElseThrow(()->new BusinessException("CUSTOMER_NOT_FOUND",
            "Customer/contact not found.",HttpStatus.NOT_FOUND,null));
        return new Detail(summary,queries.history(operator,key,f,summary.totalBookings()));
    }
}
