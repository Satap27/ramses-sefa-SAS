package it.polimi.saefa.paymentproxy1service.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Random;

@Component
@Aspect
@Slf4j
public class InstrumentationAspect {
    private final Double sleepMean;
    private final Double sleepVariance;
    private final Double exceptionProbability;

    public InstrumentationAspect(Environment env) {
        String sleepMean, sleepVariance, exceptionProbability;
        sleepMean = env.getProperty("SLEEP_MEAN");
        sleepVariance = env.getProperty("SLEEP_VARIANCE");
        exceptionProbability = env.getProperty("EXCEPTION_PROBABILITY");
        this.sleepMean = sleepMean == null ? null : Double.parseDouble(sleepMean);
        this.sleepVariance = sleepVariance == null ? null : Double.parseDouble(sleepVariance);
        this.exceptionProbability = exceptionProbability == null ? null : Double.parseDouble(exceptionProbability);
    }


    /* Pointcut per il servizio dei ristoranti */
    @Pointcut("execution(public * it.polimi.saefa.paymentproxy1service.domain.PaymentProxyService.*(..))")
    public void paymentProxyServiceMethods() {}

    @Pointcut("execution(public void it.polimi.saefa.paymentproxy1service.domain.PaymentProxyService.*(..))")
    public void paymentProxyServiceVoidMethods() {}

	/* metodi di log */ 
    private void logInvocation(JoinPoint joinPoint) {
        final String args = Arrays.toString(joinPoint.getArgs());
        final String methodName = joinPoint.getSignature().getName().replace("(..)", "()");
        log.info("CALL PaymentProxyService.{} {}", methodName, args);
    }

    private void logTermination(JoinPoint joinPoint, Object retValue) {
        final String args = Arrays.toString(joinPoint.getArgs());
        final String methodName = joinPoint.getSignature().getName().replace("(..)", "()");
        log.info("     PaymentProxyService.{} {} -> {}", methodName, args, retValue.toString());
    }

    private void logVoidTermination(JoinPoint joinPoint) {
        final String args = Arrays.toString(joinPoint.getArgs());
        final String methodName = joinPoint.getSignature().getName().replace("(..)", "()");
        log.info("     PaymentProxyService.{} {} -> RETURN", methodName, args);
    }

    private void logException(JoinPoint joinPoint, Object exception) {
        final String args = Arrays.toString(joinPoint.getArgs());
        final String methodName = joinPoint.getSignature().getName().replace("(..)", "()");
        log.info("     ERROR IN PaymentProxyService.{} {} -> {}", methodName, args, exception.toString());
    }

    /* Eseguito prima dell'esecuzione del metodo */
    @Before("paymentProxyServiceMethods()")
    public void logBeforeExecuteMethod(JoinPoint joinPoint) {
        try {
            long sleepTime = generateSleep();
            log.debug("Sleep duration: "+sleepTime);
            Thread.sleep(sleepTime);
        } catch (InterruptedException e) {}
        logInvocation(joinPoint);
    }

    /* Eseguito quando il metodo è terminato (con successo) */
    @AfterReturning(value="paymentProxyServiceMethods() &&! paymentProxyServiceVoidMethods()", returning="retValue")
    public void logSuccessMethod(JoinPoint joinPoint, Object retValue) {
        // Throw an exception with a certain probability
        shouldThrowException();
        logTermination(joinPoint, retValue);
    }

    /* Eseguito quando il metodo (void) è terminato (con successo) */
    @AfterReturning("paymentProxyServiceVoidMethods()")
    public void logSuccessVoidMethod(JoinPoint joinPoint) {
        // Throw an exception with a certain probability
        shouldThrowException();
        logVoidTermination(joinPoint);
    }

    /* Eseguito se è stata sollevata un'eccezione */
    @AfterThrowing(value="paymentProxyServiceMethods()", throwing="exception")
    public void logErrorApplication(JoinPoint joinPoint, Exception exception) {
        logException(joinPoint, exception);
    }

    private long generateSleep() {
        if (sleepMean == null || sleepVariance == null)
            return 0;
        return (long)((new Random()).nextGaussian()*sleepVariance + sleepMean);
    }

    private void shouldThrowException() throws RuntimeException {
        if (exceptionProbability != null && (new Random()).nextDouble() < exceptionProbability)
            throw new RuntimeException("An artificial exception has been thrown!");
    }

}

