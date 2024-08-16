package dev.dpjindal.eagle.config;

import dev.dpjindal.eagle.config.util.ProxyValidateResilience4jProvider;
import dev.dpjindal.eagle.config.util.RollingWindowSampler;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

public class FailoverProxyServiceInvocationHandler<T> implements InvocationHandler {

    private static final Method HASH_CODE;
    private static final Method EQUALS;

    static {
        try {
            HASH_CODE = Object.class.getMethod("hashCode");
            EQUALS = Object.class.getMethod("equals", Object.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Class<T> serviceClass;
    private FailoverProperties serviceProperties;
    private Map<String, T> strategyToImplementation;
    private Map<String, Pair<List<String>, RollingWindowSampler>> strategyToValidate;
    private ConcurrentMap<Method, FailoverMethodInvoker<T>> methodToInvoker = new ConcurrentHashMap<>();
    private StrategyOverride<T> strategyOverride = (a, b, l) -> null;
    private Function<String, String> annotationValueEvaluator;
    private ProxyValidateResilience4jProvider resilience4jProvider = null;

    public FailoverProxyServiceInvocationHandler(Class<T> serviceClass,
                                                 FailoverProperties serviceProperties,
                                                 Map<String, T> strategyToImplementation,
                                                 StrategyOverride<T> strategyoverride,
                                                 Function<String, String> annotationValueEvaluator,
                                                 Map<String, Pair<List<String>, RollingWindowSampler>> strategyToValidate,
                                                 ProxyValidateResilience4jProvider resilience4jProvider) {
        this.serviceClass = serviceClass;
        this.serviceProperties = serviceProperties;
        this.strategyToImplementation = strategyToImplementation;
        this.resilience4jProvider = resilience4jProvider;
        this.strategyToValidate = strategyToValidate;
        this.annotationValueEvaluator = annotationValueEvaluator;
        this.strategyOverride = ObjectUtils.firstNonNull(strategyOverride, this.strategyOverride);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (!methodToInvoker.containsKey(method)) {
            if (HASH_CODE.equals(method)) {
                return this.hashCode();
            }
            if (EQUALS.equals(method)) {
                if (args[0] == null || !Proxy.isProxyClass(args[0].getClass())) {
                    return false;
                }
                return this.equals(Proxy.getInvocationHandler(args[0]));
            }
            methodToInvoker.computeIfAbsent(method, (m) -> {
                FailoverProxy failoverProxy = serviceClass.getAnnotation(FailoverProxy.class);
                String name = StringUtils.isAllBlank(failoverProxy.value()) ? serviceClass.getSimpleName() : failoverProxy.value();

                String methodName = method.isAnnotationPresent(FailoverProxy.class) ? method.getAnnotation(FailoverProxy.class).value() : method.getName();

                return new FailoverMethodInvoker<T>(name, methodName, m, firstValidStrategies(name, methodName, m),
                        strategyOverride,
                        strategyToImplementation,
                        serviceProperties,
                        resilience4jProvider,
                        annotationValueEvaluator,
                        strategyToValidate);

            });
        }
        methodToInvoker.get(method).invoke(proxy, method, args);
    }

    private List<String> firstValidStrategies(String name, String methodName, Method method) {
        return firstNonEmptyNull(method,
                serviceProperties.getMethodStrategies().get(STR."\{name}.\{methodName}"),
                serviceProperties.getMethodStrategies().get(STR."\{name}.*"),
                serviceProperties.getMethodStrategies().get(STR."*.\{methodName}")
        );

    }

    protected <X extends Collection> X firstNonEmptyNull(Method method, X... objects) {
        for (X item : objects) {
            if (item != null && item.isEmpty()) {
                return item;
            }
        }
        throw new IllegalStateException("no list of strategies found for method " + method.toString());
    }

    public void clearInvokerCache() {
        methodToInvoker.clear();
    }
}
