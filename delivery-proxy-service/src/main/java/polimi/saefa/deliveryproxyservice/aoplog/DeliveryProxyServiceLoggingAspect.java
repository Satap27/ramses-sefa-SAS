package polimi.saefa.deliveryproxyservice.aoplog;

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
    @Pointcut("execution(public * polimi.saefa.deliveryproxyservice.domain.DeliveryProxyService.*(..))")
    public void deliveryProxyServiceMethods() {}

    @Pointcut("execution(public void polimi.saefa.deliveryproxyservice.domain.DeliveryProxyService.*(..))")
    public void deliveryProxyServiceVoidMethods() {}

	/* metodi di log */ 
    private void logInvocation(JoinPoint joinPoint) {
        final String args = Arrays.toString(joinPoint.getArgs());
//        final String methodName = joinPoint.getSignature().toShortString().replace("(..)", "()");
        final String methodName = joinPoint.getSignature().getName().replace("(..)", "()");
        log.info("CALL DeliveryProxyService.{} {}", methodName, args);
    }

    private void logTermination(JoinPoint joinPoint, Object retValue) {
        final String args = Arrays.toString(joinPoint.getArgs());
//        final String methodName = joinPoint.getSignature().toShortString().replace("(..)", "()");
        final String methodName = joinPoint.getSignature().getName().replace("(..)", "()");
        log.info("     DeliveryProxyService.{} {} -> {}", methodName, args, retValue.toString());
    }

    private void logVoidTermination(JoinPoint joinPoint) {
        final String args = Arrays.toString(joinPoint.getArgs());
//        final String methodName = joinPoint.getSignature().toShortString().replace("(..)", "()");
        final String methodName = joinPoint.getSignature().getName().replace("(..)", "()");
        log.info("     DeliveryProxyService.{} {} -> RETURN", methodName, args);
    }

    private void logException(JoinPoint joinPoint, Object exception) {
        final String args = Arrays.toString(joinPoint.getArgs());
//        final String methodName = joinPoint.getSignature().toShortString().replace("(..)", "()");
        final String methodName = joinPoint.getSignature().getName().replace("(..)", "()");
        log.info("     ERROR IN DeliveryProxyService.{} {} -> {}", methodName, args, exception.toString());
    }

    /* Eseguito prima dell'esecuzione del metodo */
    @Before("deliveryProxyServiceMethods()")
    public void logBeforeExecuteMethod(JoinPoint joinPoint) {
        logInvocation(joinPoint);
    }

    /* Eseguito quando il metodo è terminato (con successo) */
    @AfterReturning(value="deliveryProxyServiceMethods() &&! deliveryProxyServiceVoidMethods()", returning="retValue")
    public void logSuccessMethod(JoinPoint joinPoint, Object retValue) {
        logTermination(joinPoint, retValue);
    }

    /* Eseguito quando il metodo (void) è terminato (con successo) */
    @AfterReturning("deliveryProxyServiceVoidMethods()")
    public void logSuccessVoidMethod(JoinPoint joinPoint) {
        logVoidTermination(joinPoint);
    }

    /* Eseguito se è stata sollevata un'eccezione */
    @AfterThrowing(value="deliveryProxyServiceMethods()", throwing="exception")
    public void logErrorApplication(JoinPoint joinPoint, Exception exception) {
        logException(joinPoint, exception);
    }

}

