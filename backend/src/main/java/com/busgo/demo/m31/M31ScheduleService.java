package com.busgo.demo.m31;

import java.time.LocalDate;
import java.util.*;
import com.busgo.demo.m31.M31Schedule.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** Bounded coordinator. Each rotation commits/rolls back independently through the batch proxy. */
@Service
@Profile("dev & !prod & !production")
public class M31ScheduleService {
    private final M31ScheduleBatchService batches;
    private final org.springframework.transaction.support.TransactionTemplate transactions;
    public M31ScheduleService(M31ScheduleBatchService batches,org.springframework.transaction.PlatformTransactionManager manager) {
        this.batches=batches; this.transactions=new org.springframework.transaction.support.TransactionTemplate(manager);
        this.transactions.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactions.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    public Report dryRun(LocalDate anchor,int horizon,boolean placement) { return execute(anchor,horizon,placement,false); }
    public Report apply(LocalDate anchor,int horizon,boolean placement) { return execute(anchor,horizon,placement,true); }
    private Report execute(LocalDate anchor,int horizon,boolean placement,boolean apply) {
        M31Fleet.plan(Objects.requireNonNull(anchor,"Explicit anchor required"),horizon);
        var result=new ArrayList<Batch>();
        for(var rotation:M31Fleet.rotations()) {
            if(!apply) { result.add(batches.dryRun(anchor,horizon,rotation.plate(),placement)); continue; }
            try { result.add(transactions.execute(status->batches.apply(anchor,horizon,rotation.plate(),placement))); }
            catch(RuntimeException e) {
                String failure=e instanceof com.busgo.common.exception.BusinessException b?b.getCode():e.getClass().getSimpleName();
                var legs=M31Schedule.legs(anchor,horizon,rotation.plate());
                var type=M31Fleet.vehicles().stream().filter(v->v.plate().equals(rotation.plate())).findFirst().orElseThrow().type();
                result.add(new Batch(rotation.plate(),legs.get(0).from(),placement,List.of(),legs.stream()
                    .map(l->new Departure(M31Schedule.key(l),"SKIP",null,"ROLLED_BACK: "+failure,l,type,0,0,null)).toList(),true));
            }
        }
        var departures=result.stream().flatMap(b->b.departures().stream()).toList();
        return new Report(M31Schedule.VERSION,anchor,horizon,!apply,List.copyOf(result),departures.stream().filter(d->d.action().equals("CREATED")).count(),
            departures.stream().filter(d->d.action().equals("REUSE")).count(),departures.stream().filter(d->d.action().equals("CREATE")).count(),
            departures.stream().filter(d->d.action().equals("SKIP")).count());
    }
}
