package dev.dpjindal.eagle.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface FailoverProxyValidate {
    String[] values() default "";

    String percentage() default "10";

    String window() default "20";

}
