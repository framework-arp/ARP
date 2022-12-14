package arp.annotation;

import static java.lang.annotation.ElementType.METHOD;

import java.lang.annotation.Target;

@Target(METHOD)
public @interface Process {

	boolean publish() default false;

	boolean dontPublishWhenResultIsNull() default false;

	String listening() default "";
}
