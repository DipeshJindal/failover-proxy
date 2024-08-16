package dev.dpjindal.eagle.config;

import dev.dpjindal.eagle.config.util.ProxyValidateResilience4jProvider;
import dev.dpjindal.eagle.config.util.RollingWindowSampler;
import org.apache.commons.lang3.tuple.Pair;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class FailoverMethodInvoker<T> implements InvocationHandler {

    public FailoverMethodInvoker(String name, String methodName, Method m, Object o, StrategyOverride<T> strategyOverride, Map<String, T> strategyToImplementation, FailoverProperties serviceProperties, ProxyValidateResilience4jProvider resilience4jProvider, Function<String, String> annotationValueEvaluator, Map<String, Pair<List<String>, RollingWindowSampler>> strategyToValidate) {
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        return null;
    }
}
