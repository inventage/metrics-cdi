/**
 * Copyright © 2013 Antonin Stefanutti (antonin.stefanutti@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.astefanutti.metrics.cdi;

import io.dropwizard.metrics5.Meter;
import io.dropwizard.metrics5.MetricRegistry;
import io.dropwizard.metrics5.annotation.ExceptionMetered;
import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Intercepted;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundConstruct;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

import java.lang.reflect.Executable;

@Interceptor
@ExceptionMetered
@Priority(Interceptor.Priority.LIBRARY_BEFORE + 10)
/* package-private */ class ExceptionMeteredInterceptor {

    private final Bean<?> bean;

    private final MetricRegistry registry;

    private final MetricResolver resolver;

    @Inject
    private ExceptionMeteredInterceptor(@Intercepted Bean<?> bean, MetricRegistry registry, MetricResolver resolver) {
        this.bean = bean;
        this.registry = registry;
        this.resolver = resolver;
    }

    @AroundConstruct
    private Object meteredConstructor(InvocationContext context) throws Throwable {
        return meteredCallable(context, context.getConstructor());
    }

    @AroundInvoke
    private Object meteredMethod(InvocationContext context) throws Throwable {
        return meteredCallable(context, context.getMethod());
    }

    private Object meteredCallable(InvocationContext context, Executable executable) throws Throwable {
        MetricResolver.Of<ExceptionMetered> exceptionMetered = resolver.exceptionMetered(bean.getBeanClass(), executable);
        Meter meter = (Meter) registry.getMetrics().get(exceptionMetered.metricName());
        if (meter == null)
            throw new IllegalStateException("No meter with name [" + exceptionMetered.metricName() + "] found in registry [" + registry + "]");

        try {
            return context.proceed();
        } catch (Throwable throwable) {
            if (exceptionMetered.metricAnnotation().cause().isInstance(throwable))
                meter.mark();

            throw throwable;
        }
    }
}
