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
import io.dropwizard.metrics5.MetricName;
import io.dropwizard.metrics5.MetricRegistry;
import io.dropwizard.metrics5.annotation.Metered;
import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Intercepted;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundConstruct;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.AroundTimeout;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

import java.lang.reflect.Executable;

@Metered
@Interceptor
@Priority(Interceptor.Priority.LIBRARY_BEFORE + 10)
/* packaged-private */ class MeteredInterceptor {

    private final Bean<?> bean;

    private final MetricRegistry registry;

    private final MetricResolver resolver;

    @Inject
    private MeteredInterceptor(@Intercepted Bean<?> bean, MetricRegistry registry, MetricResolver resolver) {
        this.bean = bean;
        this.registry = registry;
        this.resolver = resolver;
    }

    @AroundConstruct
    private Object meteredConstructor(InvocationContext context) throws Exception {
        return meteredCallable(context, context.getConstructor());
    }

    @AroundInvoke
    private Object meteredMethod(InvocationContext context) throws Exception {
        return meteredCallable(context, context.getMethod());
    }

    @AroundTimeout
    private Object meteredTimeout(InvocationContext context) throws Exception {
        return meteredCallable(context, context.getMethod());
    }

    private Object meteredCallable(InvocationContext context, Executable executable) throws Exception {
        MetricName name = resolver.metered(bean.getBeanClass(), executable).metricName();
        Meter meter = (Meter) registry.getMetrics().get(name);
        if (meter == null)
            throw new IllegalStateException("No meter with name [" + name + "] found in registry [" + registry + "]");

        meter.mark();
        return context.proceed();
    }
}
