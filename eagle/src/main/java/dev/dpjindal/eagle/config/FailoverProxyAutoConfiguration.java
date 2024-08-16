package dev.dpjindal.eagle.config;


import dev.dpjindal.eagle.config.util.ProxyValidateResilience4jProvider;
import dev.dpjindal.eagle.config.util.RollingWindowSampler;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.*;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.ResolvableType;
import org.springframework.core.env.Environment;
import org.springframework.validation.SimpleErrors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(value = "failover.proxy.auto.enabled", matchIfMissing = false)
@EnableConfigurationProperties(FailoverProperties.class)
@Slf4j
public class FailoverProxyAutoConfiguration {
    public static Logger LOG = LoggerFactory.getLogger(FailoverProxyAutoConfiguration.class);

    private static void validateStrategyProperties(FailoverProperties properties) {
    }

    @Bean
    public static BeanDefinitionRegistryPostProcessor failoverPostProcessor(@Autowired Environment environment) {
        return new BeanDefinitionRegistryPostProcessor() {
            @Override
            public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
                BindResult<FailoverProperties> bindResult = Binder.get(environment)
                        .bind("failover.proxy", FailoverProperties.class);
                if (!bindResult.isBound()) {
                    return;
                }
                FailoverProperties properties = bindResult.get();
                ListableBeanFactory listableBeanFactory = (ListableBeanFactory) registry;
                String[] proxyBeanNames = listableBeanFactory.getBeanNamesForAnnotation(FailoverProxyService.class);
                Map<Class, List<BeanNameWithAnnotation>> proxyInterfaceToBean = new HashMap<>();
                Map<String, Class> existingNameToServiceClass = new HashMap<>();
                Function<String, String> valueEvalFunction = e -> evaluationAnnotationExpressionValve(registry, e);
                for (String beanName : proxyBeanNames) {
                    BeanDefinition definition = registry.getBeanDefinition(beanName);
                    FailoverProxyService proxyAnnotation = listableBeanFactory.findAnnotationOnBean(beanName, FailoverProxyService.class);
                    FailoverProxyValidate proxyValidate = listableBeanFactory.findAnnotationOnBean(beanName, FailoverProxyValidate.class);
                    validateStrategyProperties(properties, beanName, proxyAnnotation.value());
                    List<Class> interfaces = getInterfacesForBeanDef(beanName, definition, proxyAnnotation.interfaceHints());
                    if (interfaces != null && !interfaces.isEmpty()) {
                        for (Class serviceInterface : interfaces) {
                            checkForDuplicateServices(existingNameToServiceClass, serviceInterface);
                            if (!proxyInterfaceToBean.containsKey(serviceInterface)) {
                                proxyInterfaceToBean.put(serviceInterface, new ArrayList<>());
                            }
                            proxyInterfaceToBean.get(serviceInterface).add(BeanNameWithAnnotation.builder()
                                    .name(beanName)
                                    .annotation(proxyAnnotation)
                                    .validate(proxyValidate)
                                    .build());
                        }
                        definition.setPrimary(false);
                    } else {
                        System.out.println("sorryryryyryr");
                    }
                }
                List<FailoverProxyServiceInvocationHandler> refreshHandlerList = new CopyOnWriteArrayList<>();
                for (Map.Entry<Class, List<BeanNameWithAnnotation>> entry : proxyInterfaceToBean.entrySet()) {
                    Class serviceInterface = entry.getKey();
                    String beanName = "failoverProxy " + serviceInterface.getSimpleName();
                    List<BeanNameWithAnnotation> concreteBeanList = entry.getValue();
                    RootBeanDefinition proxyBeanDefinition = new RootBeanDefinition(serviceInterface, () -> {
                        Map<String, Object> strategyImplMap = new HashMap<>();
                        Map<String, Pair<List<String>, RollingWindowSampler>> strategyValidateMap = new HashMap<>();
                        concreteBeanList.forEach(info -> {
                            FailoverProxyValidate validate = info.getValidate();
                            if (validate != null) {
                                int window = Integer.parseInt(validate.window());
                                int percentage = Integer.parseInt(validate.percentage());
                                LOG.info("on Service proxy implementation {} with strategy {}, validate with following strategies if with request window {} and percentage {}",
                                        getBeanDefType(registry.getBeanDefinition(info.getName())), info.getAnnotation().value(), StringUtils.join(validate.values(), ","),
                                        window, percentage);
                                if (window > 0 && percentage > 0) {
                                    RollingWindowSampler sampler = new RollingWindowSampler(window, percentage);
                                    List<String> validateStrategies = Arrays.asList(validate.values());
                                    validateStrategies.remove(info.getAnnotation().value());
                                    strategyValidateMap.put(info.getAnnotation().value(), Pair.of(validateStrategies, sampler));
                                }
                            }
                            strategyImplMap.put(info.getAnnotation().value(), listableBeanFactory.getBean(info.getName()));
                        });
                        FailoverProperties props = listableBeanFactory.getBean(FailoverProperties.class);
                        ObjectProvider<StrategyOverride> overrideProvider =
                                listableBeanFactory.getBeanProvider(ResolvableType.forClassWithGenerics(StrategyOverride.class, serviceInterface), true);
                        ObjectProvider<ProxyValidateResilience4jProvider> resilience4jProvider =
                                listableBeanFactory.getBeanProvider(ProxyValidateResilience4jProvider.class);
                        FailoverProxyServiceInvocationHandler handler = new FailoverProxyServiceInvocationHandler(
                                serviceInterface,
                                props,
                                strategyImplMap,
                                overrideProvider.getIfAvailable(),
                                valueEvalFunction,
                                strategyValidateMap,
                                resilience4jProvider.getIfAvailable());
                        refreshHandlerList.add(handler);
                        return Proxy.newProxyInstance(FailoverProxyAutoConfiguration.class.getClassLoader(), new Class[]{serviceInterface}, handler);
                    });
                    proxyBeanDefinition.setPrimary(true);
                    LOG.info("registering primary failover proxy bean {} for service interface {}", beanName, serviceInterface.getName());
                    registry.registerBeanDefinition(beanName, proxyBeanDefinition);
                }

                if (!proxyInterfaceToBean.isEmpty()) {

                    ConstructorArgumentValues constructorArgs = new ConstructorArgumentValues();
                    constructorArgs.addGenericArgumentValue(new RuntimeBeanReference(FailoverProperties.class));
                    constructorArgs.addGenericArgumentValue(refreshHandlerList);
                    RootBeanDefinition refreshBeanDefinition = new RootBeanDefinition(FailoverProxyServiceContextRefresher.class, constructorArgs,
                            null);

                    registry.registerBeanDefinition("failoverProxyServiceContextRefresher", refreshBeanDefinition);
                }


            }

            private String getBeanDefType(BeanDefinition beanDefinition) {
                if (beanDefinition.getResolvableType() != null && beanDefinition.getResolvableType() != ResolvableType.NONE) {
                    return String.valueOf(beanDefinition.getResolvableType().getRawClass());
                } else {
                    return beanDefinition.getBeanClassName();
                }
            }

            private List<Class> getInterfacesForBeanDef(String beanName, BeanDefinition definition, Class[] interfaceHints) {
                Set<Class> hints = ArrayUtils.isEmpty(interfaceHints) ? null : Arrays.stream(interfaceHints).collect(Collectors.toSet());
                List<Class> interfaces = new ArrayList<>();
                ResolvableType resolvableType = definition.getResolvableType();

                if (resolvableType != null && resolvableType != ResolvableType.NONE) {
                    if (resolvableType.getRawClass().isInterface()) {
                        interfaces.add(resolvableType.getRawClass());
                    }
                    ResolvableType[] interfaceTypes = resolvableType.getInterfaces();
                    for (int i = 0; i < interfaceTypes.length; i++) {
                        interfaces.add(interfaceTypes[1].getRawClass());
                    }
                } else if (StringUtils.isNotEmpty(definition.getBeanClassName())) {
                    try {
                        interfaces.addAll(ClassUtils.getAllInterfaces(Class.forName(definition.getBeanClassName())));
                    } catch (ClassNotFoundException cnfe) {
//                        LOG.error("ClassNotFoundException while attempting to determine proxy interfaces for bean (J", beanDefName, cnfe);
                    }
                } else if (definition instanceof AnnotatedBeanDefinition abd) {
                    try {
                        interfaces.addAll(ClassUtils.getAllInterfaces(Class.forName(abd.getFactoryMethodMetadata().getReturnTypeName())));
                    } catch (ClassNotFoundException enfe) {
//                        LOG.error("ClassNotFoundException while attempting to determine proxy interfaces for bean f}", beanName, cnfe);
                    }

                }
                return interfaces.stream()
                        .filter(k -> !(StringUtils.startsWith(k.getPackage().getName(), "java.") || StringUtils.startsWith(k.getPackage().getName(), "javax.") ||
                                StringUtils.startsWith(k.getPackage().getName(), "org.springframework."))) // filter out springframework interfaces
                        .filter(k -> k.isAnnotationPresent(FailoverProxy.class) || (hints != null && hints.contains(k))) // filter on exact class interface annotations or hint clas
                        .collect(Collectors.toList());
            }

            private String evaluationAnnotationExpressionValve(BeanDefinitionRegistry registry, String
                    expression) {
                ConfigurableBeanFactory configurableBeanFactory = (ConfigurableBeanFactory) registry;
                BeanExpressionResolver beanExpressionResolver = configurableBeanFactory.getBeanExpressionResolver();
                String expressionWithSubstitutedVariables = configurableBeanFactory.resolveEmbeddedValue(expression);
                return String.valueOf(beanExpressionResolver.evaluate(expressionWithSubstitutedVariables, new BeanExpressionContext(configurableBeanFactory, null)));
            }

            private static void validateStrategyProperties(FailoverProperties target, String
                    beanName, String...
                                                                   possibleStrategies) throws IllegalStateException {

                FailoverProperties proxyProperties = target;
                SimpleErrors errors = new SimpleErrors(proxyProperties);
                Collection<String> leftOver = CollectionUtils.subtract(Arrays.asList(possibleStrategies), proxyProperties.getSupported());

                if (!CollectionUtils.isEmpty(leftOver)) {

                    String invalidStrategies = StringUtils.join(leftOver, ",");
                    errors.reject("field. service.strategy invalid", new Object[]{
                            beanName, invalidStrategies}, "configured strategies l" + invalidStrategies + "I for service" + beanName + " are invalid");
                }
                if (errors.hasErrors()) {

                    throw new IllegalStateException(errors.toString());

                }
            }
        };

    }

    private static void checkForDuplicateServices(Map<String, Class> existingNameToServiceClass, Class serviceInterface) throws IllegalStateException {
        String serviceName = serviceInterface.getSimpleName();
        if (serviceInterface.isAnnotationPresent(FailoverProxy.class)) {
            FailoverProxy annotation = (FailoverProxy) serviceInterface.getAnnotation(FailoverProxy.class);
            if (StringUtils.isNotBlank(annotation.value())) {
                serviceName = annotation.value();
                Class existing = existingNameToServiceClass.get(serviceName);
                if (existing != null && !serviceInterface.equals(existing)) {
                    throw new IllegalStateException("existing service class " + existing + " with name" + serviceName + "conflicts with new service class " + serviceInterface);
                } else {
                    checkForDuplicateMethods(serviceInterface);
                    existingNameToServiceClass.put(serviceName, serviceInterface);
                }
            }
        }

    }

    private static void checkForDuplicateMethods(Class serviceInterface) throws IllegalStateException {
        Map<String, Method> existingMethods = new HashMap<>();
        for (Method method : serviceInterface.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers())) {
                String name = method.getName();
                if (method.isAnnotationPresent(FailoverMethod.class)) {
                    FailoverMethod annotation = method.getAnnotation(FailoverMethod.class);
                    name = annotation.value();
                    if (existingMethods.containsKey(name)) {
                        throw new IllegalStateException("service class " + serviceInterface + " has duplicate method name " + name +
                                " between " + method.toGenericString() + " and " + existingMethods.get(name).toGenericString());
                    } else {
                        existingMethods.put(name, method);
                    }
                }
            }
        }
    }

    @Builder
    @Getter
    private static class BeanNameWithAnnotation {
        private String name;
        private FailoverProxyService annotation;
        private FailoverProxyValidate validate;
    }

    public static class FailoverProxyServiceContextRefresher {
        private FailoverProperties properties;
        private List<FailoverProxyServiceInvocationHandler> handlerList;


        public FailoverProxyServiceContextRefresher(FailoverProperties properties, List<FailoverProxyServiceInvocationHandler> handlerList) {
            this.properties = properties;
            this.handlerList = handlerList;
        }

        @EventListener({ContextRefreshedEvent.class, EnvironmentChangeEvent.class})
        public void refreshProxyMethodCache() {
            try {
                validateStrategyProperties(properties);
            } catch (IllegalStateException e) {

            }
            if (properties.isResetOnContextRefresh()) {
                LOG.info("Refresh if FailoverProxyServiceInvocationHandler method cache on refresh event", handlerList.size());
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Observed FailoverProxy Properties t}", ToStringBuilder.reflectionToString(properties));
                    this.handlerList.forEach(h -> h.clearInvokerCache());
                }
            }
        }

    }
}
