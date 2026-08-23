package com.sportsbook.admin.audit;

import com.sportsbook.admin.context.AdminContext;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

@Aspect
@Component
public final class AuditAspect {

  private final AuditService audits;
  private final AuditOutcomeClassifier outcomes = new AuditOutcomeClassifier();
  private final ExpressionParser expressions = new SpelExpressionParser();
  private final DefaultParameterNameDiscoverer parameterNames =
      new DefaultParameterNameDiscoverer();

  public AuditAspect(AuditService audits) {
    this.audits = audits;
  }

  @Around("@annotation(com.sportsbook.admin.audit.Audited)")
  public Object record(ProceedingJoinPoint invocation) throws Throwable {
    Method method = ((MethodSignature) invocation.getSignature()).getMethod();
    Audited audited = AnnotatedElementUtils.findMergedAnnotation(method, Audited.class);
    if (audited == null) {
      throw new IllegalStateException("Audit metadata is required");
    }
    AdminContext context = context(invocation.getArgs());
    String target = evaluate(audited.target(), method, invocation.getTarget(), invocation.getArgs());
    String reason = evaluate(audited.reason(), method, invocation.getTarget(), invocation.getArgs());
    audits.begin(context, audited.action().name(), target, reason);

    Object result = null;
    Throwable originalFailure = null;
    try {
      result = invocation.proceed();
    } catch (Throwable failure) {
      originalFailure = failure;
    }

    AuditOutcomeClassifier.AuditDecision decision =
        originalFailure == null
            ? outcomes.result(result, method)
            : outcomes.failure(originalFailure);
    try {
      audits.complete(context.actionId(), decision.outcome(), decision.httpStatus());
    } catch (AuditPersistenceException finalizationFailure) {
      if (originalFailure != null) {
        finalizationFailure.addSuppressed(originalFailure);
      }
      throw finalizationFailure;
    }
    if (originalFailure != null) {
      throw originalFailure;
    }
    return result;
  }

  private AdminContext context(Object[] arguments) {
    return Arrays.stream(arguments)
        .filter(AdminContext.class::isInstance)
        .map(AdminContext.class::cast)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Audited method requires AdminContext"));
  }

  private String evaluate(
      String expression, Method method, Object target, Object[] arguments) {
    if (expression.isBlank()) {
      return null;
    }
    MethodBasedEvaluationContext evaluation =
        new MethodBasedEvaluationContext(target, method, arguments, parameterNames);
    Object value = expressions.parseExpression(expression).getValue(evaluation);
    return value == null ? null : value.toString();
  }
}
