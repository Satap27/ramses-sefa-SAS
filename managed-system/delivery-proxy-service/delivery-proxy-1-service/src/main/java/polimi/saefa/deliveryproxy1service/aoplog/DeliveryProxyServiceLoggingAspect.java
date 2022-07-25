package polimi.saefa.deliveryproxy1service.aoplog;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
@Aspect
@Slf4j
public class DeliveryProxyServiceLoggingAspect {

    /* Pointcut per il servizio dei ristoranti */
    @Pointcut("execution(public * polimi.saefa.deliveryproxy1service.domain.DeliveryProxyService.*(..))")
    public void deliveryProxy1ServiceMethods() {}

    @Pointcut("execution(public void polimi.saefa.deliveryproxy1service.domain.DeliveryProxyService.*(..))")
    public void deliveryProxy1ServiceVoidMethods() {}

	/* metodi di log */ 
    private void logInvocation(JoinPoint joinPoint) {
        final String args = Arrays.toString(joinPoint.getArgs());
        final String methodName = joinPoint.getSignature().getName().replace("(..)", "()");
        log.info("CALL DeliveryProxyService from proxy 1.{} {}", methodName, args);
    }

    private void logTermination(JoinPoint joinPoint, Object retValue) {
        final String args = Arrays.toString(joinPoint.getArgs());
        final String methodName = joinPoint.getSignature().getName().replace("(..)", "()");
        log.info("     DeliveryProxyService from proxy 1.{} {} -> {}", methodName, args, retValue.toString());
    }

    private void logVoidTermination(JoinPoint joinPoint) {
        final String args = Arrays.toString(joinPoint.getArgs());
        final String methodName = joinPoint.getSignature().getName().replace("(..)", "()");
        log.info("     DeliveryProxyService from proxy 1.{} {} -> RETURN", methodName, args);
    }

    private void logException(JoinPoint joinPoint, Object exception) {
        final String args = Arrays.toString(joinPoint.getArgs());
        final String methodName = joinPoint.getSignature().getName().replace("(..)", "()");
        log.info("     ERROR IN DeliveryProxyService from proxy 1.{} {} -> {}", methodName, args, exception.toString());
    }

    /* Eseguito prima dell'esecuzione del metodo */
    @Before("deliveryProxy1ServiceMethods()")
    public void logBeforeExecuteMethod(JoinPoint joinPoint) {
        logInvocation(joinPoint);
    }

    /* Eseguito quando il metodo è terminato (con successo) */
    @AfterReturning(value="deliveryProxy1ServiceMethods() &&! deliveryProxy1ServiceVoidMethods()", returning="retValue")
    public void logSuccessMethod(JoinPoint joinPoint, Object retValue) {
        logTermination(joinPoint, retValue);
    }

    /* Eseguito quando il metodo (void) è terminato (con successo) */
    @AfterReturning("deliveryProxy1ServiceVoidMethods()")
    public void logSuccessVoidMethod(JoinPoint joinPoint) {
        logVoidTermination(joinPoint);
    }

    /* Eseguito se è stata sollevata un'eccezione */
    @AfterThrowing(value="deliveryProxy1ServiceMethods()", throwing="exception")
    public void logErrorApplication(JoinPoint joinPoint, Exception exception) {
        logException(joinPoint, exception);
    }

}

